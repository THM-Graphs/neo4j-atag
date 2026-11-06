package atag.export.format;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.spatial.Point;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Shared conversion of Neo4j property values into the primitive types the export
 * formats understand. Keeping this in one place means every format supports the
 * same set of property types.
 */
public final class PropertyValues {

    public static final String POINT_TYPE_KEY = "type";
    public static final String POINT_TYPE = "Point";
    public static final String POINT_SRID_KEY = "srid";
    public static final String POINT_COORDINATES_KEY = "coordinates";

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
            } else if (value instanceof Point point) {
                writePoint(point, target.putObject(key));
            } else if (value instanceof String[] arr) {
                ArrayNode arrayNode = target.putArray(key);
                for (String s : arr) {
                    arrayNode.add(s);
                }
            } else if (value instanceof long[] arr) {
                ArrayNode arrayNode = target.putArray(key);
                for (long l : arr) {
                    arrayNode.add(l);
                }
            } else if (value instanceof int[] arr) {
                ArrayNode arrayNode = target.putArray(key);
                for (int i : arr) {
                    arrayNode.add(i);
                }
            } else if (value instanceof double[] arr) {
                ArrayNode arrayNode = target.putArray(key);
                for (double d : arr) {
                    arrayNode.add(d);
                }
            } else if (value instanceof boolean[] arr) {
                ArrayNode arrayNode = target.putArray(key);
                for (boolean b : arr) {
                    arrayNode.add(b);
                }
            } else {
                throw new IllegalArgumentException("Unsupported property type: " + value.getClass().getName());
            }
        });
    }

    /**
     * A point is written as a GeoJSON-like object so that its coordinate reference system
     * survives: {@code {"type":"Point","srid":4326,"coordinates":[x,y]}}.
     */
    private static void writePoint(Point point, ObjectNode target) {
        target.put(POINT_TYPE_KEY, POINT_TYPE);
        target.put(POINT_SRID_KEY, point.getCRS().getCode());
        ArrayNode coordinates = target.putArray(POINT_COORDINATES_KEY);
        for (double coordinate : point.getCoordinate().getCoordinate()) {
            coordinates.add(coordinate);
        }
    }

    /**
     * Render a single property value as text, for attribute-based formats such as XML.
     */
    public static String asString(Object value) {
        if (value instanceof String[] arr) {
            return String.join(",", arr);
        }
        if (value instanceof long[] arr) {
            return Arrays.stream(arr).mapToObj(String::valueOf).collect(Collectors.joining(","));
        }
        if (value instanceof int[] arr) {
            return Arrays.stream(arr).mapToObj(String::valueOf).collect(Collectors.joining(","));
        }
        if (value instanceof double[] arr) {
            return Arrays.stream(arr).mapToObj(String::valueOf).collect(Collectors.joining(","));
        }
        if (value instanceof boolean[] arr) {
            StringJoiner joiner = new StringJoiner(",");
            for (boolean b : arr) {
                joiner.add(String.valueOf(b));
            }
            return joiner.toString();
        }
        if (value instanceof Point point) {
            return Arrays.stream(point.getCoordinate().getCoordinate())
                    .mapToObj(String::valueOf).collect(Collectors.joining(","));
        }
        return String.valueOf(value);
    }
}
