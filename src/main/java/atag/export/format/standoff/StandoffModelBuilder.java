package atag.export.format.standoff;

import atag.export.Subgraph;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schema-dependent step that interprets a {@link Subgraph} as an ATAG text document.
 * Anchor nodes ({@code Text} or {@code Collection}) form a tree along the
 * {@code PART_OF} hierarchy, and every {@code Annotation} is placed on the anchor it
 * hangs off via {@code HAS_ANNOTATION}. Annotations that hang off another annotation
 * (e.g. a commentary) are nested inside that originating annotation. Within each
 * parent, annotations are ordered by {@code startIndex}. Annotations without a
 * {@code HAS_ANNOTATION} parent (neither an anchor nor another annotation) are
 * excluded. The resulting {@link StandoffDocument} is independent of the target
 * serialization format.
 */
public class StandoffModelBuilder {

    private static final Label ANNOTATION = Label.label("Annotation");
    private static final Label TEXT = Label.label("Text");
    private static final Label COLLECTION = Label.label("Collection");
    private static final RelationshipType HAS_ANNOTATION = RelationshipType.withName("HAS_ANNOTATION");
    private static final RelationshipType PART_OF = RelationshipType.withName("PART_OF");

    public StandoffDocument build(Subgraph subgraph) {
        Map<String, MutableNode> anchors = new LinkedHashMap<>();
        Map<String, MutableAnnotation> annotations = new LinkedHashMap<>();
        for (Node node : subgraph.nodes()) {
            if (node.hasLabel(ANNOTATION)) {
                annotations.put(node.getElementId(), new MutableAnnotation(node));
            } else if (isAnchor(node)) {
                anchors.put(node.getElementId(), new MutableNode(node));
            }
        }

        List<MutableNode> roots = new ArrayList<>();
        for (MutableNode anchor : anchors.values()) {
            MutableNode parent = parentAnchorOf(anchor.node, anchors);
            if (parent == null) {
                roots.add(anchor);
            } else {
                parent.children.add(anchor);
            }
        }

        for (MutableAnnotation annotation : annotations.values()) {
            Node parent = annotationParent(annotation.node, anchors, annotations);
            if (parent == null) {
                continue;
            }
            if (annotations.containsKey(parent.getElementId())) {
                annotations.get(parent.getElementId()).children.add(annotation);
            } else {
                anchors.get(parent.getElementId()).annotations.add(annotation);
            }
        }

        MutableNode root;
        if (roots.size() == 1) {
            root = roots.get(0);
        } else {
            root = new MutableNode("document", Map.of());
            root.children.addAll(roots);
        }

        return root.toDocument();
    }

    private boolean isAnchor(Node node) {
        return node.hasLabel(TEXT) || node.hasLabel(COLLECTION);
    }

    private MutableNode parentAnchorOf(Node anchor, Map<String, MutableNode> anchors) {
        for (Relationship rel : anchor.getRelationships(Direction.OUTGOING, PART_OF)) {
            MutableNode parent = anchors.get(rel.getEndNode().getElementId());
            if (parent != null) {
                return parent;
            }
        }
        return null;
    }

    private Node annotationParent(Node annotation,
                                  Map<String, MutableNode> anchors,
                                  Map<String, MutableAnnotation> annotations) {
        for (Relationship rel : annotation.getRelationships(Direction.INCOMING, HAS_ANNOTATION)) {
            Node source = rel.getStartNode();
            String id = source.getElementId();
            if (anchors.containsKey(id) || annotations.containsKey(id)) {
                return source;
            }
        }
        return null;
    }

    private static String primaryLabel(Node node) {
        return node.getLabels().iterator().hasNext()
                ? node.getLabels().iterator().next().name()
                : "document";
    }

    private static long startIndex(Map<String, Object> annotation) {
        Object value = annotation.get("startIndex");
        return value instanceof Number number ? number.longValue() : Long.MAX_VALUE;
    }

    private static final class MutableNode {
        private final Node node;
        private final String name;
        private final Map<String, Object> properties;
        private final List<MutableAnnotation> annotations = new ArrayList<>();
        private final List<MutableNode> children = new ArrayList<>();

        MutableNode(Node node) {
            this.node = node;
            this.name = primaryLabel(node);
            this.properties = new LinkedHashMap<>(node.getAllProperties());
        }

        MutableNode(String name, Map<String, Object> properties) {
            this.node = null;
            this.name = name;
            this.properties = new LinkedHashMap<>(properties);
        }

        StandoffDocument toDocument() {
            annotations.sort(Comparator.comparingLong(a -> startIndex(a.properties)));
            List<StandoffAnnotation> annotationModels = new ArrayList<>();
            for (MutableAnnotation annotation : annotations) {
                annotationModels.add(annotation.toModel());
            }
            List<StandoffDocument> childDocuments = new ArrayList<>();
            for (MutableNode child : children) {
                childDocuments.add(child.toDocument());
            }
            return new StandoffDocument(name, properties, annotationModels, childDocuments);
        }
    }

    private static final class MutableAnnotation {
        private final Node node;
        private final Map<String, Object> properties;
        private final List<MutableAnnotation> children = new ArrayList<>();

        MutableAnnotation(Node node) {
            this.node = node;
            this.properties = new LinkedHashMap<>(node.getAllProperties());
        }

        StandoffAnnotation toModel() {
            children.sort(Comparator.comparingLong(a -> startIndex(a.properties)));
            List<StandoffAnnotation> childModels = new ArrayList<>();
            for (MutableAnnotation child : children) {
                childModels.add(child.toModel());
            }
            return new StandoffAnnotation(properties, childModels);
        }
    }
}
