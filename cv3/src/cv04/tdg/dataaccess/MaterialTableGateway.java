package cv04.tdg.dataaccess;

import dataaccess.h2.H2Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Table Data Gateway for cv04_materials. */
public class MaterialTableGateway {
    private final H2Database database;

    public MaterialTableGateway(H2Database database) {
        this.database = database;
    }

    public void insert(MaterialDto material) {
        database.run("CV04 TDG: vložení materiálu", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO cv04_materials (material_id, name) VALUES (?, ?)")) {
                statement.setString(1, material.materialId());
                statement.setString(2, material.name());
                return statement.executeUpdate();
            }
        });
    }

    public void update(MaterialDto material) {
        database.run("CV04 TDG: úprava materiálu", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE cv04_materials SET name = ? WHERE material_id = ?")) {
                statement.setString(1, material.name());
                statement.setString(2, material.materialId());
                return statement.executeUpdate();
            }
        });
    }

    public Optional<MaterialDto> findById(String materialId) {
        return database.run("CV04 TDG: načtení materiálu", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT material_id, name FROM cv04_materials WHERE material_id = ?")) {
                statement.setString(1, materialId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(read(rs)) : Optional.empty();
                }
            }
        });
    }

    public List<MaterialDto> findAll() {
        return database.run("CV04 TDG: načtení materiálů", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT material_id, name FROM cv04_materials ORDER BY material_id");
                 ResultSet rs = statement.executeQuery()) {
                List<MaterialDto> materials = new ArrayList<>();
                while (rs.next()) {
                    materials.add(read(rs));
                }
                return materials;
            }
        });
    }

    private static MaterialDto read(ResultSet rs) throws SQLException {
        return new MaterialDto(rs.getString("material_id"), rs.getString("name"));
    }
}
