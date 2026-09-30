package studybuddy.backend.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts aggregates to and from plain document maps. Going through JSON keeps documents to
 * simple values (strings, numbers, booleans, lists, maps) that any store accepts, and gives every
 * read a fresh object graph.
 */
@Component
public class DocumentCodec {
    static final String ID_FIELD = "_id";
    static final String KIND_FIELD = "_kind";
    private static final TypeReference<LinkedHashMap<String, Object>> DOCUMENT = new TypeReference<>() {};

    private final ObjectMapper json;

    public DocumentCodec(ObjectMapper json) {
        this.json = json;
    }

    /** The collection an aggregate type is stored in, e.g. {@code StudentProfile}. */
    public String kindOf(Class<?> type) {
        return type.getSimpleName();
    }

    public Map<String, Object> encode(String id, Object value) {
        try {
            Map<String, Object> document = json.readValue(json.writeValueAsString(value), DOCUMENT);
            document.put(ID_FIELD, id);
            document.put(KIND_FIELD, kindOf(value.getClass()));
            return document;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize aggregate", e);
        }
    }

    public <T> T decode(Map<String, Object> document, Class<T> type) {
        Map<String, Object> fields = new LinkedHashMap<>(document);
        fields.remove(ID_FIELD);
        fields.remove(KIND_FIELD);
        try {
            return json.convertValue(fields, type);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Cannot deserialize aggregate", e);
        }
    }
}
