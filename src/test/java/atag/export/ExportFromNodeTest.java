package atag.export;

import apoc.convert.Json;
import atag.chains.ChainsProcedure;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterators;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


class ExportFromNodeTest {

    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withFunction(ExporterProcedures.class)
            .withProcedure(ExporterProcedures.class)
            .withProcedure(ChainsProcedure.class)
            .withFunction(Json.class)
            .withConfig(GraphDatabaseSettings.procedure_unrestricted, List.of("atag.*"))
            .withDisabledServer()
            .withFixture("""
                CREATE (m:Collection:Manuscript {label: 'Handschrift R', uuid: 'f5f6deaa-8356-4c97-931a-14e0b02f08b2'})
                CREATE (letter:Collection:Letter {label: 'R86: Hildegard von Rupertsberg an Berthold von Zwiefalten', uuid: '876236f2-fb7f-4128-bfbf-040560cf9385'})
                CREATE (letter)-[:PART_OF]->(m)

                CREATE (t:Text {uuid: 'bd96acbe-9f45-4bf7-b6da-b40f730f4a9a', text: ' Responsum hildegardis. Lux uiuens dicit. Quendam hominem uidi.'})
                CREATE (t)-[:PART_OF]->(letter)

                CREATE (hildegard:Entity:Person {label: 'Hildegard von Rupertsberg', uuid: 'fc9d709a-05a7-4a1d-9c62-a5e1960e5ef9', wikidataId: 'Q70991'})
                CREATE (sender:Role:Entity {label: 'Sender', uuid: '26cfb709-a74e-4d22-9e9e-24ce811b5686'})

                CREATE (a1:Annotation {type: 'line', startIndex: 0, endIndex: 23, text: ' Responsum hildegardis. ', uuid: 'a1000001-0000-0000-0000-000000000001'})
                CREATE (t)-[:HAS_ANNOTATION]->(a1)
                CREATE (a2:Annotation {type: 'head', startIndex: 1, endIndex: 22, text: 'Responsum hildegardis.', uuid: 'a1000002-0000-0000-0000-000000000002'})
                CREATE (t)-[:HAS_ANNOTATION]->(a2)
                CREATE (a3:Annotation {type: 'emphasised', subType: 'rubricated', startIndex: 1, endIndex: 22, text: 'Responsum hildegardis.', uuid: 'a1000003-0000-0000-0000-000000000003'})
                CREATE (t)-[:HAS_ANNOTATION]->(a3)
                CREATE (a4:Annotation {type: 'entity', subType: 'person', startIndex: 1, endIndex: 9, text: 'Responsum', uuid: 'a1000004-0000-0000-0000-000000000004'})
                CREATE (t)-[:HAS_ANNOTATION]->(a4)
                CREATE (a4)-[:REFERS_TO]->(sender)
                CREATE (a4)-[:REFERS_TO]->(hildegard)
                CREATE (a5:Annotation {type: 'entity', subType: 'person', startIndex: 11, endIndex: 21, text: 'hildegardis', uuid: 'a1000005-0000-0000-0000-000000000005'})
                CREATE (t)-[:HAS_ANNOTATION]->(a5)
                CREATE (a5)-[:REFERS_TO]->(hildegard)
                CREATE (a6:Annotation {type: 'line', startIndex: 24, endIndex: 62, text: 'Lux uiuens dicit. Quendam hominem uidi.', uuid: 'a1000006-0000-0000-0000-000000000006'})
                CREATE (t)-[:HAS_ANNOTATION]->(a6)
                CREATE (a7:Annotation {type: 'emphasised', subType: 'rubricated', startIndex: 24, endIndex: 24, text: 'L', uuid: 'a1000007-0000-0000-0000-000000000007'})
                CREATE (t)-[:HAS_ANNOTATION]->(a7)
                CREATE (a8:Annotation {type: 'expansion', startIndex: 48, endIndex: 48, text: 'm', uuid: 'a1000008-0000-0000-0000-000000000008'})
                CREATE (t)-[:HAS_ANNOTATION]->(a8)
                CREATE (c1:Annotation {type: 'commentary', label: 'comment', uuid: 'c1000001-0000-0000-0000-000000000001'})
                CREATE (a4)-[:HAS_ANNOTATION]->(c1)
                CREATE (o1:Annotation {type: 'floating', startIndex: 5, endIndex: 6, uuid: 'f0000001-0000-0000-0000-000000000001'})
                """)
            .build();

