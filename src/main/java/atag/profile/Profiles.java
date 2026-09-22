package atag.profile;

import atag.model.MetaGraph;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.graphdb.Transaction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A profile as one named artifact rather than a map repeated in every call.
 * <p>
 * A stored profile carries the project model, the dictionary, the document structure and
 * the keys of both directions; a call refers to it with {@code {profile: 'name'}} and may
 * still override single keys. The sections {@code import} and {@code export} let one
 * profile hold keys that mean different things in each direction - the rest of the
 * pipeline never sees them, because resolution flattens a profile back into the map the
 * profile classes have always read.
 */
public final class Profiles {

    public static final String PROFILE = "profile";
    public static final String IMPORT = "import";
    public static final String EXPORT = "export";
    /** Sections that are not profile keys of their own. */
    private static final Set<String> SECTIONS = Set.of(IMPORT, EXPORT);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Profiles() {
    }

    /**
     * Flatten the profile a call refers to, the section that applies to this direction,
     * and the keys of the call itself into the map the profile classes read. Later wins,
     * so a call can always override what the stored profile says.
     *
     * @param section {@link #IMPORT} or {@link #EXPORT}
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> resolve(Map<String, Object> config, Transaction tx, String section) {
        Object reference = config == null ? null : config.get(PROFILE);
        if (reference == null) {
            return config == null ? Map.of() : config;
        }

        Map<String, Object> base = reference instanceof Map
                ? (Map<String, Object>) reference
                : stored((String) reference, tx);

        Map<String, Object> resolved = new LinkedHashMap<>();
        base.forEach((key, value) -> {
            if (!SECTIONS.contains(key)) {
                resolved.put(key, value);
            }
        });
        if (base.get(section) instanceof Map) {
            resolved.putAll((Map<String, Object>) base.get(section));
        }
        config.forEach((key, value) -> {
            if (!PROFILE.equals(key)) {
                resolved.put(key, value);
            }
        });
        return resolved;
    }

    private static Map<String, Object> stored(String name, Transaction tx) {
        if (tx == null) {
            throw new IllegalArgumentException(
                    "profile: '" + name + "' can only be used where a transaction is available");
        }
        String json = MetaGraph.readProfileJson(tx, name);
        if (json == null) {
            throw new IllegalArgumentException("no profile named '" + name + "' has been written");
        }
        return fromJson(json);
    }

    /** A profile as it is stored: JSON, because a profile nests and a property cannot. */
    public static String toJson(Map<String, Object> profile) {
        try {
            return MAPPER.writeValueAsString(profile);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("profile cannot be stored as JSON: " + e.getMessage(), e);
        }
    }

    public static Map<String, Object> fromJson(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("not a JSON object: " + e.getMessage(), e);
        }
    }
}
