package atag.profile;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The dictionary is what both directions of the pipeline share, so the property that
 * matters is that a name mapped on the way in comes back unchanged on the way out.
 */
class DictionaryTest {

    private static final Map<String, Object> CONFIG = Map.of("dictionary", Map.of(
            "elements", Map.of("persName", "person-reference"),
            "attributes", Map.of("ref", "reference")));

    @Test
    void mapsElementAndAttributeNamesInBothDirections() {
        Dictionary dictionary = Dictionary.from(CONFIG, "");

        assertEquals("person-reference", dictionary.typeFor("persName"));
        assertEquals("persName", dictionary.elementFor("person-reference"));
        assertEquals("reference", dictionary.propertyFor("ref"));
        assertEquals("ref", dictionary.attributeFor("reference"));
    }

    @Test
    void passesUnknownNamesThroughInsteadOfDroppingThem() {
        Dictionary dictionary = Dictionary.from(CONFIG, "");

        assertNull(dictionary.typeFor("hi"), "an element the dictionary does not know has no type");
        assertEquals("rendition", dictionary.propertyFor("rendition"));
        assertEquals("rendition", dictionary.attributeFor("rendition"));
    }

    @Test
    void keepsUnmappedAttributesApartByTheConfiguredPrefix() {
        Dictionary dictionary = Dictionary.from(CONFIG, "attribute:");

        assertEquals("attribute:class", dictionary.propertyFor("class"));
        assertEquals("class", dictionary.attributeFor("attribute:class"));
        assertEquals("reference", dictionary.propertyFor("ref"),
                "a mapped attribute is not prefixed, its name is a decision of the profile");
    }

    @Test
    void fallsBackToTheDefaultsWhenAProfileDeclaresNoDictionary() {
        Dictionary dictionary = Dictionary.from(Map.of(), "");

        assertEquals("tag", dictionary.elementProperty());
        assertEquals("type", dictionary.typeProperty());
        assertEquals("seg", dictionary.defaultElement());
        assertEquals("", dictionary.attributePrefix());
    }
}
