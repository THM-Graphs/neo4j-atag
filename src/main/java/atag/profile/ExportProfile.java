package atag.profile;

import atag.model.ProjectModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The configuration that controls both phases of an export: which part of the graph is
 * traversed (phase 1) and how the traversed graph is mapped onto a serialization
 * vocabulary (phase 2).
 * <p>
 * The same subgraph can be rendered as JGF or as stand-off JSON/XML; the profile - not
 * the procedure - decides the scope of the export and the vocabulary it is mapped into.
 */
public class ExportProfile {

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
    private final String entityKey;
    private final String idProperty;
    private final String fileName;

    @SuppressWarnings("unchecked")
    private ExportProfile(Map<String, Object> config) {
        this.model = ProjectModel.from(config);
        this.dictionary = Dictionary.from(config, "");
        this.followIncoming = (List<String>) config.getOrDefault("followIncoming", DEFAULT_INCOMING);
        this.followOutgoing = outgoing(config);
        this.annotationTypes = (List<String>) config.get("annotationTypes");
        this.textProperties = (List<String>) config.getOrDefault("textProperties", DEFAULT_TEXT_PROPERTIES);
        this.ignoreProperties = (List<String>) config.getOrDefault("ignoreProperties", List.of());
        this.entityKey = (String) config.getOrDefault("entityKey", "uuid");
        this.idProperty = (String) config.getOrDefault("idProperty", "uuid");
        this.fileName = (String) config.get("fileName");
    }

    public static ExportProfile from(Map<String, Object> config) {
        return new ExportProfile(config);
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

    /** File in the import directory to write to, or {@code null} to return the result to Cypher. */
    public String fileName() {
        return fileName;
    }
}
