package business.service;

import business.domainmodel.Finish;
import business.domainmodel.Material;
import com.fasterxml.jackson.core.type.TypeReference;
import dataaccess.persistence.JsonFiles;
import dataaccess.row.MaterialFinishRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.IsolatedJsonStorage;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CV06 - Association Table Mapping: Material <-> Finish (M:N) stored as pairs in material_finishes. */
class AssociationTableMappingTest {
    @TempDir
    Path dir;
    private IsolatedJsonStorage storage;

    @BeforeEach
    void setUp() {
        storage = new IsolatedJsonStorage(dir);
        MaterialService materials = storage.newContext().materialService();
        materials.createMaterial("M-OAK", "Dub");
        materials.createMaterial("M-BEECH", "Buk");
        materials.createFinish("F-LACQ", "Lak");
        materials.createFinish("F-OIL", "Olej");
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void linksAreStoredAsSeparateRecordsAndReadInBothDirections() {
        MaterialService materials = storage.newContext().materialService();
        materials.addFinish("M-OAK", "F-LACQ");
        materials.addFinish("M-OAK", "F-OIL");
        materials.addFinish("M-BEECH", "F-LACQ");

        assertEquals(3, links().size());
        MaterialService fresh = storage.newContext().materialService();
        assertEquals(List.of("F-LACQ", "F-OIL"),
                fresh.findFinishes("M-OAK").stream().map(Finish::getFinishId).sorted().toList());
        assertEquals(List.of("M-BEECH", "M-OAK"),
                fresh.findMaterialsWithFinish("F-LACQ").stream().map(Material::getMaterialId).sorted().toList());
    }

    @Test
    void duplicatePairIsNotStored() {
        MaterialService materials = storage.newContext().materialService();
        assertTrue(materials.addFinish("M-OAK", "F-LACQ"));
        assertFalse(materials.addFinish("M-OAK", "F-LACQ"));
        assertFalse(storage.newContext().materialService().addFinish("M-OAK", "F-LACQ"));

        assertEquals(1, links().size());
    }

    @Test
    void removingAFinishDeletesOnlyThatPair() {
        MaterialService materials = storage.newContext().materialService();
        materials.addFinish("M-OAK", "F-LACQ");
        materials.addFinish("M-OAK", "F-OIL");

        storage.newContext().materialService().removeFinish("M-OAK", "F-OIL");

        assertEquals(List.of(new MaterialFinishRow("M-OAK", "F-LACQ")), links());
    }

    @Test
    void linkToAMissingFinishIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.newContext().materialService().addFinish("M-OAK", "F-404"));
        assertTrue(links().isEmpty());
    }

    private static List<MaterialFinishRow> links() {
        return JsonFiles.read("material_finishes.json", new TypeReference<List<MaterialFinishRow>>() {});
    }
}
