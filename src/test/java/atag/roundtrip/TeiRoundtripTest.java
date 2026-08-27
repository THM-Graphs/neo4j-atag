package atag.roundtrip;

import atag.export.ExporterProcedures;
import atag.text.Importer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterators;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exports a text with its annotations as TEI and imports the result back into the same
 * graph. The roundtrip is semantic rather than lexical: what has to survive is the text,
 * the annotated ranges with their types, the annotation on an annotation and the
 * reference to the entity - not the exact serialization, which is precisely why the
 * overlapping annotation comes back from stand-off markup while the others come back
 * from inline elements.
 */
class TeiRoundtripTest {

    private static final Map<String, Object> PROFILE = Map.of("dictionary", Map.of(
            "elements", Map.of("persName", "person-reference", "s", "sentence")));

    /** each test roundtrips into a text of its own, since the database is shared by the class */
    private static final AtomicLong NEXT_ID = new AtomicLong();

    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withProcedure(ExporterProcedures.class)
            .withProcedure(Importer.class)
            .withConfig(GraphDatabaseSettings.procedure_unrestricted, List.of("atag.*"))
            .withDisabledServer()
            .withFixture("""
                CREATE (t:Text {uuid: 'text-1', text: 'Hildegard writes to Berthold.'})
                CREATE (hildegard:Entity:Person {uuid: 'hildegard', label: 'Hildegard von Bingen'})

                CREATE (a1:Annotation {uuid: 'a-1', type: 'person-reference', startIndex: 0, endIndex: 9})
                CREATE (t)-[:HAS_ANNOTATION]->(a1)
                CREATE (a1)-[:REFERS_TO]->(hildegard)
                CREATE (a2:Annotation {uuid: 'a-2', type: 'sentence', startIndex: 0, endIndex: 29})
                CREATE (t)-[:HAS_ANNOTATION]->(a2)
                CREATE (a3:Annotation {uuid: 'a-3', type: 'phrase', startIndex: 5, endIndex: 20})
                CREATE (t)-[:HAS_ANNOTATION]->(a3)
                CREATE (c1:Annotation {uuid: 'c-1', type: 'commentary', note: 'uncertain reading'})
                CREATE (a1)-[:HAS_ANNOTATION]->(c1)
                """)
            .build();

    private String roundtrip(GraphDatabaseService db) {
        String uuid = "reimported-" + NEXT_ID.incrementAndGet();
        String tei = db.executeTransactionally("""
                MATCH (t:Text {uuid: 'text-1'})
                CALL atag.export.tei.fromNode(t, $profile) YIELD value
                RETURN value
                """, Map.of("profile", PROFILE), r -> (String) Iterators.single(r).get("value"));

        db.executeTransactionally("CREATE (t:Text {uuid: $uuid, xml: $xml})", Map.of("uuid", uuid, "xml", tei));
        db.executeTransactionally("""
                MATCH (t:Text {uuid: $uuid})
                CALL atag.text.import.tei(t, 'xml', $profile) YIELD node
                RETURN count(node) AS count
                """, Map.of("uuid", uuid, "profile", PROFILE));
        return uuid;
    }

    @Test
    void theTextAndItsAnnotatedRangesSurvive(GraphDatabaseService db) {
        String uuid = roundtrip(db);

        String plainText = db.executeTransactionally(
                "MATCH (t:Text {uuid: $uuid}) RETURN t.plainText AS plainText",
                Map.of("uuid", uuid), r -> (String) Iterators.single(r).get("plainText"));
        assertEquals("Hildegard writes to Berthold.", plainText);

        List<String> spans = db.executeTransactionally("""
                MATCH (:Text {uuid: $uuid})-[:HAS_ANNOTATION]->(a:Annotation)
                RETURN a.type + '[' + a.startIndex + ',' + a.endIndex + ']' AS span
                ORDER BY a.startIndex, a.endIndex, a.type
                """, Map.of("uuid", uuid), r -> Iterators.asList(r).stream().map(row -> (String) row.get("span")).toList());

        assertEquals(List.of("person-reference[0,9]", "sentence[0,29]", "phrase[5,20]"), spans,
                "every annotated range should come back with its type, whether it was written inline or as stand-off");
    }

    @Test
    void theAnnotationOnAnAnnotationSurvives(GraphDatabaseService db) {
        String uuid = roundtrip(db);

        Map<String, Object> commentary = db.executeTransactionally("""
                MATCH (:Text {uuid: $uuid})-[:HAS_ANNOTATION]->(a:Annotation {type: 'person-reference'})
                MATCH (a)-[:HAS_ANNOTATION]->(c:Annotation)
                RETURN properties(c) AS properties
                """, Map.of("uuid", uuid), r -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) Iterators.single(r).get("properties");
            return properties;
        });

        assertEquals("commentary", commentary.get("type"));
        assertEquals("uncertain reading", commentary.get("note"), "its own properties should survive as well");
    }

    @Test
    void theEntityReferencePointsAtTheEntityThatIsAlreadyInTheGraph(GraphDatabaseService db) {
        String uuid = roundtrip(db);

        boolean sameEntity = db.executeTransactionally("""
                MATCH (:Text {uuid: 'text-1'})-[:HAS_ANNOTATION]->(:Annotation {uuid: 'a-1'})-[:REFERS_TO]->(original)
                MATCH (:Text {uuid: $uuid})-[:HAS_ANNOTATION]->(a:Annotation {uuid: 'a-1'})-[:REFERS_TO]->(reimported)
                RETURN original = reimported AS same
                """, Map.of("uuid", uuid), r -> (Boolean) Iterators.single(r).get("same"));

        assertTrue(sameEntity, "the reference should resolve to the existing entity instead of duplicating it");
    }
}
