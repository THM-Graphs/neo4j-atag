package atag.profile;

import atag.model.ProjectModel;
import atag.model.Ramen.Concept;
import org.neo4j.graphdb.Transaction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The configuration that controls the four phases of an import: what is parsed and
 * validated (phase 1), which parts of the document are extracted (phase 2), how markup
 * names are translated into the project vocabulary (phase 3), and how the result is
 * written to the graph (phase 4).
 * <p>
 * A profile is what makes an import reproducible: the same TEI document imported with
 * two different profiles legitimately yields two different graphs, and nothing outside
 * the profile decides how a document is interpreted.
 */
public class ImportProfile {

    /**
     * Namespace-agnostic XPaths, so a profile works for TEI with and without a namespace
     * declaration. The structural containers {@code <div>} and {@code <ab>} are skipped:
     * they carry the collection and content nodes of the model, not annotations.
     */
    private static final String TEI_BODY = "/*[local-name()='TEI']/*[local-name()='text']/*[local-name()='body']"
            + "//node()[not(self::*[local-name()='div' or local-name()='ab'])]";
    private static final String TEI_STANDOFF_ANNOTATIONS =
            "/*[local-name()='TEI']/*[local-name()='standOff']//*[local-name()='annotation']";
    private static final String TEI_STANDOFF_ENTITIES =
            "/*[local-name()='TEI']/*[local-name()='standOff']//*[local-name()='list'][@type='entity']/*[local-name()='item']";

    private static final Map<String, Object> TEI_DEFAULTS = Map.of(
            "xpath", TEI_BODY,
            "standoffXPath", TEI_STANDOFF_ANNOTATIONS,
            "entityXPath", TEI_STANDOFF_ENTITIES,
            "rootElement", "TEI",
            "idAttribute", "xml:id",
            "referenceAttributes", List.of("ref"));

    private final ProjectModel model;
    private final Dictionary dictionary;
    private final String xpath;
    private final String standoffXPath;
    private final String entityXPath;
    private final String rootElement;
    private final String idAttribute;
    private final String idProperty;
    private final List<String> referenceAttributes;
    private final String annotationLabel;
    private final String plainTextProperty;
    private final String relationshipType;
    private final boolean addUuid;
    private final String entityKey;
    private final boolean createMissingEntities;

    @SuppressWarnings("unchecked")
    private ImportProfile(Map<String, Object> config, String defaultAttributePrefix, Transaction tx) {
        this.model = ProjectModel.from(config, tx);
        this.dictionary = Dictionary.from(config, defaultAttributePrefix);
        this.xpath = (String) config.getOrDefault("xpath", TEI_BODY);
        this.standoffXPath = (String) config.getOrDefault("standoffXPath", "");
        this.entityXPath = (String) config.getOrDefault("entityXPath", "");
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
        this.createMissingEntities = Boolean.TRUE.equals(config.get("createMissingEntities"));
    }

    /** A profile for XML sources, where attribute names become property keys unchanged. */
    public static ImportProfile xml(Map<String, Object> config, Transaction tx) {
        return new ImportProfile(config, "", tx);
    }

    /** A profile for HTML sources, where attributes are kept apart by an {@code attribute:} prefix. */
    public static ImportProfile html(Map<String, Object> config, Transaction tx) {
        return new ImportProfile(config, "attribute:", tx);
    }

    /**
     * An XML profile with TEI defaults layered underneath the given configuration:
     * the body is the text, {@code standOff} carries annotations and entities, and
     * {@code @ref} links an annotation to an entity.
     */
    public static ImportProfile tei(Map<String, Object> config, Transaction tx) {
        Map<String, Object> merged = new LinkedHashMap<>(TEI_DEFAULTS);
        merged.putAll(config);
        return new ImportProfile(merged, "", tx);
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

    public String standoffXPath() {
        return standoffXPath;
    }

    public String entityXPath() {
        return entityXPath;
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

    public boolean createMissingEntities() {
        return createMissingEntities;
    }
}
