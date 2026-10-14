package studybuddy.backend.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Stores template POJO aggregates as JSON in H2; domain rules remain in services. Reads deserialize
 * fresh objects so a failed transaction cannot mutate cached state.
 */
@Repository
public class StudyRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public StudyRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // All business mutations acquire this database lock inside a transaction. This small
    // demo favors predictable cross-aggregate integrity over concurrent write throughput.
    public void lock() {
        jdbc.queryForObject("SELECT id FROM mutation_lock WHERE id = 1 FOR UPDATE", Integer.class);
    }

    public <T> List<T> all(Class<T> type) {
        return jdbc.query(
                "SELECT payload FROM aggregates WHERE kind = ? ORDER BY id",
                (rs, row) -> decode(rs.getString(1), type),
                type.getSimpleName());
    }

    public <T> Optional<T> find(Class<T> type, String id) {
        return jdbc
                .query(
                        "SELECT payload FROM aggregates WHERE kind = ? AND id = ?",
                        (rs, row) -> decode(rs.getString(1), type),
                        type.getSimpleName(),
                        id)
                .stream()
                .findFirst();
    }

    public <T> T save(String id, T value) {
        try {
            jdbc.update(
                    "MERGE INTO aggregates (kind, id, payload) KEY(kind, id) VALUES (?, ?, ?)",
                    value.getClass().getSimpleName(),
                    id,
                    json.writeValueAsString(value));
            return value;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize aggregate", e);
        }
    }

    public void delete(Class<?> type, String id) {
        jdbc.update("DELETE FROM aggregates WHERE kind = ? AND id = ?", type.getSimpleName(), id);
    }

    private <T> T decode(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot deserialize aggregate", e);
        }
    }
}
