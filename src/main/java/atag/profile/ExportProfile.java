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

    /**
     * An element whose annotation is written as one reading of a {@code <choice>}, and the
     * property holding the other reading, e.g. {@code sic} and its {@code corrected} form.
     *
     * @param alternative element the other reading is written as, e.g. {@code corr}
     * @param property    property of the annotation holding that reading
     */
    public record Choice(String alternative, String property) {
    }

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
    private final List<String> milestones;
    private final boolean standoffNotes;
    private final boolean omitBlankProperties;
    private final Map<String, Choice> choices;
    private final Map<String, String> entityElements;
    private final Map<String, String> entityIdentifiers;
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
        this.milestones = (List<String>) config.getOrDefault("milestones", List.of());
        this.choices = choices(config.get("choices"));
        this.omitBlankProperties = Boolean.TRUE.equals(config.get("omitBlankProperties"));
        this.standoffNotes = Boolean.TRUE.equals(config.get("standoffNotes"));
        this.entityElements = stringMap(config.get("entityElements"));
        this.entityIdentifiers = stringMap(config.get("entityIdentifiers"));
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
    private static Map<String, Choice> choices(Object value) {
        Map<String, Choice> result = new java.util.LinkedHashMap<>();
        if (value instanceof Map<?, ?> configured) {
            configured.forEach((element, definition) -> {
                Map<String, String> map = (Map<String, String>) definition;
                result.put((String) element, new Choice(map.get("alternative"), map.get("property")));
            });
        }
        return Map.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringMap(Object value) {
        return value instanceof Map ? Map.copyOf((Map<String, String>) value) : Map.of();
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

    /**
     * Element names written as empty milestones at the start of their range, e.g. {@code lb}:
     * the text they mark stays flat instead of being wrapped by them.
     */
    public List<String> milestones() {
        return milestones;
    }

    /** Whether a property whose value is empty or blank is left out instead of written as an empty attribute. */
    public boolean omitBlankProperties() {
        return omitBlankProperties;
    }

    /**
     * Elements written together with an alternative reading as {@code <choice>}, by element
     * name, e.g. {@code {sic: {alternative: 'corr', property: 'corrected'}}}.
     */
    public Map<String, Choice> choices() {
        return choices;
    }

    /**
     * Whether a stand-off {@code <annotation>} carries its type as a child {@code <note type>}
     * and its references as child {@code <ptr target>} instead of as attributes, which TEI
     * does not allow on {@code <annotation>}.
     */
    public boolean standoffNotes() {
        return standoffNotes;
    }

    /**
     * The TEI element an entity is declared as, by entity label, e.g. {@code Person} to
     * {@code person}. Entities that match, and have no verbatim declaration, are declared
     * with their name and identifiers instead of as a generic {@code <item>}.
     */
    public Map<String, String> entityElements() {
        return entityElements;
    }

    /** Entity properties written as {@code <idno>}, by property key, to the {@code @type} of the idno. */
    public Map<String, String> entityIdentifiers() {
        return entityIdentifiers;
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
