package atag.text;

import atag.profile.ProfileProcedures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterators;
import org.xmlunit.assertj3.XmlAssert;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The document hierarchy a profile describes, built by the import instead of by hand:
 * a corpus of letters with witnesses, a corpus of texts without the middle level, and a
 * single document that is no corpus at all.
 */
class CorpusImportTest {

    private static final String CORPUS = """
            <teiCorpus xmlns="http://www.tei-c.org/ns/1.0">
              <teiHeader><fileDesc><titleStmt><title>Corpus</title></titleStmt></fileDesc></teiHeader>
              <standOff>
                <listPerson><person xml:id="anna"><persName type="reg">Anna</persName><birth>1601</birth></person></listPerson>
                <listPlace><place xml:id="rome"><placeName type="reg">Roma</placeName></place></listPlace>
              </standOff>
              <TEI xml:id="l-1" type="letter" n="first">
                <teiHeader><fileDesc><titleStmt><title>Letter</title></titleStmt></fileDesc></teiHeader>
                <TEI xml:id="w-1" n="manuscript"><teiHeader><fileDesc><titleStmt><title>MS</title></titleStmt></fileDesc></teiHeader>
                <text><body><div type="letter"><rs corresp="anna">Anna</rs> in <rs corresp="rome">Roma</rs>.</div></body></text></TEI>
                <TEI xml:id="w-2"><teiHeader><fileDesc><titleStmt><title>Print</title></titleStmt></fileDesc></teiHeader>
                <text><body><div type="letter">Anna.</div></body></text></TEI>
              </TEI>
            </teiCorpus>
            """;

    private static final Map<String, Object> PROFILE = Map.of(
            "name", "corpus",
            "model", Map.of("collection", List.of("Corpus", "Letter"), "content", List.of("Witness"),
                    "entity", List.of("Entity")),
            "documents", List.of(
                    Map.of("xpath", "/*:teiCorpus", "concept", "collection", "label", "Corpus",
                            "id", "c-1", "headerXPath", "/*:teiCorpus/*:teiHeader", "sourceProperty", "xml"),
                    Map.of("xpath", "/*:teiCorpus/*:TEI", "concept", "collection", "label", "Letter"),
                    Map.of("xpath", "/*:TEI/*:TEI", "concept", "content", "label", "Witness")),
            "registers", List.of(
                    Map.of("xpath", "/*:teiCorpus/*:standOff/*:listPerson/*:person", "labels", List.of("Person"),
                            "labelXPath", "normalize-space(*:persName)", "sourceProperty", "tei"),
                    Map.of("xpath", "/*:teiCorpus/*:standOff/*:listPlace/*:place", "labels", List.of("Place"))),
            "import", Map.of(
                    "rootElement", "teiCorpus",
                    "xpath", "/*:TEI/*:text/*:body//node()[not(self::*:ab)]",
                    "referenceAttributes", List.of("corresp"),
                    "addUuid", false));

    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withDisabledServer()
            .withProcedure(Importer.class)
            .withProcedure(ProfileProcedures.class)
            .withFunction(ProfileProcedures.class)
            .withFunction(Utils.class)
            .build();

    /** the database is shared by the class, and every test imports a corpus of its own */
    @BeforeEach
    void emptyTheGraph(GraphDatabaseService db) {
        db.executeTransactionally("MATCH (n) DETACH DELETE n");
    }

    private List<String> rows(GraphDatabaseService db, String query, Map<String, Object> parameters) {
        return db.executeTransactionally(query, parameters,
                r -> Iterators.asList(r).stream().map(row -> (String) row.get("row")).toList());
    }

    private void importCorpus(GraphDatabaseService db, String xml, Map<String, Object> profile) {
        db.executeTransactionally("CALL atag.profile.write($profile) YIELD value RETURN value",
                Map.of("profile", profile));
        db.executeTransactionally("""
                CALL atag.text.import.corpus($xml, {profile: $name}) YIELD node RETURN count(node) AS count
                """, Map.of("xml", xml, "name", profile.get("name")));
    }

    @Test
    void aCorpusBecomesItsHierarchy(GraphDatabaseService db) {
        importCorpus(db, CORPUS, PROFILE);

        assertEquals(List.of("Corpus | c-1", "Letter | l-1", "Witness | w-1", "Witness | w-2"),
                rows(db, """
                        MATCH (n) WHERE n:Corpus OR n:Letter OR n:Witness
                        RETURN head(labels(n)) + ' | ' + n.uuid AS row ORDER BY row
                        """, Map.of()));

        assertEquals(List.of("w-1 | l-1 | c-1", "w-2 | l-1 | c-1"),
                rows(db, """
                        MATCH (w:Witness)-[:PART_OF]->(l:Letter)-[:PART_OF]->(c:Corpus)
                        RETURN w.uuid + ' | ' + l.uuid + ' | ' + c.uuid AS row ORDER BY row
                        """, Map.of()), "every level is part of the one above it");

        assertEquals(List.of("l-1 | letter | first"),
                rows(db, "MATCH (l:Letter) RETURN l.uuid + ' | ' + l.type + ' | ' + l.n AS row", Map.of()),
                "the attributes of a level become its properties");
    }

