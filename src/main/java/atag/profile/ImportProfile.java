package atag.profile;

import atag.model.ProjectModel;
import atag.model.Ramen.Concept;

import java.util.List;
import java.util.Map;

/**
 * The configuration that controls the four phases of an import: what is parsed and
 * validated (phase 1), which parts of the document are extracted (phase 2), how markup
 * names are translated into the project vocabulary (phase 3), and how the result is
 * written to the graph (phase 4).
 * <p>
 * A profile is what makes an import reproducible: the same document imported with two
 * different profiles legitimately yields two different graphs, and nothing outside the
 * profile decides how a document is interpreted.
 */
public class ImportProfile {

    private static final String DEFAULT_XPATH = "/TEI/text/body//node()";

    private final ProjectModel model;
    private final Dictionary dictionary;
    private final String xpath;
    private final String rootElement;
    private final String idAttribute;
    private final String idProperty;
    private final List<String> referenceAttributes;
    private final String annotationLabel;
    private final String plainTextProperty;
    private final String relationshipType;
    private final boolean addUuid;
    private final String entityKey;

    @SuppressWarnings("unchecked")
    private ImportProfile(Map<String, Object> config, String defaultAttributePrefix) {
        this.model = ProjectModel.from(config);
        this.dictionary = Dictionary.from(config, defaultAttributePrefix);
        this.xpath = (String) config.getOrDefault("xpath", DEFAULT_XPATH);
        this.rootElement = (String) config.getOrDefault("rootElement", "");
        this.idAttribute = (String) config.getOrDefault("idAttribute", "xml:id");
        this.idProperty = (String) config.getOrDefault("idProperty", "uuid");
        this.referenceAttributes = (List<String>) config.getOrDefault("referenceAttributes", List.of());
        this.annotationLabel = (String) config.getOrDefault("annotationLabel",
                model.primaryLabel(Concept.ANNOTATION).name());
        this.plainTextProperty = (String) config.getOrDefault("plainTextProperty", "plainText");
        this.relationshipType = (String) config.getOrDefault("relationshipType", model.hasAnnotation().name());
        this.addUuid = !Boolean.FALSE.equals(config.getOrDefault("addUuid", true));
        this.entityKey = (String) config.getOrDefault("entityKey", "uuid");
    }

    /** A profile for XML sources, where attribute names become property keys unchanged. */
    public static ImportProfile xml(Map<String, Object> config) {
        return new ImportProfile(config, "");
    }

    /** A profile for HTML sources, where attributes are kept apart by an {@code attribute:} prefix. */
    public static ImportProfile html(Map<String, Object> config) {
        return new ImportProfile(config, "attribute:");
    }

    public ProjectModel model() {
        return model;
    }

    public Dictionary dictionary() {
        return dictionary;
    }

    public String xpath() {
        return xpath;
    }

    /** Expected name of the document element, or empty when phase 1 should not check it. */
    public String rootElement() {
        return rootElement;
    }

    public String idAttribute() {
        return idAttribute;
    }

    /** Property an identifier found in the source is written to. */
    public String idProperty() {
        return idProperty;
    }

    /** Attributes whose value points at an entity, e.g. {@code ref="#hildegard"}. */
    public List<String> referenceAttributes() {
        return referenceAttributes;
    }

    public String annotationLabel() {
        return annotationLabel;
    }

    public String plainTextProperty() {
        return plainTextProperty;
    }

    public String relationshipType() {
        return relationshipType;
    }

    public boolean addUuid() {
        return addUuid;
    }

    /** Property an entity reference is resolved against. */
    public String entityKey() {
        return entityKey;
    }
}
