package atag.profile;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Resolution is what makes a profile one artifact: a call names it, and what the call
 * says itself still wins over what the profile says.
 */
class ProfilesTest {

    private static final Map<String, Object> PROFILE = Map.of(
            "name", "edition",
            "model", Map.of("content", List.of("Witness")),
            "referenceAttributes", List.of("corresp"),
            "import", Map.of("addUuid", false, "rootElement", "teiCorpus"),
            "export", Map.of("referenceAttribute", "corresp"));

    @Test
    void aCallWithoutAProfileIsLeftAsItIs() {
        Map<String, Object> config = Map.of("xpath", "//p");

        assertSame(config, Profiles.resolve(config, null, Profiles.IMPORT));
    }

    @Test
    void theSectionOfThisDirectionIsFlattenedIntoTheProfile() {
        Map<String, Object> resolved = Profiles.resolve(Map.of("profile", PROFILE), null, Profiles.IMPORT);

        assertEquals(false, resolved.get("addUuid"), "the import section applies");
        assertEquals("teiCorpus", resolved.get("rootElement"));
        assertEquals(List.of("corresp"), resolved.get("referenceAttributes"), "top-level keys apply too");
        assertEquals(null, resolved.get("referenceAttribute"), "the export section does not");
        assertEquals(null, resolved.get("import"), "sections are not keys of their own");
        assertEquals(null, resolved.get("profile"));
    }

    @Test
    void theOtherDirectionSeesItsOwnSection() {
        Map<String, Object> resolved = Profiles.resolve(Map.of("profile", PROFILE), null, Profiles.EXPORT);

        assertEquals("corresp", resolved.get("referenceAttribute"));
        assertEquals(null, resolved.get("addUuid"));
    }

    @Test
    void theCallOverridesTheProfile() {
        Map<String, Object> resolved = Profiles.resolve(
                Map.of("profile", PROFILE, "addUuid", true, "fileName", "out.xml"), null, Profiles.IMPORT);

        assertEquals(true, resolved.get("addUuid"));
        assertEquals("out.xml", resolved.get("fileName"));
        assertEquals("teiCorpus", resolved.get("rootElement"), "what the call does not mention stays");
    }

    @Test
    void aStoredProfileNeedsADatabaseToBeReadFrom() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Profiles.resolve(Map.of("profile", "edition"), null, Profiles.IMPORT));

        assertEquals("profile: 'edition' can only be used where a transaction is available",
                exception.getMessage());
    }

    @Test
    void aProfileSurvivesBeingStoredAsJson() {
        assertEquals(PROFILE, Profiles.fromJson(Profiles.toJson(PROFILE)));
    }
}
