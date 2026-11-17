package dataaccess.h2;

import dataaccess.dto.FinishDto;
import dataaccess.persistence.RecordIds;
import dataaccess.gateway.FinishGateway;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Table Data Gateway used by the Data Mapper (H2 table finishes). */
public class H2FinishGateway implements FinishGateway {
    private static final String ENTITY = "Povrchová úprava";
    private final H2Database database;

    public H2FinishGateway(H2Database database) {
        this.database = database;
    }

    @Override
    public void insert(FinishDto finish) {
        String id = RecordIds.require(finish.finishId(), ENTITY);
        database.run("Vložení povrchové úpravy " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO finishes (finish_id, name) VALUES (?, ?)")) {
                statement.setString(1, id);
                statement.setString(2, finish.name());
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public void update(FinishDto finish) {
        String id = RecordIds.require(finish.finishId(), ENTITY);
        int updated = database.run("Úprava povrchové úpravy " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE finishes SET name = ? WHERE finish_id = ?")) {
                statement.setString(1, finish.name());
                statement.setString(2, id);
                return statement.executeUpdate();
            }
        });
        if (updated == 0) {
            throw RecordIds.missing(ENTITY, id);
        }
    }

    /** A finish still linked to a material cannot be deleted (FK without cascade). */
    @Override
    public void delete(String id) {
        RecordIds.require(id, ENTITY);
        int deleted = database.run("Smazání povrchové úpravy " + id,
                connection -> H2Records.deleteById(connection, "finishes", "finish_id", id));
        if (deleted == 0) {
            throw RecordIds.missing(ENTITY, id);
        }
    }

    @Override
    public Optional<FinishDto> findById(String id) {
        return database.run("Načtení povrchové úpravy " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT finish_id, name FROM finishes WHERE finish_id = ?")) {
                statement.setString(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<FinishDto> findAll() {
        return database.run("Načtení povrchových úprav", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT finish_id, name FROM finishes ORDER BY finish_id");
                 ResultSet rs = statement.executeQuery()) {
                List<FinishDto> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return result;
            }
        });
    }

    private static FinishDto map(ResultSet rs) throws SQLException {
        return new FinishDto(rs.getString("finish_id"), rs.getString("name"));
    }
}