    private void setupCharacterChain(GraphDatabaseService db) {
        db.executeTransactionally("""
                MATCH (t:Text {uuid: 'bd96acbe-9f45-4bf7-b6da-b40f730f4a9a'})
                CALL atag.chains.fullChain(t, 'text')
                RETURN t
                """);
    }

    @Test
    void testExportWrapperFromStartNode(GraphDatabaseService db) throws JsonProcessingException {
        setupCharacterChain(db);

        String json = db.executeTransactionally("""
                MATCH (m:Manuscript {uuid: 'f5f6deaa-8356-4c97-931a-14e0b02f08b2'})
                CALL atag.export.jgf.fromNode(m, {}) YIELD value
                WITH apoc.convert.toJson(value) AS json
                RETURN json
                """, Collections.emptyMap(), r -> Iterators.single(r).get("json").toString());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode graph = root.path("graph");
        assertFalse(graph.isMissingNode(), "JGF must contain a 'graph' key");
        assertTrue(graph.path("directed").asBoolean());

        JsonNode nodes = graph.path("nodes");
        assertFalse(nodes.isMissingNode(), "graph must contain 'nodes'");
        assertTrue(nodes.size() > 1, "export should contain multiple nodes");

        JsonNode edges = graph.path("edges");
        assertFalse(edges.isMissingNode(), "graph must contain 'edges'");
        assertFalse(edges.isEmpty(), "export should contain edges");

        boolean hasLetterNode = false;
        boolean hasTextNode = false;
        boolean hasAnnotationNode = false;
        var fields = nodes.fields();
        while (fields.hasNext()) {
            String label = fields.next().getValue().path("label").asText();
            if (label.contains("Letter")) hasLetterNode = true;
            if (label.contains("Text")) hasTextNode = true;
            if (label.contains("Annotation")) hasAnnotationNode = true;
        }
        assertTrue(hasLetterNode, "export should contain the Letter node");
        assertTrue(hasTextNode, "export should contain the Text node");
        assertTrue(hasAnnotationNode, "export should contain Annotation nodes");
    }

