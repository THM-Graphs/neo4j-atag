package atag.text.pipeline;

import atag.profile.Dictionary;
import atag.profile.ImportProfile;
import atag.text.pipeline.MappedStructure.MappedAnnotation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 3 of the import pipeline: translate the extracted structures with the profile's
 * {@link Dictionary}. An element name becomes an annotation type, attribute names become
 * property keys, and the attributes the profile declares as references become entity
 * references rather than properties - because a reference is a relation in the graph, not
 * a string on a node.
 * <p>
 * Names the dictionary does not know are not dropped: the element name is kept as it was
 * written, so an import never loses information that a later, richer profile could
 * interpret.
 */
public class StructureMapper {

    public MappedStructure map(ExtractedStructure structure, ImportProfile profile) {
        List<MappedAnnotation> annotations = new ArrayList<>();
        for (ExtractedElement element : structure.elements()) {
            annotations.add(mapAnnotation(element, profile));
        }
        return new MappedStructure(structure.plainText(), annotations);
    }

    private MappedAnnotation mapAnnotation(ExtractedElement element, ImportProfile profile) {
        Dictionary dictionary = profile.dictionary();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(dictionary.elementProperty(), element.name());

        String type = dictionary.typeFor(element.name());
        if (type != null) {
            properties.put(dictionary.typeProperty(), type);
        }
        properties.put("startIndex", element.startIndex());
        properties.put("endIndex", element.endIndex());
        if (element.text() != null) {
            properties.put(profile.plainTextProperty(), element.text());
        }

        List<String> references = new ArrayList<>();
        for (Map.Entry<String, String> attribute : element.attributes().entrySet()) {
            String name = attribute.getKey();
            String value = attribute.getValue();
            if (name.equals(profile.idAttribute())) {
                properties.put(profile.idProperty(), value);
            } else if (profile.referenceAttributes().contains(name)) {
                references.addAll(pointers(value));
            } else {
                properties.put(dictionary.propertyFor(name), value);
            }
        }
        return new MappedAnnotation(properties, references);
    }

    /** A reference attribute may hold several whitespace-separated pointers. */
    private List<String> pointers(String value) {
        List<String> result = new ArrayList<>();
        for (String pointer : value.trim().split("\\s+")) {
            if (!pointer.isEmpty()) {
                result.add(pointer.startsWith("#") ? pointer.substring(1) : pointer);
            }
        }
        return result;
    }
}