    @Test
    void theHeaderAndTheSourceAreKeptWhereTheProfileAsksForThem(GraphDatabaseService db) {
        importCorpus(db, CORPUS, PROFILE);

        assertEquals(List.of("Corpus", "Letter", "MS", "Print"),
                rows(db, """
                        MATCH (n) WHERE n:Corpus OR n:Letter OR n:Witness
                        RETURN atag.text.xpath(n.teiHeader, '//*:title/string()')[0] AS row ORDER BY row
                        """, Map.of()), "every level keeps its own header");

        assertEquals(List.of("true | false"),
                rows(db, """
                        MATCH (c:Corpus), (l:Letter)
                        RETURN toString(c.xml IS NOT NULL) + ' | ' + toString(l.xml IS NOT NULL) AS row
                        """, Map.of()), "only the level that asked for it keeps the source");
    }

    @Test
    void theTextOfEveryContentNodeIsImported(GraphDatabaseService db) {
        importCorpus(db, CORPUS, PROFILE);

        assertEquals(List.of("w-1 | Anna in Roma. | 3", "w-2 | Anna. | 1"),
                rows(db, """
                        MATCH (w:Witness)
                        OPTIONAL MATCH (w)-[:HAS_ANNOTATION]->(a:Annotation)
                        WITH w, count(a) AS annotations
                        RETURN w.uuid + ' | ' + w.plainText + ' | ' + annotations AS row ORDER BY row
                        """, Map.of()), "div plus the two references in the first witness");
    }

    @Test
    void theRegistersBecomeEntitiesOfTheCorpus(GraphDatabaseService db) {
        importCorpus(db, CORPUS, PROFILE);

        assertEquals(List.of("anna | Anna | Entity,Person", "rome | null | Entity,Place"),
                rows(db, """
                        MATCH (e:Entity)-[:PART_OF]->(:Corpus)
                        RETURN e.uuid + ' | ' + coalesce(e.label, 'null') + ' | '
                               + reduce(s = '', l IN labels(e) | s + CASE s WHEN '' THEN '' ELSE ',' END + l) AS row
                        ORDER BY row
                        """, Map.of()), "each register adds its own label, the labelXPath its display name");

        XmlAssert.assertThat(rows(db, "MATCH (e:Entity {uuid: 'anna'}) RETURN e.tei AS row", Map.of()).get(0))
                .and("""
                        <person xmlns="http://www.tei-c.org/ns/1.0" xml:id="anna">
                        <persName type="reg">Anna</persName><birth>1601</birth></person>""")
                .ignoreWhitespace().areIdentical();

        assertEquals(List.of("anna", "rome"),
                rows(db, """
                        MATCH (:Witness {uuid: 'w-1'})-[:HAS_ANNOTATION]->(:Annotation)-[:REFERS_TO]->(e:Entity)
                        RETURN e.uuid AS row ORDER BY row
                        """, Map.of()), "the references of the text resolve against the register");
    }

    @Test
    void aCorpusWithoutAMiddleLevelWorksTheSameWay(GraphDatabaseService db) {
        String corpus = """
                <teiCorpus xmlns="http://www.tei-c.org/ns/1.0"><teiHeader/>
                  <TEI xml:id="t-1"><teiHeader/><text><body><p>One.</p></body></text></TEI>
                  <TEI xml:id="t-2"><teiHeader/><text><body><p>Two.</p></body></text></TEI>
                </teiCorpus>
                """;
        importCorpus(db, corpus, Map.of(
                "name", "flat",
                "model", Map.of("collection", List.of("Shelf"), "content", List.of("Piece")),
                "documents", List.of(
                        Map.of("xpath", "/*:teiCorpus", "concept", "collection", "label", "Shelf", "id", "s-1"),
                        Map.of("xpath", "/*:teiCorpus/*:TEI", "concept", "content", "label", "Piece")),
                "import", Map.of("rootElement", "teiCorpus", "addUuid", false)));

        assertEquals(List.of("Piece | t-1 | One.", "Piece | t-2 | Two.", "Shelf | s-1 | null"),
                rows(db, """
                        MATCH (n) WHERE n:Shelf OR n:Piece
                        RETURN head(labels(n)) + ' | ' + n.uuid + ' | ' + coalesce(n.plainText, 'null') AS row
                        ORDER BY row
                        """, Map.of()));
    }

    @Test
    void aSingleDocumentNeedsNoCollectionAtAll(GraphDatabaseService db) {
        String tei = """
                <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="only"><teiHeader/>
                <text><body><p>Alone.</p></body></text></TEI>
                """;
        importCorpus(db, tei, Map.of(
                "name", "single",
                "model", Map.of("content", List.of("Sheet")),
                "documents", List.of(Map.of("xpath", "/*:TEI", "concept", "content", "label", "Sheet")),
                "import", Map.of("addUuid", false)));

        assertEquals(List.of("only | Alone. | 1"),
                rows(db, """
                        MATCH (s:Sheet)-[:HAS_ANNOTATION]->(a:Annotation)
                        WITH s.uuid AS uuid, s.plainText AS text, count(a) AS annotations
                        RETURN uuid + ' | ' + text + ' | ' + annotations AS row
                        """, Map.of()));
    }

    @Test
    void aLabelTheModelDoesNotKnowIsRejected(GraphDatabaseService db) {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> importCorpus(db, CORPUS, Map.of(
                "name", "wrong",
                "model", Map.of("collection", List.of("Corpus"), "content", List.of("Witness")),
                "documents", List.of(Map.of("xpath", "/*:teiCorpus", "concept", "collection", "label", "Bundle")),
                "import", Map.of("rootElement", "teiCorpus"))));

        assertTrue(exception.getMessage().contains("the model does not know Bundle as a label for collection"),
                exception.getMessage());
    }
}
