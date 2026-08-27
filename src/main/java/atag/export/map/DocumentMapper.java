package atag.export.map;

import atag.export.Subgraph;
import atag.export.map.MappedExport.MappedAnnotation;
import atag.export.map.MappedExport.MappedDocument;
import atag.export.map.MappedExport.MappedEntity;
import atag.model.Ramen.Concept;
import atag.profile.Dictionary;
import atag.profile.ExportProfile;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 2 of the export pipeline: interpret a collected {@link Subgraph} in terms of the
 * profile's project model and translate it with the profile's dictionary. Anchors
 * ({@code Collection} and {@code Content} nodes) form a tree along the {@code PART_OF}
 * hierarchy, every annotation is placed on the anchor - or the annotation - it hangs off
 * via {@code HAS_ANNOTATION}, and {@code REFERS_TO} relations become entity references.
 * Within each parent, annotations are ordered by {@code startIndex}. Annotations without
 * a {@code HAS_ANNOTATION} parent in the subgraph are excluded, since there is nothing
 * they could be serialized against.
 */
public class DocumentMapper {

    private final ExportProfile profile;

    public DocumentMapper(ExportProfile profile) {
        this.profile = profile;
    }

    public MappedExport map(Subgraph subgraph) {
        Map<String, MutableAnchor> anchors = new LinkedHashMap<>();
        Map<String, MutableAnnotation> annotations = new LinkedHashMap<>();
        List<MappedEntity> entities = new ArrayList<>();

        for (Node node : subgraph.nodes()) {
            Concept concept = profile.model().conceptOf(node);
            if (concept == null) {
                continue;
            }
            switch (concept) {
                case ANNOTATION -> annotations.put(node.getElementId(), new MutableAnnotation(node, profile));
                case CONTENT, COLLECTION -> anchors.put(node.getElementId(), new MutableAnchor(node, concept, profile));
                case ENTITY -> entities.add(entity(node));
            }
        }

        List<MutableAnchor> roots = new ArrayList<>();
        for (MutableAnchor anchor : anchors.values()) {
            MutableAnchor parent = parentAnchorOf(anchor.node, anchors);
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

        MutableAnchor root;
        if (roots.size() == 1) {
            root = roots.get(0);
        } else {
            root = new MutableAnchor("document", Concept.COLLECTION);
            root.children.addAll(roots);
        }
        return new MappedExport(root.toDocument(), entities);
    }

    private MutableAnchor parentAnchorOf(Node anchor, Map<String, MutableAnchor> anchors) {
        if (anchor == null) {
            return null;
        }
        for (Relationship rel : anchor.getRelationships(Direction.OUTGOING, profile.model().partOf())) {
            MutableAnchor parent = anchors.get(rel.getEndNode().getElementId());
            if (parent != null) {
                return parent;
            }
        }
        return null;
    }

    private Node annotationParent(Node annotation,
                                  Map<String, MutableAnchor> anchors,
                                  Map<String, MutableAnnotation> annotations) {
        for (Relationship rel : annotation.getRelationships(Direction.INCOMING, profile.model().hasAnnotation())) {
            Node source = rel.getStartNode();
            String id = source.getElementId();
            if (anchors.containsKey(id) || annotations.containsKey(id)) {
                return source;
            }
        }
        return null;
    }

    private MappedEntity entity(Node node) {
        List<String> labels = new ArrayList<>();
        node.getLabels().forEach(label -> labels.add(label.name()));
        return new MappedEntity(string(node, profile.entityKey()), labels, string(node, "label"),
                mapProperties(node));
    }

    private List<String> referencesOf(Node annotation) {
        List<String> result = new ArrayList<>();
        for (Relationship rel : annotation.getRelationships(Direction.OUTGOING, profile.model().refersTo())) {
            Node entity = rel.getEndNode();
            String id = string(entity, profile.entityKey());
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    /** Property keys are translated by the dictionary, ignored keys are left out entirely. */
    private Map<String, Object> mapProperties(Node node) {
        Dictionary dictionary = profile.dictionary();
        Map<String, Object> result = new LinkedHashMap<>();
        node.getAllProperties().forEach((key, value) -> {
            if (!profile.ignoreProperties().contains(key)) {
                result.put(dictionary.attributeFor(key), value);
            }
        });
        return result;
    }

    private static String string(Node node, String key) {
        Object value = node.getProperty(key, null);
        return value == null ? null : value.toString();
    }

    private static Long number(Node node, String key) {
        Object value = node.getProperty(key, null);
        return value instanceof Number number ? number.longValue() : null;
    }

    private static String primaryLabel(Node node) {
        return node.getLabels().iterator().hasNext()
                ? node.getLabels().iterator().next().name()
                : "document";
    }

    private static long orderOf(MappedAnnotation annotation) {
        return annotation.startIndex() == null ? Long.MAX_VALUE : annotation.startIndex();
    }

    private final class MutableAnchor {
        private final Node node;
        private final String name;
        private final Concept concept;
        private final Map<String, Object> properties;
        private final List<MutableAnnotation> annotations = new ArrayList<>();
        private final List<MutableAnchor> children = new ArrayList<>();

        MutableAnchor(Node node, Concept concept, ExportProfile profile) {
            this.node = node;
            this.name = primaryLabel(node);
            this.concept = concept;
            this.properties = mapProperties(node);
        }

        MutableAnchor(String name, Concept concept) {
            this.node = null;
            this.name = name;
            this.concept = concept;
            this.properties = Map.of();
        }

        MappedDocument toDocument() {
            List<MappedAnnotation> annotationModels = new ArrayList<>();
            for (MutableAnnotation annotation : annotations) {
                annotationModels.add(annotation.toModel());
            }
            annotationModels.sort(Comparator.comparingLong(DocumentMapper::orderOf));

            List<MappedDocument> childDocuments = new ArrayList<>();
            for (MutableAnchor child : children) {
                childDocuments.add(child.toDocument());
            }
            return new MappedDocument(name, concept,
                    node == null ? null : string(node, profile.idProperty()),
                    node == null ? null : text(node),
                    new LinkedHashMap<>(properties), annotationModels, childDocuments);
        }

        private String text(Node node) {
            for (String key : profile.textProperties()) {
                String value = string(node, key);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
    }

    private final class MutableAnnotation {
        private final Node node;
        private final String element;
        private final Map<String, Object> properties;
        private final List<MutableAnnotation> children = new ArrayList<>();

        MutableAnnotation(Node node, ExportProfile profile) {
            this.node = node;
            this.element = elementOf(node, profile.dictionary());
            this.properties = mapProperties(node);
        }

        /**
         * The element an annotation maps to: the dictionary entry for its type, or - for
         * annotations that came from markup in the first place - the element name the
         * import kept.
         */
        private String elementOf(Node node, Dictionary dictionary) {
            String type = string(node, dictionary.typeProperty());
            String mapped = type == null ? null : dictionary.elementFor(type);
            return mapped != null ? mapped : string(node, dictionary.elementProperty());
        }

        MappedAnnotation toModel() {
            List<MappedAnnotation> childModels = new ArrayList<>();
            for (MutableAnnotation child : children) {
                childModels.add(child.toModel());
            }
            childModels.sort(Comparator.comparingLong(DocumentMapper::orderOf));
            return new MappedAnnotation(element, string(node, profile.idProperty()),
                    number(node, "startIndex"), number(node, "endIndex"),
                    new LinkedHashMap<>(properties), referencesOf(node), childModels);
        }
    }
}