    @Test
    void testExportWrapperExcludesCharacterChains(GraphDatabaseService db) throws JsonProcessingException {
        setupCharacterChain(db);

        String json = db.executeTransactionally("""
                MATCH (m:Manuscript {uuid: 'f5f6deaa-8356-4c97-931a-14e0b02f08b2'})
                CALL atag.export.jgf.fromNode(m, {includeCharacterChain: false}) YIELD value
                WITH apoc.convert.toJson(value) AS json
                RETURN json
                """, Collections.emptyMap(), r -> Iterators.single(r).get("json").toString());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode nodes = root.path("graph").path("nodes");
        var fields = nodes.fields();
        while (fields.hasNext()) {
            String label = fields.next().getValue().path("label").asText();
            assertFalse(label.contains("Character"),
                    "export with includeCharacterChain:false should not contain Character nodes");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStandoffJsonFromNode(GraphDatabaseService db) {
        Map<String, Object> value = db.executeTransactionally("""
                MATCH (t:Text {uuid: 'bd96acbe-9f45-4bf7-b6da-b40f730f4a9a'})
                CALL atag.export.standoff_json.fromNode(t, {}) YIELD value
                RETURN value
                """, Collections.emptyMap(), r -> (Map<String, Object>) Iterators.single(r).get("value"));

        assertTrue(value.containsKey("text"), "root should contain the Text anchor's 'text' property");
        assertTrue(value.containsKey("uuid"), "root should contain the Text anchor's 'uuid' property");

        List<Map<String, Object>> annotations = (List<Map<String, Object>>) value.get("annotations");
        assertNotNull(annotations);
        assertEquals(8, annotations.size(), "all annotations should sit on their Text anchor");
        assertTrue(annotations.stream().allMatch(p -> p.containsKey("startIndex")),
                "each annotation entry should have a startIndex property");

        List<Long> startIndices = annotations.stream()
                .map(a -> ((Number) a.get("startIndex")).longValue())
                .toList();
        List<Long> sorted = startIndices.stream().sorted().toList();
        assertEquals(sorted, startIndices, "annotations should be ordered by startIndex");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStandoffJsonNestsAnnotationOnAnnotation(GraphDatabaseService db) {
        Map<String, Object> value = db.executeTransactionally("""
                MATCH (t:Text {uuid: 'bd96acbe-9f45-4bf7-b6da-b40f730f4a9a'})
                CALL atag.export.standoff_json.fromNode(t, {}) YIELD value
                RETURN value
                """, Collections.emptyMap(), r -> (Map<String, Object>) Iterators.single(r).get("value"));

        List<Map<String, Object>> annotations = (List<Map<String, Object>>) value.get("annotations");
        assertTrue(annotations.stream().noneMatch(a -> "commentary".equals(a.get("type"))),
                "a commentary on an annotation must not sit directly on the Text anchor");

        Map<String, Object> a4 = annotations.stream()
                .filter(a -> "a1000004-0000-0000-0000-000000000004".equals(a.get("uuid")))
                .findFirst()
                .orElseThrow();
        List<Map<String, Object>> nested = (List<Map<String, Object>>) a4.get("annotations");
        assertNotNull(nested, "the annotated annotation should carry its commentary under 'annotations'");
        assertEquals(1, nested.size());
        assertEquals("commentary", nested.get(0).get("type"),
                "the commentary should be nested inside its originating annotation");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStandoffJsonNestsAnchorsFromCollection(GraphDatabaseService db) {
        Map<String, Object> value = db.executeTransactionally("""
                MATCH (m:Manuscript {uuid: 'f5f6deaa-8356-4c97-931a-14e0b02f08b2'})
                CALL atag.export.standoff_json.fromNode(m, {}) YIELD value
                RETURN value
                """, Collections.emptyMap(), r -> (Map<String, Object>) Iterators.single(r).get("value"));

        assertEquals("Handschrift R", value.get("label"),
                "root should be the top-level Manuscript collection");
        assertFalse(value.containsKey("annotations"),
                "an empty annotations list should be omitted on the root");

        List<Map<String, Object>> manuscriptParts = (List<Map<String, Object>>) value.get("parts");
        assertNotNull(manuscriptParts, "Manuscript should nest its Letter as a part");
        assertEquals(1, manuscriptParts.size());
        Map<String, Object> letter = manuscriptParts.get(0);
        assertTrue(((List<?>) letter.get("annotations")).isEmpty(),
                "the Letter anchor carries no annotations of its own");

        List<Map<String, Object>> letterParts = (List<Map<String, Object>>) letter.get("parts");
        assertNotNull(letterParts, "Letter should nest its Text as a part");
        assertEquals(1, letterParts.size());
        Map<String, Object> text = letterParts.get(0);

        assertTrue(text.containsKey("text"), "the Text anchor should carry its 'text' property");
        assertNull(text.get("parts"), "the Text anchor is a leaf with no nested anchors");

        List<Map<String, Object>> annotations = (List<Map<String, Object>>) text.get("annotations");
        assertNotNull(annotations);
        assertEquals(8, annotations.size(), "all annotations should sit on their Text anchor, not the collections");
    }

    @Test
    void testStandoffXmlFromNode(GraphDatabaseService db) {
        String xml = db.executeTransactionally("""
                MATCH (t:Text {uuid: 'bd96acbe-9f45-4bf7-b6da-b40f730f4a9a'})
                CALL atag.export.standoff_xml.fromNode(t, {}) YIELD value
                RETURN value
                """, Collections.emptyMap(), r -> (String) Iterators.single(r).get("value"));

        assertTrue(xml.startsWith("<?xml"), "output should be an XML document");
        assertTrue(xml.contains("<Text "), "root element should be named after the Text anchor's label");
        assertTrue(xml.contains("uuid=\"bd96acbe-9f45-4bf7-b6da-b40f730f4a9a\""),
                "root element should carry the Text anchor's uuid as an attribute");
        int annotationCount = xml.split("<annotation ", -1).length - 1;
        assertEquals(9, annotationCount, "should emit one <annotation> element per Annotation node, nested ones included");
        assertTrue(xml.contains("type=\"commentary\""), "the nested commentary annotation should be emitted");
    }

    @Test
    void testStandoffXmlNestsAnchorsFromCollection(GraphDatabaseService db) {
        String xml = db.executeTransactionally("""
                MATCH (m:Manuscript {uuid: 'f5f6deaa-8356-4c97-931a-14e0b02f08b2'})
                CALL atag.export.standoff_xml.fromNode(m, {}) YIELD value
                RETURN value
                """, Collections.emptyMap(), r -> (String) Iterators.single(r).get("value"));

        assertTrue(xml.contains("uuid=\"f5f6deaa-8356-4c97-931a-14e0b02f08b2\""),
                "the Manuscript should be the outermost element");
        assertTrue(xml.contains("<Text "), "the Text anchor should be nested as its own element");
        assertTrue(xml.indexOf("<Text ") > xml.indexOf("f5f6deaa"),
                "the Text element should be nested inside the Manuscript element");
        int annotationCount = xml.split("<annotation ", -1).length - 1;
        assertEquals(9, annotationCount, "annotations should sit inside their Text anchor element, nested ones included");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStandoffJsonList(GraphDatabaseService db) {
        Map<String, Object> value = db.executeTransactionally("""
                MATCH (t:Text {uuid: 'bd96acbe-9f45-4bf7-b6da-b40f730f4a9a'})
                MATCH (t)-[:HAS_ANNOTATION]->(a:Annotation)
                WITH t, collect(a) AS annotations
                WITH [t] + annotations AS nodes
                CALL atag.export.standoff_json.list(nodes, [], {}) YIELD value
                RETURN value
                """, Collections.emptyMap(), r -> (Map<String, Object>) Iterators.single(r).get("value"));

        assertTrue(value.containsKey("text"), "root should contain the document's 'text' property");

        List<Map<String, Object>> annotations = (List<Map<String, Object>>) value.get("annotations");
        assertNotNull(annotations);
        assertFalse(annotations.isEmpty(), "annotations should contain the annotations");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStandoffJsonExcludesUnanchoredAnnotations(GraphDatabaseService db) {
        Map<String, Object> value = db.executeTransactionally("""
                MATCH (t:Text {uuid: 'bd96acbe-9f45-4bf7-b6da-b40f730f4a9a'})
                MATCH (o:Annotation {uuid: 'f0000001-0000-0000-0000-000000000001'})
                CALL atag.export.standoff_json.list([t, o], [], {}) YIELD value
                RETURN value
                """, Collections.emptyMap(), r -> (Map<String, Object>) Iterators.single(r).get("value"));

        assertFalse(value.containsKey("annotations"),
                "an annotation with no HAS_ANNOTATION parent must be excluded, not attached to the root");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStandoffJsonWithIgnoredProperties(GraphDatabaseService db) {
        Map<String, Object> value = db.executeTransactionally("""
                MATCH (t:Text {uuid: 'bd96acbe-9f45-4bf7-b6da-b40f730f4a9a'})
                CALL atag.export.standoff_json.fromNode(t, {ignoreProperties: ['text']}) YIELD value
                RETURN value
                """, Collections.emptyMap(), r -> (Map<String, Object>) Iterators.single(r).get("value"));

        assertFalse(value.containsKey("text"), "an ignored property should not be serialized");
        List<Map<String, Object>> annotations = (List<Map<String, Object>>) value.get("annotations");
        assertTrue(annotations.stream().noneMatch(a -> a.containsKey("text")),
                "ignoring a property should apply to every node of the export");
    }

    @Test
    void testExportWrapperWithAnnotationTypeFilter(GraphDatabaseService db) throws JsonProcessingException {
        setupCharacterChain(db);

        String json = db.executeTransactionally("""
                MATCH (m:Manuscript {uuid: 'f5f6deaa-8356-4c97-931a-14e0b02f08b2'})
                CALL atag.export.jgf.fromNode(m, {annotationTypes: ['entity', 'head']}) YIELD value
                WITH apoc.convert.toJson(value) AS json
                RETURN json
                """, Collections.emptyMap(), r -> Iterators.single(r).get("json").toString());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode nodes = root.path("graph").path("nodes");
        boolean hasEntityAnnotation = false;
        boolean hasHeadAnnotation = false;
        boolean hasLineAnnotation = false;
        boolean hasExpansionAnnotation = false;
        var fields = nodes.fields();
        while (fields.hasNext()) {
            JsonNode metadata = fields.next().getValue().path("metadata");
            String type = metadata.path("type").asText();
            if ("entity".equals(type)) hasEntityAnnotation = true;
            if ("head".equals(type)) hasHeadAnnotation = true;
            if ("line".equals(type)) hasLineAnnotation = true;
            if ("expansion".equals(type)) hasExpansionAnnotation = true;
        }
        assertTrue(hasEntityAnnotation, "export should contain 'entity' annotations");
        assertTrue(hasHeadAnnotation, "export should contain 'head' annotations");
        assertFalse(hasLineAnnotation, "export with annotationTypes:['entity','head'] should not contain 'line' annotations");
        assertFalse(hasExpansionAnnotation, "export with annotationTypes:['entity','head'] should not contain 'expansion' annotations");
    }
}