package atag.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterators;
import org.xmlunit.assertj3.XmlAssert;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same annotations expressed in the two TEI encodings the profile has to reconcile:
 * an inline {@code <persName>} and a stand-off annotation pointing at a character range,
 * plus an annotation on an annotation and an entity declaration.
 */
class TeiImportTest {

    private static final String TEI = """
            <TEI xmlns="http://www.tei-c.org/ns/1.0">
              <teiHeader><fileDesc><titleStmt><title>Letter R86</title></titleStmt></fileDesc></teiHeader>
              <text><body><ab xml:id="text-1"><persName xml:id="a-1" ref="#hildegard">Hildegard</persName> writes to Berthold.</ab></body></text>
              <standOff>
                <listAnnotation>
                  <annotation target="#string-range(text-1,5,15)" xml:id="a-3" type="phrase"/>
                  <annotation target="#a-1" xml:id="c-1" type="commentary" note="uncertain reading"/>
                </listAnnotation>
                <list type="entity">
                  <item xml:id="hildegard" n="Hildegard von Bingen" type="Person" wikidataId="Q70991"/>
                </list>
              </standOff>
            </TEI>
            """;

    private static final Map<String, Object> PROFILE = Map.of(
            "dictionary", Map.of("elements", Map.of("persName", "person-reference")),
            "createMissingEntities", true);

    /** each test imports into a text of its own, since the database is shared by the class */
    private static final AtomicLong NEXT_ID = new AtomicLong();

    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withDisabledServer()
            .withProcedure(Importer.class)
            .build();

    private long importTei(GraphDatabaseService db, String source, Map<String, Object> profile) {
        long id = NEXT_ID.incrementAndGet();
        db.executeTransactionally("CREATE (t:Text {id: $id, xml: $xml})", Map.of("id", id, "xml", source));
        db.executeTransactionally("""
                MATCH (t:Text {id: $id})
                CALL atag.text.import.tei(t, 'xml', $profile) YIELD node
                RETURN count(node) AS count
                """, Map.of("id", id, "profile", profile));
        return id;
    }

    @Test
    void extractsThePlainTextTheAnnotationsReferTo(GraphDatabaseService db) {
        long id = importTei(db, TEI, PROFILE);

        String plainText = db.executeTransactionally("MATCH (t:Text {id: $id}) RETURN t.plainText AS plainText",
                Map.of("id", id), r -> (String) Iterators.single(r).get("plainText"));
        assertEquals("Hildegard writes to Berthold.", plainText);
    }

    @Test
    @SuppressWarnings("unchecked")
    void inlineAndStandoffAnnotationsBecomeTheSameKindOfNode(GraphDatabaseService db) {
        long id = importTei(db, TEI, PROFILE);

        List<Map<String, Object>> annotations = db.executeTransactionally("""
                MATCH (t:Text {id: $id})-[:HAS_ANNOTATION]->(a:Annotation)
                RETURN properties(a) AS properties ORDER BY a.startIndex
                """, Map.of("id", id),
                r -> Iterators.asList(r).stream().map(row -> (Map<String, Object>) row.get("properties")).toList());

        assertEquals(2, annotations.size(), "the commentary belongs to its annotation, not to the text");

        Map<String, Object> inline = annotations.get(0);
        assertEquals("person-reference", inline.get("type"), "the dictionary should map the element name to a type");
        assertEquals("persName", inline.get("tag"), "the element name itself should be kept");
        assertEquals(0L, inline.get("startIndex"));
        assertEquals(9L, inline.get("endIndex"));
        assertEquals(4L, inline.get("depth"), "TEI/text/body/ab/persName: an inline element records how deep it was nested");

        Map<String, Object> standoff = annotations.get(1);
        assertEquals("phrase", standoff.get("type"));
        assertNull(standoff.get("tag"),
                "the generic <annotation> element of the stand-off vocabulary is not a markup name to keep");
        assertEquals(5L, standoff.get("startIndex"), "a string-range pointer should resolve to a character offset");
        assertEquals(20L, standoff.get("endIndex"));
        assertNull(standoff.get("depth"), "stand-off markup has no place in the hierarchy");
    }

    @Test
    void theHeaderIsKeptVerbatimOnTheContentNode(GraphDatabaseService db) {
        long id = importTei(db, TEI, PROFILE);

        String header = db.executeTransactionally("MATCH (t:Text {id: $id}) RETURN t.teiHeader AS header",
                Map.of("id", id), r -> (String) Iterators.single(r).get("header"));
        XmlAssert.assertThat(header).and("""
                <teiHeader xmlns="http://www.tei-c.org/ns/1.0"><fileDesc><titleStmt><title>Letter R86</title></titleStmt></fileDesc></teiHeader>""")
                .areIdentical();
    }

    @Test
    void anUnresolvableReferenceStaysAProperty(GraphDatabaseService db) {
        long id = importTei(db, """
                <TEI xmlns="http://www.tei-c.org/ns/1.0"><teiHeader/>
                <text><body><ab><rs corresp="nobody #hildegard letter-7">Somebody</rs></ab></body></text></TEI>
                """, Map.of("referenceAttributes", List.of("corresp")));

        Map<String, Object> row = db.executeTransactionally("""
                MATCH (t:Text {id: $id})-[:HAS_ANNOTATION]->(a:Annotation)
                RETURN a.corresp AS corresp, count { (a)-[:REFERS_TO]->() } AS references
                """, Map.of("id", id), Iterators::single);
        assertEquals("nobody letter-7", row.get("corresp"),
                "the pointers nothing in the graph answers to stay in the attribute they were written in");
        assertEquals(1L, row.get("references"), "hildegard exists from the earlier imports");
    }

