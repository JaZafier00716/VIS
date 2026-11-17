package dataaccess.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import dataaccess.dto.FinishDto;
import dataaccess.mapper.FinishRowMapper;
import dataaccess.gateway.FinishGateway;
import dataaccess.row.FinishRow;
import dataaccess.row.MaterialFinishRow;

import java.util.List;
import java.util.Optional;

/** Table Data Gateway used by the Data Mapper (JSON file(s) finishes). */
public class JsonFinishGateway implements FinishGateway {
    static final String FILE = "finishes.json";
    static final TypeReference<List<FinishRow>> TYPE = new TypeReference<>() {};
    private static final String ENTITY = "Povrchová úprava";

    @Override
    public void insert(FinishDto finish) {
        String id = RecordIds.require(finish.finishId(), ENTITY);
        List<FinishRow> rows = JsonFiles.read(FILE, TYPE);
        if (rows.stream().anyMatch(row -> row.finishId().equals(id))) {
            throw RecordIds.duplicate(ENTITY, id);
        }
        rows.add(FinishRowMapper.toRow(finish));
        JsonFiles.write(FILE, rows);
    }

    @Override
    public void update(FinishDto finish) {
        String id = RecordIds.require(finish.finishId(), ENTITY);
        List<FinishRow> rows = JsonFiles.read(FILE, TYPE);
        rows.set(indexOf(rows, id), FinishRowMapper.toRow(finish));
        JsonFiles.write(FILE, rows);
    }

    /** A finish still used by a material cannot be deleted (FK without cascade in H2). */
    @Override
    public void delete(String id) {
        RecordIds.require(id, ENTITY);
        List<FinishRow> rows = JsonFiles.read(FILE, TYPE);
        int index = indexOf(rows, id);
        List<MaterialFinishRow> links = JsonFiles.read(JsonMaterialFinishGateway.FILE,
                JsonMaterialFinishGateway.TYPE);
        if (links.stream().anyMatch(link -> link.finishId().equals(id))) {
            throw new IllegalStateException("Povrchovou úpravu " + id + " nelze smazat, používají ji materiály.");
        }
        rows.remove(index);
        JsonFiles.write(FILE, rows);
    }

    @Override
    public Optional<FinishDto> findById(String id) {
        return JsonFiles.read(FILE, TYPE).stream().filter(row -> row.finishId().equals(id))
                .findFirst().map(FinishRowMapper::toDto);
    }

    @Override
    public List<FinishDto> findAll() {
        return JsonFiles.read(FILE, TYPE).stream().map(FinishRowMapper::toDto).toList();
    }

    static boolean exists(String id) {
        return JsonFiles.read(FILE, TYPE).stream().anyMatch(row -> row.finishId().equals(id));
    }

    private static int indexOf(List<FinishRow> rows, String id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).finishId().equals(id)) {
                return i;
            }
        }
        throw RecordIds.missing(ENTITY, id);
    }
}
