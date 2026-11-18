package atag.export.collect;

import atag.model.Ramen.Concept;
import atag.profile.ExportProfile;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;

import java.util.List;

/**
 * The rule set that drives a {@link TraversalCollector}: which relationship types to
 * follow in each direction, and which neighbouring nodes to keep. Both come from the
 * export profile, so the scope of an export is declared rather than hard-coded - the
 * defaults describe the ATAG text/annotation schema, and a project with a different
 * schema overrides them in its profile.
 */
public class TraversalRules {

    private final RelationshipType[] incomingTypes;
    private final RelationshipType[] outgoingTypes;
    private final ExportProfile profile;

    private TraversalRules(ExportProfile profile) {
        this.incomingTypes = toTypes(profile.followIncoming());
        this.outgoingTypes = toTypes(profile.followOutgoing());
        this.profile = profile;
    }

    public static TraversalRules from(ExportProfile profile) {
        return new TraversalRules(profile);
    }

    RelationshipType[] incomingTypes() {
        return incomingTypes;
    }

    RelationshipType[] outgoingTypes() {
        return outgoingTypes;
    }

    /**
     * A neighbour is kept unless it is an annotation whose type is not part of an
     * explicit annotation type allow-list.
     */
    boolean accepts(Node node) {
        List<String> annotationTypes = profile.annotationTypes();
        if (annotationTypes == null || !profile.model().is(node, Concept.ANNOTATION)) {
            return true;
        }
        Object type = node.getProperty(profile.dictionary().typeProperty(), null);
        return type != null && annotationTypes.contains(type.toString());
    }

    private static RelationshipType[] toTypes(List<String> names) {
        return names.stream().map(RelationshipType::withName).toArray(RelationshipType[]::new);
    }
}
