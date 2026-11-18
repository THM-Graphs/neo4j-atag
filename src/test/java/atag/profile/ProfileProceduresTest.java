package atag.profile;

import atag.model.ModelProcedures;
import atag.text.Importer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterators;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileProceduresTest {

    private static final String TEI = """
            <TEI xmlns="http://www.tei-c.org/ns/1.0"><teiHeader/>
            <text><body><ab xml:id="t-1"><rs corresp="#anna">Anna</rs></ab></body></text>
            <standOff><list type="entity"><item xml:id="anna" n="Anna" type="Person"/></list></standOff></TEI>
            """;

    private static final Map<String, Object> PROFILE = Map.of(
            "name", "edition",
            "model", Map.of("content", List.of("Witness"), "entity", List.of("Entity")),
            "import", Map.of("referenceAttributes", List.of("corresp"), "createMissingEntities", true),
            "export", Map.of("referenceAttribute", "corresp", "ignoreProperties", List.of("xml")));

    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withDisabledServer()
            .withProcedure(ProfileProcedures.class)
            .withFunction(ProfileProcedures.class)
            .withProcedure(ModelProcedures.class)
            .withProcedure(Importer.class)
            .build();

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(GraphDatabaseService db, String query, Map<String, Object> parameters) {
        return db.executeTransactionally(query, parameters,
                r -> (Map<String, Object>) Iterators.single(r).get("value"));
    }

    @Test
    void aProfileIsStoredUnderItsNameAndReadBack(GraphDatabaseService db) {
        Map<String, Object> written = call(db,
                "CALL atag.profile.write($profile) YIELD value RETURN value", Map.of("profile", PROFILE));
        assertEquals("edition", written.get("name"));
        assertEquals(List.of("export", "import", "model"), written.get("sections"));

        Map<String, Object> read = call(db,
                "CALL atag.profile.read('edition') YIELD value RETURN value", Map.of());
        assertEquals(PROFILE, read);

        call(db, "CALL atag.profile.write($profile) YIELD value RETURN value", Map.of("profile", PROFILE));
        assertEquals(1L, (long) db.executeTransactionally(
                "MATCH (p:Meta:Profile {name: 'edition'}) RETURN count(p) AS count", Map.of(),
                r -> Iterators.single(r).get("count")), "writing the same profile twice must not duplicate it");
    }

    @Test
    void theModelOfAProfileReachesTheMetaGraph(GraphDatabaseService db) {
        call(db, "CALL atag.profile.write($profile) YIELD value RETURN value", Map.of("profile", PROFILE));

        Map<String, Object> model = call(db, "CALL atag.model.meta.read() YIELD value RETURN value", Map.of());
        assertEquals(List.of("Witness"), model.get("content"));
    }

    @Test
    void aStoredProfileDrivesAnImport(GraphDatabaseService db) {
        call(db, "CALL atag.profile.write($profile) YIELD value RETURN value", Map.of("profile", PROFILE));
        db.executeTransactionally("CREATE (w:Witness {uuid: 'w-1', xml: $xml})", Map.of("xml", TEI));

        long annotations = db.executeTransactionally("""
                MATCH (w:Witness {uuid: 'w-1'})
                CALL atag.text.import.tei(w, 'xml', {profile: 'edition'}) YIELD node
                RETURN count(node) AS count
                """, Map.of(), r -> (Long) Iterators.single(r).get("count"));
        assertEquals(1, annotations);

        assertEquals(1L, (long) db.executeTransactionally("""
                MATCH (:Witness {uuid: 'w-1'})-[:HAS_ANNOTATION]->(:Annotation)-[:REFERS_TO]->(e:Entity {uuid: 'anna'})
                RETURN count(e) AS count
                """, Map.of(), r -> Iterators.single(r).get("count")),
                "the profile's import section named corresp as a reference attribute");
    }

    @Test
    void aProfileKeptAsJsonCanBeParsedAndStored(GraphDatabaseService db) {
        String json = """
                {"name": "from-file", "model": {"content": ["Witness"]}, "import": {"addUuid": false}}""";

        Map<String, Object> read = call(db, """
                CALL atag.profile.write(atag.profile.parse($json)) YIELD value
                WITH value.name AS name
                CALL atag.profile.read(name) YIELD value
                RETURN value
                """, Map.of("json", json));

        assertEquals("from-file", read.get("name"));
        assertEquals(Map.of("addUuid", false), read.get("import"));
    }

    @Test
    void aProfileThatWasNeverWrittenIsAnError(GraphDatabaseService db) {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> db.executeTransactionally(
                "CALL atag.profile.read('nothing') YIELD value RETURN value", Map.of(), Iterators::single));

        assertTrue(exception.getMessage().contains("no profile named 'nothing' has been written"),
                exception.getMessage());
    }

    @Test
    void aProfileWithoutANameCannotBeStored(GraphDatabaseService db) {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> db.executeTransactionally(
                "CALL atag.profile.write({model: {content: ['Witness']}}) YIELD value RETURN value",
                Map.of(), Iterators::single));

        assertTrue(exception.getMessage().contains("a profile needs a name"), exception.getMessage());
    }
}
