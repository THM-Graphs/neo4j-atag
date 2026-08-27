package atag.model;

import atag.model.Ramen.Concept;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A project-specific refinement of the {@link Ramen} meta-model: which labels a
 * concrete edition uses for each RAMEN concept and which relationship type names carry
 * the RAMEN relations. A correspondence project may refine {@code Content} as
 * {@code Text} and {@code Entity} as {@code Person} while still being traversable in
 * terms of the generic concepts.
 * <p>
 * Import and export profiles hold a project model, so both directions of the pipeline
 * agree on what counts as content, annotation or entity.
 */
public class ProjectModel {

    private static final Map<Concept, List<String>> DEFAULT_LABELS = Map.of(
            Concept.COLLECTION, List.of("Collection"),
            Concept.CONTENT, List.of("Text"),
            Concept.ENTITY, List.of("Entity"),
            Concept.ANNOTATION, List.of("Annotation"));

    private final Map<Concept, List<String>> labels;
    private final RelationshipType partOf;
    private final RelationshipType hasAnnotation;
    private final RelationshipType refersTo;

    public ProjectModel(Map<Concept, List<String>> labels,
                        String partOf, String hasAnnotation, String refersTo) {
        Map<Concept, List<String>> merged = new LinkedHashMap<>(DEFAULT_LABELS);
        merged.putAll(labels);
        this.labels = merged;
        this.partOf = RelationshipType.withName(partOf);
        this.hasAnnotation = RelationshipType.withName(hasAnnotation);
        this.refersTo = RelationshipType.withName(refersTo);
    }

    public static ProjectModel defaults() {
        return new ProjectModel(Map.of(), Ramen.PART_OF.name(), Ramen.HAS_ANNOTATION.name(), Ramen.REFERS_TO.name());
    }

    /**
     * Read a project model from the {@code model} entry of a profile configuration, e.g.
     * {@code {model: {content: ['Text'], entity: ['Person', 'Place'], hasAnnotation: 'HAS_ANNOTATION'}}}.
     */
    @SuppressWarnings("unchecked")
    public static ProjectModel from(Map<String, Object> config) {
        Object raw = config.get("model");
        if (!(raw instanceof Map)) {
            return defaults();
        }
        Map<String, Object> model = (Map<String, Object>) raw;
        Map<Concept, List<String>> labels = new LinkedHashMap<>();
        for (Concept concept : Concept.values()) {
            List<String> configured = labelsOf(model.get(concept.name().toLowerCase(Locale.ROOT)));
            if (configured != null) {
                labels.put(concept, configured);
            }
        }
        return new ProjectModel(labels,
                (String) model.getOrDefault("partOf", Ramen.PART_OF.name()),
                (String) model.getOrDefault("hasAnnotation", Ramen.HAS_ANNOTATION.name()),
                (String) model.getOrDefault("refersTo", Ramen.REFERS_TO.name()));
    }

    @SuppressWarnings("unchecked")
    private static List<String> labelsOf(Object value) {
        if (value instanceof String single) {
            return List.of(single);
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>((List<String>) list);
        }
        return null;
    }

    public List<String> labels(Concept concept) {
        return labels.get(concept);
    }

    public Label primaryLabel(Concept concept) {
        return Label.label(labels.get(concept).get(0));
    }

    public boolean is(Node node, Concept concept) {
        return labels.get(concept).stream().anyMatch(label -> node.hasLabel(Label.label(label)));
    }

    /**
     * The RAMEN concept a node belongs to, or {@code null} if it is outside the model.
     * {@code ANNOTATION} is checked first, since an annotation may well carry additional
     * labels that also appear in another concept's label list.
     */
    public Concept conceptOf(Node node) {
        for (Concept concept : List.of(Concept.ANNOTATION, Concept.CONTENT, Concept.COLLECTION, Concept.ENTITY)) {
            if (is(node, concept)) {
                return concept;
            }
        }
        return null;
    }

    public RelationshipType partOf() {
        return partOf;
    }

    public RelationshipType hasAnnotation() {
        return hasAnnotation;
    }

    public RelationshipType refersTo() {
        return refersTo;
    }

    public Map<Concept, List<String>> allLabels() {
        return labels;
    }
}
