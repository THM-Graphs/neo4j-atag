package thm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.helpers.collection.Iterators;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ChainsProcedureTest {
    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withDisabledServer()
            .withProcedure(ChainsProcedure.class)
            /*.withFixture(db -> {
                try {

                    InputStream importFile = RouteProcedureNewModelTest.class.getClassLoader().getResourceAsStream("newmodel.cypher");
                    String allCmds = IOUtils.toString(importFile, Charset.defaultCharset());
                    for (String cmd: allCmds.split(";")) {
                        cmd = cmd.trim();
                        if (!cmd.isEmpty()) {
                            db.executeTransactionally(cmd);
                        }
                    }
                    return null;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })*/
            .build();

    @Test
    public void testChain(GraphDatabaseService db) {

        String text = "what a nice text";
        db.executeTransactionally("CALL thm.chain($text, '((?<=\\\\s)|(?=\\\\s))', 'Character', 'NEXT_CHARACTER', false) YIELD path RETURN path", Map.of( "text", text), result -> {
//        db.executeTransactionally("CALL thm.chain($text, '', 'Character', 'NEXT_CHARACTER', true) YIELD path RETURN path", Map.of( "text", text), result -> {

            Map<String, Object> map = Iterators.single(result);
            Path path = (Path) map.get("path");

            assertEquals(6, path.length());
            List<Node> nodes = Iterables.asList(path.nodes());
            assertEquals("what", nodes.get(0).getProperty("text"));
            assertEquals("text", nodes.get(nodes.size()-1).getProperty("text"));
            return true;
        });
    }

    @Test
    public void testCharacterChain(GraphDatabaseService db) {

        String text = "what a nice text";
        db.executeTransactionally("CALL thm.characterChain($text) YIELD path RETURN path", Map.of( "text", text), result -> {
//        db.executeTransactionally("CALL thm.chain($text, '', 'Character', 'NEXT_CHARACTER', true) YIELD path RETURN path", Map.of( "text", text), result -> {

            Map<String, Object> map = Iterators.single(result);
            Path path = (Path) map.get("path");

            assertEquals(15, path.length());
            List<Node> nodes = Iterables.asList(path.nodes());
            assertEquals("w", nodes.get(0).getProperty("text"));
            assertEquals("t", nodes.get(nodes.size()-1).getProperty("text"));
            return true;
        });
    }

    @Test
    public void testFullChain(GraphDatabaseService db) {

        String text = "what a nice text";
        db.executeTransactionally("CREATE (s:Text{text:$text}) WITH s CALL thm.fullChain(s, 'text') RETURN s", Map.of( "text", text), result -> {

            Map<String, Object> map = Iterators.single(result);
            Node startNode = (Node) map.get("s");

            assertNotNull(startNode.getSingleRelationship(ChainsProcedure.REL_NEXT_TOKEN, Direction.OUTGOING));
            assertNotNull(startNode.getSingleRelationship(ChainsProcedure.REL_NEXT_CHARACTER, Direction.OUTGOING));

            return true;
        });
    }

}
