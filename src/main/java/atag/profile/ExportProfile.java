package atag.profile;

import atag.model.ProjectModel;
import org.neo4j.graphdb.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The configuration that controls both phases of an export: which part of the graph is
 * traversed (phase 1) and how the traversed graph is mapped onto a serialization
 * vocabulary (phase 2).
 * <p>
 * The same subgraph can be rendered as JGF, as generic stand-off JSON/XML or as TEI;
 * the profile - not the procedure - decides the scope, the vocabulary and, for TEI,
 * whether annotations are serialized inline or as stand-off markup.
 */
public class ExportProfile {

    public enum Serialization {
        INLINE,
        STANDOFF
    }

    private static final List<String> DEFAULT_INCOMING = List.of("PART_OF");
    private static final List<String> DEFAULT_OUTGOING = List.of("HAS_ANNOTATION", "NEXT_TOKEN", "REFERS_TO");
    private static final List<String> CHARACTER_CHAIN_OUTGOING = List.of(
            "NEXT_CHARACTER", "TOKEN_START", "TOKEN_END",
            "STANDOFF_START", "STANDOFF_END", "CHARACTER_HAS_ANNOTATION");
    private static final List<String> DEFAULT_TEXT_PROPERTIES = List.of("text", "plainText");

    private final ProjectModel model;
    private final Dictionary dictionary;
    private final List<String> followIncoming;
    private final List<String> followOutgoing;
    private final List<String> annotationTypes;
    private final List<String> textProperties;
    private final List<String> ignoreProperties;
    private final Serialization serialization;
    private final String entityKey;
    private final String idProperty;
    private final String referenceAttribute;
    private final String headerProperty;
    private final String entitySourceProperty;
    private final String fileName;

    @SuppressWarnings("unchecked")
    private ExportProfile(Map<String, Object> config, Transaction tx) {
        this.model = ProjectModel.from(config, tx);
        this.dictionary = Dictionary.from(config, "");
        this.followIncoming = (List<String>) config.getOrDefault("followIncoming", DEFAULT_INCOMING);
        this.followOutgoing = outgoing(config);
        this.annotationTypes = (List<String>) config.get("annotationTypes");
        this.textProperties = (List<String>) config.getOrDefault("textProperties", DEFAULT_TEXT_PROPERTIES);
        this.ignoreProperties = (List<String>) config.getOrDefault("ignoreProperties", List.of());
        this.serialization = Serialization.valueOf(
                ((String) config.getOrDefault("serialization", "inline")).toUpperCase(Locale.ROOT));
        this.entityKey = (String) config.getOrDefault("entityKey", "uuid");
        this.idProperty = (String) config.getOrDefault("idProperty", "uuid");
        this.referenceAttribute = (String) config.getOrDefault("referenceAttribute", "ref");
        this.headerProperty = (String) config.getOrDefault("headerProperty", "teiHeader");
        this.entitySourceProperty = (String) config.get("entitySourceProperty");
        this.fileName = (String) config.get("fileName");
    }

    public static ExportProfile from(Map<String, Object> config) {
        return new ExportProfile(Profiles.resolve(config, null, Profiles.EXPORT), null);
    }

    /** A profile that may take its project model, or all of itself, from the database. */
    public static ExportProfile from(Map<String, Object> config, Transaction tx) {
        return new ExportProfile(Profiles.resolve(config, tx, Profiles.EXPORT), tx);
    }

    @SuppressWarnings("unchecked")
    private static List<String> outgoing(Map<String, Object> config) {
        List<String> configured = new ArrayList<>(
                (List<String>) config.getOrDefault("followOutgoing", DEFAULT_OUTGOING));
        boolean includeCharacterChain = !Boolean.FALSE.equals(config.get("includeCharacterChain"));
        if (includeCharacterChain && !config.containsKey("followOutgoing")) {
            configured.addAll(CHARACTER_CHAIN_OUTGOING);
        }
        return configured;
    }

    public ProjectModel model() {
        return model;
    }

    public Dictionary dictionary() {
        return dictionary;
    }

    public List<String> followIncoming() {
        return followIncoming;
    }

    public List<String> followOutgoing() {
        return followOutgoing;
    }

    /** Allow-list of annotation types, or {@code null} when every annotation is in scope. */
    public List<String> annotationTypes() {
        return annotationTypes;
    }

    /** Property keys holding the character content of a content node, in order of preference. */
    public List<String> textProperties() {
        return textProperties;
    }

    /** Property holding the identifier a node is addressed by in the serialization. */
    public String idProperty() {
        return idProperty;
    }

    /** Property holding the identifier an entity reference points at. */
    public String entityKey() {
        return entityKey;
    }

    /** Properties that should not be serialized at all, e.g. the source markup a node keeps. */
    public List<String> ignoreProperties() {
        return ignoreProperties;
    }

    public Serialization serialization() {
        return serialization;
    }

    /** File in the import directory to write to, or {@code null} to return the result to Cypher. */
    /** Attribute an entity reference is written as in TEI, e.g. {@code ref} or {@code corresp}. */
    public String referenceAttribute() {
        return referenceAttribute;
    }

    /** Property holding a verbatim TEI header; an anchor that has one is written as a {@code <TEI>}. */
    public String headerProperty() {
        return headerProperty;
    }

    /** Property holding an entity's verbatim declaration, or {@code null} to declare entities from their properties. */
    public String entitySourceProperty() {
        return entitySourceProperty;
    }

    public String fileName() {
        return fileName;
    }
}
