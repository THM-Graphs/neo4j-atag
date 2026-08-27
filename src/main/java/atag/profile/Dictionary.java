package atag.profile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The dictionary of a mapping profile: the translation table between a markup
 * vocabulary (element and attribute names) and the graph vocabulary (annotation types
 * and property keys). It is deliberately bidirectional - an import maps
 * {@code persName -> person reference}, an export maps the same relation back - so a
 * single dictionary describes both directions of a project's conventions.
 * <p>
 * Names that the dictionary does not know are passed through: an unmapped element name
 * is kept verbatim in the {@link #elementProperty()}, an unmapped attribute becomes a
 * property with the configured {@link #attributePrefix()}.
 */
public class Dictionary {

    private final Map<String, String> elementToType;
    private final Map<String, String> typeToElement;
    private final Map<String, String> attributeToProperty;
    private final Map<String, String> propertyToAttribute;
    private final String attributePrefix;
    private final String elementProperty;
    private final String typeProperty;
    private final String defaultElement;

    private Dictionary(Map<String, String> elements, Map<String, String> attributes,
                       String attributePrefix, String elementProperty, String typeProperty,
                       String defaultElement) {
        this.elementToType = elements;
        this.typeToElement = inverse(elements);
        this.attributeToProperty = attributes;
        this.propertyToAttribute = inverse(attributes);
        this.attributePrefix = attributePrefix;
        this.elementProperty = elementProperty;
        this.typeProperty = typeProperty;
        this.defaultElement = defaultElement;
    }

    /**
     * Read the {@code dictionary} entry of a profile configuration. The default
     * attribute prefix differs per source format, which is why it is passed in rather
     * than hard-coded.
     */
    @SuppressWarnings("unchecked")
    public static Dictionary from(Map<String, Object> config, String defaultAttributePrefix) {
        Map<String, Object> dictionary = config.get("dictionary") instanceof Map
                ? (Map<String, Object>) config.get("dictionary")
                : Map.of();
        return new Dictionary(
                stringMap(dictionary.get("elements")),
                stringMap(dictionary.get("attributes")),
                (String) dictionary.getOrDefault("attributePrefix", defaultAttributePrefix),
                (String) dictionary.getOrDefault("elementProperty", "tag"),
                (String) dictionary.getOrDefault("typeProperty", "type"),
                (String) dictionary.getOrDefault("defaultElement", "seg"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringMap(Object value) {
        return value instanceof Map
                ? new LinkedHashMap<>((Map<String, String>) value)
                : new LinkedHashMap<>();
    }

    private static Map<String, String> inverse(Map<String, String> source) {
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.putIfAbsent(value, key));
        return result;
    }

    /**
     * The annotation type an element name stands for, or {@code null} if the dictionary
     * has no entry - in that case the element name itself is the only type information.
     */
    public String typeFor(String elementName) {
        return elementToType.get(elementName);
    }

    public String elementFor(String type) {
        return typeToElement.get(type);
    }

    public String propertyFor(String attributeName) {
        String mapped = attributeToProperty.get(attributeName);
        return mapped != null ? mapped : attributePrefix + attributeName;
    }

    public String attributeFor(String propertyKey) {
        String mapped = propertyToAttribute.get(propertyKey);
        if (mapped != null) {
            return mapped;
        }
        return !attributePrefix.isEmpty() && propertyKey.startsWith(attributePrefix)
                ? propertyKey.substring(attributePrefix.length())
                : propertyKey;
    }

    public String attributePrefix() {
        return attributePrefix;
    }

    public String elementProperty() {
        return elementProperty;
    }

    public String typeProperty() {
        return typeProperty;
    }

    public String defaultElement() {
        return defaultElement;
    }
}
