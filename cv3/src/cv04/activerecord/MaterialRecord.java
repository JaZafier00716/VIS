package cv04.activerecord;

import dataaccess.h2.H2Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Active Record for materials, with the slide's static Material.Exists. */
public class MaterialRecord {
    private final H2Database database;
    private final String materialId;
    private final String name;

    public MaterialRecord(H2Database database, String materialId, String name) {
        this.database = database;
        this.materialId = materialId;
        this.name = name;
    }

    public static boolean exists(H2Database database, String materialId) {
        return database.run("CV04 AR: existence materiálu", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM cv04_materials WHERE material_id = ?")) {
                statement.setString(1, materialId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    public void validate() {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Název materiálu je povinný.");
    }

    public void save() {
        validate();
        database.run("CV04 AR: uložení materiálu", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "MERGE INTO cv04_materials (material_id, name) KEY (material_id) VALUES (?, ?)")) {
                statement.setString(1, materialId);
                statement.setString(2, name);
                return statement.executeUpdate();
            }
        });
    }

    public String getMaterialId() { return materialId; }
    public String getName() { return name; }
}
