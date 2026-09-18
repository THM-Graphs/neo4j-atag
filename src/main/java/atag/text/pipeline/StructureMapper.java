package atag.text.pipeline;

import atag.model.Ramen.Concept;
import atag.profile.Dictionary;
import atag.profile.ImportProfile;
import atag.profile.StandoffVocabulary;
import atag.text.pipeline.MappedStructure.MappedAnnotation;
import atag.text.pipeline.MappedStructure.MappedEntity;
import atag.text.pipeline.MappedStructure.Reference;

import java.util.ArrayList;
import java.util.Arrays;
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
        return new MappedStructure(structure.plainText(), annotations, mapEntities(structure.entities(), profile),
                structure.header());
    }

    public List<MappedEntity> mapEntities(List<ExtractedEntity> declarations, ImportProfile profile) {
        List<MappedEntity> entities = new ArrayList<>();
        for (ExtractedEntity declaration : declarations) {
            entities.add(mapEntity(declaration, profile));
        }
        return entities;
    }

    private MappedAnnotation mapAnnotation(ExtractedElement element, ImportProfile profile) {
        Dictionary dictionary = profile.dictionary();
        Map<String, Object> properties = new LinkedHashMap<>();
        if (element.name() != null) {
            properties.put(dictionary.elementProperty(), element.name());
            String type = dictionary.typeFor(element.name());
            if (type != null) {
                properties.put(dictionary.typeProperty(), type);
            }
        }
        if (element.startIndex() != null) {
            properties.put("startIndex", element.startIndex());
            properties.put("endIndex", element.endIndex());
        }
        if (element.text() != null) {
            properties.put(profile.plainTextProperty(), element.text());
        }
        if (element.depth() != null) {
            properties.put("depth", element.depth());
        }

        String id = null;
        List<Reference> references = new ArrayList<>();
        for (Map.Entry<String, String> attribute : element.attributes().entrySet()) {
            String name = attribute.getKey();
            String value = attribute.getValue();
            if (name.equals(profile.idAttribute())) {
                id = value;
                properties.put(profile.idProperty(), value);
            } else if (profile.referenceAttributes().contains(name)) {
                for (String pointer : pointers(value)) {
                    references.add(new Reference(name, pointer));
                }
            } else {
                properties.put(dictionary.propertyFor(name), value);
            }
        }
        return new MappedAnnotation(id, element.parentId(), properties, references);
    }

    /**
     * An entity declaration carries the identifier references point at, the labels that
     * refine the generic {@code Entity} concept, and a display name. The element it was
     * written as is kept like an annotation's, and so is the declaration itself when the
     * profile asks for it.
     */
    private MappedEntity mapEntity(ExtractedEntity element, ImportProfile profile) {
        Dictionary dictionary = profile.dictionary();
        List<String> labels = new ArrayList<>(profile.model().labels(Concept.ENTITY));
        Map<String, Object> properties = new LinkedHashMap<>();
        String id = null;

        properties.put(dictionary.elementProperty(), element.name());
        if (element.label() != null) {
            properties.put("label", element.label());
        }
        if (element.source() != null) {
            properties.put(profile.entitySourceProperty(), element.source());
        }
        for (Map.Entry<String, String> attribute : element.attributes().entrySet()) {
            String name = attribute.getKey();
            String value = attribute.getValue();
            if (name.equals(profile.idAttribute())) {
                id = value;
                properties.put(profile.entityKey(), value);
            } else if (name.equals(StandoffVocabulary.TYPE_ATTRIBUTE)) {
                Arrays.stream(value.split(",")).map(String::trim).filter(label -> !label.isEmpty())
                        .filter(label -> !labels.contains(label))
                        .forEach(labels::add);
            } else if (name.equals(StandoffVocabulary.NAME_ATTRIBUTE)) {
                properties.putIfAbsent("label", value);
            } else {
                properties.put(dictionary.propertyFor(name), value);
            }
        }
        return new MappedEntity(id, labels, properties);
    }

    /** A reference attribute may hold several whitespace-separated pointers. */
    private List<String> pointers(String value) {
        List<String> result = new ArrayList<>();
        for (String pointer : value.trim().split("\\s+")) {
            if (!pointer.isEmpty()) {
                result.add(pointer);
            }
        }
        return result;
    }
}
