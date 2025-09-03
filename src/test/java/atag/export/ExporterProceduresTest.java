package atag.export;

import apoc.convert.Json;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterators;

import javax.ws.rs.ProcessingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExporterProceduresTest {

    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withFunction(ExporterProcedures.class)
            .withProcedure(ExporterProcedures.class)
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

        final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        URI uri = ExporterProceduresTest.class.getResource("/json-graph-schema.json").toURI();
        final JsonSchema schema = factory.getSchema(uri);
        JsonNode jsonNode = new ObjectMapper().readTree(json);
        Set<ValidationMessage> validationMessages = schema.validate(jsonNode);
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
                CALL atag.export.jgfFile(nodes, relationships, 'jgfExport.json') YIELD value
                RETURN value
                """, Collections.emptyMap(), r -> Iterators.single(r).get("value").toString());

        // Then
        assertEquals( "jgfExport.json", value);
    }
}