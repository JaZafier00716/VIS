package dataaccess.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import dataaccess.dto.MaterialDto;
import dataaccess.mapper.MaterialRowMapper;
import dataaccess.gateway.MaterialGateway;
import dataaccess.row.MaterialFinishRow;
import dataaccess.row.MaterialRow;
import dataaccess.row.OrderItemRow;
import dataaccess.row.OrderRow;

import java.util.List;
import java.util.Optional;

/** Table Data Gateway used by the Data Mapper (JSON file(s) materials). */
public class JsonMaterialGateway implements MaterialGateway {
    static final String FILE = "materials.json";
    static final TypeReference<List<MaterialRow>> TYPE = new TypeReference<>() {};
    private static final String ENTITY = "Materiál";

    @Override
    public void insert(MaterialDto material) {
        String id = RecordIds.require(material.materialId(), ENTITY);
        List<MaterialRow> rows = JsonFiles.read(FILE, TYPE);
        if (rows.stream().anyMatch(row -> row.materialId().equals(id))) {
            throw RecordIds.duplicate(ENTITY, id);
        }
        rows.add(MaterialRowMapper.toRow(material));
        JsonFiles.write(FILE, rows);
    }

    @Override
    public void update(MaterialDto material) {
        String id = RecordIds.require(material.materialId(), ENTITY);
        List<MaterialRow> rows = JsonFiles.read(FILE, TYPE);
        rows.set(indexOf(rows, id), MaterialRowMapper.toRow(material));
        JsonFiles.write(FILE, rows);
    }

    /** Orders and order items block the delete; association records are removed (like ON DELETE CASCADE). */
    @Override
    public void delete(String id) {
        RecordIds.require(id, ENTITY);
        List<MaterialRow> rows = JsonFiles.read(FILE, TYPE);
        int index = indexOf(rows, id);
        List<OrderRow> orders = JsonFiles.read(JsonOrderGateway.FILE, JsonOrderGateway.TYPE);
        List<OrderItemRow> items = JsonFiles.read(JsonOrderGateway.ITEMS_FILE, JsonOrderGateway.ITEMS_TYPE);
        if (orders.stream().anyMatch(o -> o.materialId().equals(id))
                || items.stream().anyMatch(i -> i.materialId().equals(id))) {
            throw new IllegalStateException("Materiál " + id + " nelze smazat, používají ho objednávky.");
        }
        rows.remove(index);
        JsonFiles.write(FILE, rows);
        List<MaterialFinishRow> links = JsonFiles.read(JsonMaterialFinishGateway.FILE,
                JsonMaterialFinishGateway.TYPE);
        if (links.removeIf(link -> link.materialId().equals(id))) {
            JsonFiles.write(JsonMaterialFinishGateway.FILE, links);
        }
    }

    @Override
    public Optional<MaterialDto> findById(String id) {
        return JsonFiles.read(FILE, TYPE).stream().filter(row -> row.materialId().equals(id))
                .findFirst().map(MaterialRowMapper::toDto);
    }

    @Override
    public List<MaterialDto> findAll() {
        return JsonFiles.read(FILE, TYPE).stream().map(MaterialRowMapper::toDto).toList();
    }

    static boolean exists(String id) {
        return JsonFiles.read(FILE, TYPE).stream().anyMatch(row -> row.materialId().equals(id));
    }

    private static int indexOf(List<MaterialRow> rows, String id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).materialId().equals(id)) {
                return i;
            }
        }
        throw RecordIds.missing(ENTITY, id);
    }
}
