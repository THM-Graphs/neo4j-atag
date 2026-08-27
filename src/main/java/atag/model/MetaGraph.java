package atag.model;

import atag.model.Ramen.Concept;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The project model stored as graph data, next to the instance data it describes.
 * <p>
 * Each RAMEN concept becomes a {@code (:Meta:Concept)} node, each label a project uses
 * for that concept a {@code (:Meta:Type)} node refining it, and the RAMEN relations
 * become relationships between the concept nodes - carrying the very relationship types
 * the instance data uses. A profile can therefore say {@code model: 'meta'} and have the
 * model read back from the database instead of repeating it in every call, and the model
 * itself can be queried and traversed with plain Cypher.
 */
public final class MetaGraph {

    public static final Label META = Label.label("Meta");
    public static final Label CONCEPT = Label.label("Concept");
    public static final Label TYPE = Label.label("Type");
    private static final RelationshipType REFINES = RelationshipType.withName("REFINES");

    private MetaGraph() {
    }

    /**
     * Write the model, reusing the concept and type nodes that are already there, so that
     * writing the same model twice does not duplicate it.
     */
    public static Map<String, Object> write(Transaction tx, ProjectModel model) {
        Map<Concept, Node> concepts = new LinkedHashMap<>();
        long types = 0;
        for (Concept concept : Concept.values()) {
            Node conceptNode = merge(tx, CONCEPT, "name", concept.name());
            concepts.put(concept, conceptNode);
            List<String> labels = model.labels(concept);
            for (int position = 0; position < labels.size(); position++) {
                Node typeNode = merge(tx, TYPE, "label", labels.get(position));
                typeNode.setProperty("position", (long) position);
                relate(typeNode, conceptNode, REFINES);
                types++;
            }
        }

        relate(concepts.get(Concept.CONTENT), concepts.get(Concept.COLLECTION), model.partOf());
        relate(concepts.get(Concept.COLLECTION), concepts.get(Concept.COLLECTION), model.partOf());
        relate(concepts.get(Concept.CONTENT), concepts.get(Concept.ANNOTATION), model.hasAnnotation());
        relate(concepts.get(Concept.ANNOTATION), concepts.get(Concept.ANNOTATION), model.hasAnnotation());
        relate(concepts.get(Concept.ANNOTATION), concepts.get(Concept.ENTITY), model.refersTo());

        return Map.of("concepts", (long) concepts.size(), "types", types);
    }

    /**
     * Read the model back. Concepts the meta graph does not describe keep their defaults,
     * so a partially modelled project is still usable.
     */
    public static ProjectModel read(Transaction tx) {
        Map<Concept, List<String>> labels = new LinkedHashMap<>();
        Map<Concept, Node> concepts = new LinkedHashMap<>();
        for (Concept concept : Concept.values()) {
            Node conceptNode = tx.findNode(CONCEPT, "name", concept.name());
            if (conceptNode == null) {
                continue;
            }
            concepts.put(concept, conceptNode);
            List<String> refinements = refinementsOf(conceptNode);
            if (!refinements.isEmpty()) {
                labels.put(concept, refinements);
            }
        }

        return new ProjectModel(labels,
                relationBetween(concepts, Concept.CONTENT, Concept.COLLECTION, Ramen.PART_OF.name()),
                relationBetween(concepts, Concept.CONTENT, Concept.ANNOTATION, Ramen.HAS_ANNOTATION.name()),
                relationBetween(concepts, Concept.ANNOTATION, Concept.ENTITY, Ramen.REFERS_TO.name()));
    }

    private static List<String> refinementsOf(Node conceptNode) {
        List<Node> typeNodes = new ArrayList<>();
        conceptNode.getRelationships(Direction.INCOMING, REFINES)
                .forEach(relationship -> typeNodes.add(relationship.getStartNode()));
        typeNodes.sort(Comparator.comparingLong(node -> (long) node.getProperty("position", 0L)));
        return typeNodes.stream().map(node -> (String) node.getProperty("label")).toList();
    }

    private static String relationBetween(Map<Concept, Node> concepts, Concept from, Concept to, String fallback) {
        Node source = concepts.get(from);
        Node target = concepts.get(to);
        if (source == null || target == null) {
            return fallback;
        }
        for (Relationship relationship : source.getRelationships(Direction.OUTGOING)) {
            if (relationship.getEndNode().equals(target)) {
                return relationship.getType().name();
            }
        }
        return fallback;
    }

    private static Node merge(Transaction tx, Label label, String key, String value) {
        Node node = tx.findNode(label, key, value);
        if (node != null) {
            return node;
        }
        Node created = tx.createNode(META, label);
        created.setProperty(key, value);
        return created;
    }

    private static void relate(Node source, Node target, RelationshipType type) {
        for (Relationship relationship : source.getRelationships(Direction.OUTGOING, type)) {
            if (relationship.getEndNode().equals(target)) {
                return;
            }
        }
        source.createRelationshipTo(target, type);
    }
}
