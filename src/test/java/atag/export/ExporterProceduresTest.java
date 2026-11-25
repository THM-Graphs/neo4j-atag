package atag.export;

import apoc.convert.Json;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterators;

import javax.ws.rs.ProcessingException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExporterProceduresTest {

    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withFunction(ExporterProcedures.class)
            .withProcedure(ExporterProcedures.class)
            .withConfig(GraphDatabaseSettings.procedure_unrestricted, List.of("atag.*"))
            .withFunction(Json.class)
            //.withConfig(ApocConfig.APOC_EXPORT_FILE_ENABLED, true)
            .withDisabledServer()
            .withFixture("""
                CREATE (a:Person {name: 'Alice', dob: date({year: 2012, month: 6, day: 1}), height: 175})
                CREATE (b:Person {name: 'Bob'})
                CREATE (a)-[:KNOWS]->(b)
                """)
            .build();

    @Test
    void testExportToJsonFunction(GraphDatabaseService db) throws URISyntaxException, ProcessingException, JsonProcessingException {
        // When
        String json = db.executeTransactionally("""
                MATCH (a)-[r]->(b)
                WITH collect(a) + collect(b) AS nodes, collect(r) AS relationships
                RETURN apoc.convert.toJson(atag.export.jgf(nodes, relationships)) AS json
                """, Collections.emptyMap(), r -> Iterators.single(r).get("json").toString());
//        System.out.println(json);

        final SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);
        final Schema schema = registry.getSchema(
                ExporterProceduresTest.class.getResourceAsStream("/json-graph-schema.json"), InputFormat.JSON);
        List<Error> validationMessages = schema.validate(json, InputFormat.JSON);
        validationMessages.forEach(validationMessage -> System.out.println(validationMessage.getMessage()));

        // Then
        assertEquals(0, validationMessages.size(), "JSON does not conform to schema");
    }

    @Test
    void testExportToJsonProcedure(GraphDatabaseService db) throws URISyntaxException, ProcessingException, JsonProcessingException {
        // When
        String value = db.executeTransactionally("""
                MATCH (a)-[r]->(b)
                WITH collect(a) + collect(b) AS nodes, collect(r) AS relationships
                CALL atag.export.jgf.list(nodes, relationships, {fileName: 'jgfExport.json'}) YIELD value
                RETURN value
                """, Collections.emptyMap(), r -> Iterators.single(r).get("value").toString());

        // Then
        assertTrue(value.endsWith("bytes written to jgfExport.json"));
    }

}