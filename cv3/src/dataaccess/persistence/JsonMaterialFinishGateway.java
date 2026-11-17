package dataaccess.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import dataaccess.gateway.MaterialFinishGateway;
import dataaccess.row.MaterialFinishRow;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Table Data Gateway used by the Data Mapper (JSON file(s) material_finishes).
 * Association Table Mapping over material_finishes.json: each record is one (material, finish) pair.
 * Enforces the same rules as the H2 composite PK and FKs.
 */
public class JsonMaterialFinishGateway implements MaterialFinishGateway {
    static final String FILE = "material_finishes.json";
    static final TypeReference<List<MaterialFinishRow>> TYPE = new TypeReference<>() {};

    @Override
    public Set<String> findFinishIds(String materialId) {
        return JsonFiles.read(FILE, TYPE).stream().filter(row -> row.materialId().equals(materialId))
                .map(MaterialFinishRow::finishId).collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public Set<String> findMaterialIds(String finishId) {
        return JsonFiles.read(FILE, TYPE).stream().filter(row -> row.finishId().equals(finishId))
                .map(MaterialFinishRow::materialId).collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public void synchronize(String materialId, Set<String> finishIds) {
        RecordIds.require(materialId, "Materiál");
        if (!JsonMaterialGateway.exists(materialId)) {
            throw new IllegalArgumentException("Vazba odkazuje na neexistující materiál " + materialId + ".");
        }
        for (String finishId : finishIds) {
            if (!JsonFinishGateway.exists(finishId)) {
                throw new IllegalArgumentException("Vazba odkazuje na neexistující povrchovou úpravu " + finishId + ".");
            }
        }
        List<MaterialFinishRow> rows = JsonFiles.read(FILE, TYPE);
        Set<String> stored = findFinishIds(materialId);
        if (stored.equals(new TreeSet<>(finishIds))) {
            return;
        }
        rows.removeIf(row -> row.materialId().equals(materialId) && !finishIds.contains(row.finishId()));
        for (String finishId : new TreeSet<>(finishIds)) {
            if (!stored.contains(finishId)) {
                rows.add(new MaterialFinishRow(materialId, finishId));
            }
        }
        JsonFiles.write(FILE, rows);
    }
}
