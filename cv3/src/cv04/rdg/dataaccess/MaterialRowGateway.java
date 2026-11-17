package cv04.rdg.dataaccess;

import dataaccess.h2.H2Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Row Data Gateway: one object = one cv04_materials row. */
public class MaterialRowGateway {
    private final H2Database database;
    private final String materialId;
    private String name;

    public MaterialRowGateway(H2Database database, String materialId, String name) {
        this.database = database;
        this.materialId = materialId;
        this.name = name;
    }

    public static Optional<MaterialRowGateway> find(H2Database database, String materialId) {
        return database.run("CV04 RDG: načtení materiálu", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT material_id, name FROM cv04_materials WHERE material_id = ?")) {
                statement.setString(1, materialId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(read(database, rs)) : Optional.empty();
                }
            }
        });
    }

    public static List<MaterialRowGateway> findAll(H2Database database) {
        return database.run("CV04 RDG: načtení materiálů", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT material_id, name FROM cv04_materials ORDER BY material_id");
                 ResultSet rs = statement.executeQuery()) {
                List<MaterialRowGateway> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(read(database, rs));
                }
                return rows;
            }
        });
    }

    public void insert() {
        database.run("CV04 RDG: vložení materiálu", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO cv04_materials (material_id, name) VALUES (?, ?)")) {
                statement.setString(1, materialId);
                statement.setString(2, name);
                return statement.executeUpdate();
            }
        });
    }

    public void update() {
        database.run("CV04 RDG: úprava materiálu", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE cv04_materials SET name = ? WHERE material_id = ?")) {
                statement.setString(1, name);
                statement.setString(2, materialId);
                return statement.executeUpdate();
            }
        });
    }

    public void delete() {
        database.run("CV04 RDG: smazání materiálu", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM cv04_materials WHERE material_id = ?")) {
                statement.setString(1, materialId);
                return statement.executeUpdate();
            }
        });
    }

    private static MaterialRowGateway read(H2Database database, ResultSet rs) throws SQLException {
        return new MaterialRowGateway(database, rs.getString("material_id"), rs.getString("name"));
    }

    public String getMaterialId() { return materialId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
