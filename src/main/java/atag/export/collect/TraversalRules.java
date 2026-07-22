package atag.export.collect;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;

import java.util.ArrayList;
import java.util.List;

/**
 * The rule set that drives a {@link TraversalCollector}: which relationship types
 * to follow in each direction, and which neighbouring nodes to keep.
 * <p>
 * Defaults describe the ATAG text/annotation schema (a start node is followed
 * <em>down</em> to its text and all annotations), but every rule can be overridden
 * through the export {@code config} map so the same collector serves other schemas.
 */
public class TraversalRules {

    private static final Label ANNOTATION_LABEL = Label.label("Annotation");

    private static final List<String> DEFAULT_INCOMING = List.of("PART_OF");
    private static final List<String> DEFAULT_OUTGOING = List.of("HAS_ANNOTATION", "NEXT_TOKEN", "REFERS_TO");
    private static final List<String> CHARACTER_CHAIN_OUTGOING = List.of(
            "NEXT_CHARACTER", "TOKEN_START", "TOKEN_END",
            "STANDOFF_START", "STANDOFF_END", "CHARACTER_HAS_ANNOTATION");

    private final RelationshipType[] incomingTypes;
    private final RelationshipType[] outgoingTypes;
    private final List<String> annotationTypes;

    private TraversalRules(List<String> incoming, List<String> outgoing, List<String> annotationTypes) {
        this.incomingTypes = toTypes(incoming);
        this.outgoingTypes = toTypes(outgoing);
        this.annotationTypes = annotationTypes;
    }

    @SuppressWarnings("unchecked")
    public static TraversalRules from(java.util.Map<String, Object> config) {
        boolean includeCharacterChain = !Boolean.FALSE.equals(config.get("includeCharacterChain"));

        List<String> incoming = (List<String>) config.getOrDefault("followIncoming", DEFAULT_INCOMING);

        List<String> outgoing = new ArrayList<>(
                (List<String>) config.getOrDefault("followOutgoing", DEFAULT_OUTGOING));
        if (includeCharacterChain && !config.containsKey("followOutgoing")) {
            outgoing.addAll(CHARACTER_CHAIN_OUTGOING);
        }

        List<String> annotationTypes = (List<String>) config.get("annotationTypes");
        return new TraversalRules(incoming, outgoing, annotationTypes);
    }

    RelationshipType[] incomingTypes() {
        return incomingTypes;
    }

    RelationshipType[] outgoingTypes() {
        return outgoingTypes;
    }

    /**
     * A neighbour is kept unless it is an {@code Annotation} whose {@code type} is
     * not part of an explicit {@code annotationTypes} allow-list.
     */
    boolean accepts(Node node) {
        if (annotationTypes == null || !node.hasLabel(ANNOTATION_LABEL)) {
            return true;
        }
        String type = (String) node.getProperty("type", null);
        return type != null && annotationTypes.contains(type);
    }

    private static RelationshipType[] toTypes(List<String> names) {
        return names.stream().map(RelationshipType::withName).toArray(RelationshipType[]::new);
    }
}
