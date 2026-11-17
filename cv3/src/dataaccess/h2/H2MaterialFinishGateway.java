package dataaccess.h2;

import dataaccess.persistence.RecordIds;
import dataaccess.gateway.MaterialFinishGateway;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.TreeSet;

/**
 * Table Data Gateway used by the Data Mapper (H2 table material_finishes).
 * Association table material_finishes. The composite PK forbids duplicate pairs and the two FKs forbid
 * references to missing materials or finishes; synchronize() writes only the difference.
 */
public class H2MaterialFinishGateway implements MaterialFinishGateway {
    private final H2Database database;

    public H2MaterialFinishGateway(H2Database database) {
        this.database = database;
    }

    @Override
    public Set<String> findFinishIds(String materialId) {
        return database.run("Načtení povrchových úprav materiálu " + materialId,
                connection -> ids(connection, "SELECT finish_id FROM material_finishes WHERE material_id = ?", materialId));
    }

    @Override
    public Set<String> findMaterialIds(String finishId) {
        return database.run("Načtení materiálů s povrchovou úpravou " + finishId,
                connection -> ids(connection, "SELECT material_id FROM material_finishes WHERE finish_id = ?", finishId));
    }

    @Override
    public void synchronize(String materialId, Set<String> finishIds) {
        RecordIds.require(materialId, "Materiál");
        database.inTransaction("Uložení vazeb materiálu " + materialId, connection -> {
            Set<String> stored = ids(connection, "SELECT finish_id FROM material_finishes WHERE material_id = ?", materialId);
            Set<String> toAdd = new TreeSet<>(finishIds);
            toAdd.removeAll(stored);
            Set<String> toRemove = new TreeSet<>(stored);
            toRemove.removeAll(finishIds);
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM material_finishes WHERE material_id = ? AND finish_id = ?")) {
                for (String finishId : toRemove) {
                    delete.setString(1, materialId);
                    delete.setString(2, finishId);
                    delete.executeUpdate();
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO material_finishes (material_id, finish_id) VALUES (?, ?)")) {
                for (String finishId : toAdd) {
                    insert.setString(1, materialId);
                    insert.setString(2, finishId);
                    insert.executeUpdate();
                }
            }
            if (toAdd.isEmpty() && toRemove.isEmpty()) {
                requireMaterial(connection, materialId);
            }
            return null;
        });
    }

    /** With nothing to write, the FK cannot report a missing material, so check it explicitly. */
    private static void requireMaterial(Connection connection, String materialId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM materials WHERE material_id = ?")) {
            statement.setString(1, materialId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Vazba odkazuje na neexistující materiál " + materialId + ".");
                }
            }
        }
    }

    private static Set<String> ids(Connection connection, String sql, String parameter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet rs = statement.executeQuery()) {
                Set<String> result = new TreeSet<>();
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
                return result;
            }
        }
    }
}
