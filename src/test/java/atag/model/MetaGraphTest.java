package atag.model;

import atag.export.ExporterProcedures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterators;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A project that refines the RAMEN concepts with labels of its own: content is a
 * {@code Transcript}, annotations are {@code Note}s. Written to the graph as a meta
 * graph, that model is available to every later profile as {@code model: 'meta'}.
 */
class MetaGraphTest {

    private static final Map<String, Object> MODEL = Map.of("model", Map.of(
            "content", List.of("Transcript"),
            "annotation", List.of("Note"),
            "entity", List.of("Person")));

    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withProcedure(ModelProcedures.class)
            .withProcedure(ExporterProcedures.class)
            .withConfig(GraphDatabaseSettings.procedure_unrestricted, List.of("atag.*"))
            .withDisabledServer()
            .withFixture("""
                CREATE (t:Transcript {uuid: 'transcript-1', text: 'Hildegard writes to Berthold.'})
                CREATE (n:Note {uuid: 'note-1', type: 'person-reference', startIndex: 0, endIndex: 9})
                CREATE (t)-[:HAS_ANNOTATION]->(n)
                """)
            .build();

    private void writeModel(GraphDatabaseService db) {
        db.executeTransactionally("CALL atag.model.meta.write($config) YIELD value RETURN value",
                Map.of("config", MODEL));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theModelCanBeReadBackFromTheGraph(GraphDatabaseService db) {
        writeModel(db);

        Map<String, Object> model = db.executeTransactionally(
                "CALL atag.model.meta.read() YIELD value RETURN value", Map.of(),
                r -> (Map<String, Object>) Iterators.single(r).get("value"));

        assertEquals(List.of("Transcript"), model.get("content"));
        assertEquals(List.of("Note"), model.get("annotation"));
        assertEquals(List.of("Person"), model.get("entity"));
        assertEquals(List.of("Collection"), model.get("collection"), "unrefined concepts keep their default label");
        assertEquals("HAS_ANNOTATION", model.get("hasAnnotation"));
    }

    @Test
    void anEntityMayBePartOfACollection(GraphDatabaseService db) {
        writeModel(db);

        long relations = db.executeTransactionally("""
                MATCH (:Meta:Concept {name: 'ENTITY'})-[:PART_OF]->(:Meta:Concept {name: 'COLLECTION'})
                RETURN count(*) AS count
                """, Map.of(), r -> (Long) Iterators.single(r).get("count"));
        assertEquals(1, relations, "a register of entities belongs to the collection that declares it");
    }

    @Test
    void writingTheSameModelTwiceDoesNotDuplicateIt(GraphDatabaseService db) {
        writeModel(db);
        writeModel(db);

        long types = db.executeTransactionally(
                "MATCH (t:Meta:Type {label: 'Transcript'}) RETURN count(t) AS count", Map.of(),
                r -> (Long) Iterators.single(r).get("count"));
        assertEquals(1, types);
    }

    @Test
    @SuppressWarnings("unchecked")
    void anExportCanTakeItsModelFromTheMetaGraph(GraphDatabaseService db) {
        writeModel(db);

        Map<String, Object> value = db.executeTransactionally("""
                MATCH (t:Transcript {uuid: 'transcript-1'})
                CALL atag.export.standoff_json.fromNode(t, {model: 'meta'}) YIELD value
                RETURN value
                """, Map.of(), r -> (Map<String, Object>) Iterators.single(r).get("value"));

        assertTrue(value.containsKey("text"), "the transcript should be recognized as the content anchor");
        List<Map<String, Object>> annotations = (List<Map<String, Object>>) value.get("annotations");
        assertEquals(1, annotations.size(), "a Note should be recognized as an annotation");
        assertEquals("person-reference", annotations.get(0).get("type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void withoutTheModelTheProjectLabelsAreNotRecognized(GraphDatabaseService db) {
        Map<String, Object> value = db.executeTransactionally("""
                MATCH (t:Transcript {uuid: 'transcript-1'})
                CALL atag.export.standoff_json.fromNode(t, {}) YIELD value
                RETURN value
                """, Map.of(), r -> (Map<String, Object>) Iterators.single(r).get("value"));

        assertTrue(value.isEmpty() || !value.containsKey("text"),
                "the default model knows nothing about a Transcript, so there is no anchor to export");
    }
}
