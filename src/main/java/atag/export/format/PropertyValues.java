package atag.export.format;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.neo4j.graphdb.Entity;

import java.time.LocalDate;

/**
 * Shared conversion of Neo4j property values into the primitive types the export
 * formats understand. Keeping this in one place means every format supports the
 * same set of property types.
 */
public final class PropertyValues {

    private PropertyValues() {
    }

    /**
     * Write all of an entity's properties into a JSON object node.
     */
    public static void writeJson(Entity entity, ObjectNode target) {
        entity.getAllProperties().forEach((key, value) -> {
            if (value instanceof String str) {
                target.put(key, str);
            } else if (value instanceof Integer i) {
                target.put(key, i);
            } else if (value instanceof Long l) {
                target.put(key, l);
            } else if (value instanceof Double d) {
                target.put(key, d);
            } else if (value instanceof Boolean b) {
                target.put(key, b);
            } else if (value instanceof LocalDate d) {
                target.put(key, d.toString());
            } else if (value instanceof String[] arr) {
                ArrayNode arrayNode = target.putArray(key);
                for (String s : arr) {
                    arrayNode.add(s);
                }
            } else {
                throw new IllegalArgumentException("Unsupported property type: " + value.getClass().getName());
            }
        });
    }

    /**
     * Render a single property value as text, for attribute-based formats such as XML.
     */
    public static String asString(Object value) {
        if (value instanceof String[] arr) {
            return String.join(",", arr);
        }
        return String.valueOf(value);
    }
}
