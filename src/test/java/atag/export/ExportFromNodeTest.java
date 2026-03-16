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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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