    private static final String CORPUS = """
            <teiCorpus xmlns="http://www.tei-c.org/ns/1.0">
              <teiHeader/>
              <standOff>
                <listPerson>
                  <person xml:id="boulliau"><persName type="reg"><surname>Boulliau</surname> <forename>Ismaël</forename></persName><birth>1605</birth></person>
                </listPerson>
              </standOff>
            </teiCorpus>
            """;

    private static final Map<String, Object> REGISTER_PROFILE = Map.of(
            "rootElement", "teiCorpus",
            "entityXPath", "//*[local-name()='listPerson']/*[local-name()='person']",
            "entityLabelXPath", "normalize-space(*[local-name()='persName'][@type='reg'])",
            "entitySourceProperty", "tei",
            "model", Map.of("entity", List.of("Entity", "Person")));

    @Test
    void aRegisterIsImportedOnItsOwnAndKeptVerbatim(GraphDatabaseService db) {
        db.executeTransactionally("CREATE (c:Corpus {id: 'register', xml: $xml})", Map.of("xml", CORPUS));

        for (int run = 0; run < 2; run++) {
            long imported = db.executeTransactionally("""
                    MATCH (c:Corpus {id: 'register'})
                    CALL atag.text.import.entities(c, 'xml', $profile) YIELD node
                    RETURN count(node) AS count
                    """, Map.of("profile", REGISTER_PROFILE), r -> (Long) Iterators.single(r).get("count"));
            assertEquals(1, imported);
        }

        List<Map<String, Object>> entities = db.executeTransactionally("""
                MATCH (e:Person {uuid: 'boulliau'}) RETURN properties(e) AS properties, labels(e) AS labels
                """, Map.of(), r -> Iterators.asList(r).stream().map(row -> (Map<String, Object>) row).toList());
        assertEquals(1, entities.size(), "a second import must reuse the entity");
        Map<String, Object> properties = (Map<String, Object>) entities.get(0).get("properties");
        assertEquals("person", properties.get("tag"));
        assertEquals("Boulliau Ismaël", properties.get("label"));
        XmlAssert.assertThat(properties.get("tei").toString()).and("""
                <person xmlns="http://www.tei-c.org/ns/1.0" xml:id="boulliau"><persName type="reg"><surname>Boulliau</surname> <forename>Ismaël</forename></persName><birth>1605</birth></person>""")
                .areIdentical();
        assertNull(db.executeTransactionally("MATCH (c:Corpus {id: 'register'}) RETURN c.plainText AS text",
                Map.of(), r -> Iterators.single(r).get("text")), "a register import writes nothing to the start node");
    }

    @Test
    void annotationsOnAnnotationsAreAttachedToTheirAnnotation(GraphDatabaseService db) {
        long id = importTei(db, TEI, PROFILE);

        long count = db.executeTransactionally("""
                MATCH (:Text {id: $id})-[:HAS_ANNOTATION]->(a:Annotation {uuid: 'a-1'})
                MATCH (a)-[:HAS_ANNOTATION]->(c:Annotation {uuid: 'c-1'})
                RETURN count(c) AS count
                """, Map.of("id", id), r -> (Long) Iterators.single(r).get("count"));
        assertEquals(1, count, "the commentary should hang off the annotation it targets");
    }

    @Test
    void entityReferencesBecomeRelationships(GraphDatabaseService db) {
        long id = importTei(db, TEI, PROFILE);

        List<String> labels = db.executeTransactionally("""
                MATCH (:Text {id: $id})-[:HAS_ANNOTATION]->(a:Annotation {uuid: 'a-1'})-[:REFERS_TO]->(e)
                RETURN labels(e) AS labels, e.label AS label, e.wikidataId AS wikidataId
                """, Map.of("id", id), r -> {
            Map<String, Object> row = Iterators.single(r);
            assertEquals("Hildegard von Bingen", row.get("label"));
            assertEquals("Q70991", row.get("wikidataId"));
            return ((List<?>) row.get("labels")).stream().map(Object::toString).toList();
        });
        assertTrue(labels.containsAll(List.of("Entity", "Person")),
                "an entity declaration refines the generic Entity concept, here as a Person");
    }

    @Test
    void aSecondImportReusesTheEntityItReferencesInsteadOfCreatingAnother(GraphDatabaseService db) {
        importTei(db, TEI, PROFILE);
        importTei(db, TEI, PROFILE);

        long count = db.executeTransactionally("""
                MATCH (e:Entity {uuid: 'hildegard'}) RETURN count(e) AS count
                """, Map.of(), r -> (Long) Iterators.single(r).get("count"));
        assertEquals(1, count, "an entity that is already in the graph should be reused, not duplicated");
    }

    @Test
    void aDocumentWithTheWrongRootElementIsRejected(GraphDatabaseService db) {
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                importTei(db, "<html><body>no TEI here</body></html>", PROFILE));

        assertTrue(exception.getMessage().contains("expected root element <TEI>"),
                "phase 1 should reject a document the profile cannot describe: " + exception.getMessage());
    }
}
