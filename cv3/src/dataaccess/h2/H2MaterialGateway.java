package dataaccess.h2;

import dataaccess.dto.MaterialDto;
import dataaccess.persistence.RecordIds;
import dataaccess.gateway.MaterialGateway;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Table Data Gateway used by the Data Mapper (H2 table materials). */
public class H2MaterialGateway implements MaterialGateway {
    private static final String ENTITY = "Materiál";
    private final H2Database database;

    public H2MaterialGateway(H2Database database) {
        this.database = database;
    }

    @Override
    public void insert(MaterialDto material) {
        String id = RecordIds.require(material.materialId(), ENTITY);
        database.run("Vložení materiálu " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO materials (material_id, name) VALUES (?, ?)")) {
                statement.setString(1, id);
                statement.setString(2, material.name());
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public void update(MaterialDto material) {
        String id = RecordIds.require(material.materialId(), ENTITY);
        int updated = database.run("Úprava materiálu " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE materials SET name = ? WHERE material_id = ?")) {
                statement.setString(1, material.name());
                statement.setString(2, id);
                return statement.executeUpdate();
            }
        });
        if (updated == 0) {
            throw RecordIds.missing(ENTITY, id);
        }
    }

    /** Association records go with the material (ON DELETE CASCADE); orders using it block the delete. */
    @Override
    public void delete(String id) {
        RecordIds.require(id, ENTITY);
        int deleted = database.run("Smazání materiálu " + id,
                connection -> H2Records.deleteById(connection, "materials", "material_id", id));
        if (deleted == 0) {
            throw RecordIds.missing(ENTITY, id);
        }
    }

    @Override
    public Optional<MaterialDto> findById(String id) {
        return database.run("Načtení materiálu " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT material_id, name FROM materials WHERE material_id = ?")) {
                statement.setString(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<MaterialDto> findAll() {
        return database.run("Načtení materiálů", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT material_id, name FROM materials ORDER BY material_id");
                 ResultSet rs = statement.executeQuery()) {
                List<MaterialDto> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return result;
            }
        });
    }

    private static MaterialDto map(ResultSet rs) throws SQLException {
        return new MaterialDto(rs.getString("material_id"), rs.getString("name"));
    }
}
