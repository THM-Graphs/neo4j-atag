package atag.chains;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.helpers.collection.Iterators;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

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

    static Map<String, Object> configuration = Map.of(
            "textLabel", "Text",
            "elementLabel", "Token",
            "relationshipType", "NEXT_TOKEN"
    );

    public static Stream<Arguments> testTokenChain() {
        return Stream.of(
                Arguments.of("here's a comma, in this text", 13),
                Arguments.of("Ümlaute für den Spaß!", 7)
        );
    }

    private static Consumer<GraphDatabaseService> assertTokenCount(long expectedCount) {
        return db -> db.executeTransactionally("MATCH (t:Token) RETURN count(t) as count", Collections.emptyMap(), result -> {
            assertEquals(expectedCount, (long) Iterators.single(result).get("count"));
            return true;
        });
    }

    public static Stream<Arguments> testUpdateInitial() {

        return Stream.of(
                Arguments.of(Collections.emptyList(), 0, 0, assertTokenCount(0)),
                Arguments.of(List.of(Map.of("uuid", uuid())), 0, 1, assertTokenCount(1)),
                Arguments.of(List.of(Map.of("uuid", uuid()), Map.of("uuid", uuid())), 1, 2, assertTokenCount(2))
        );
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static void validatePathLength(GraphDatabaseService db, String query, Map<String, Object> params,
                                           int expectedPathLength) {
        validatePathLength(db, query, params, expectedPathLength, (Path entities) -> {});
    }

    private static void validatePathLength(GraphDatabaseService db, String query, Map<String, Object> params,
                                           int expectedPathLength, Consumer<Path> assertion) {
        db.executeTransactionally(query,
                params,
                result -> {
                    Path path = (Path) Iterators.single(result).get("path");
                    assertEquals(expectedPathLength, path.length());
                    assertion.accept(path);
                    return true;
                });
    }

    @AfterEach
    void cleanup(GraphDatabaseService db) {
        db.executeTransactionally("MATCH (n) DETACH DELETE n");
    }

    @Test
    public void testChain(GraphDatabaseService db) {

        String text = "what a nice text";
        db.executeTransactionally("CALL atag.chains.chain($text, '((?<=\\\\s)|(?=\\\\s))', 'Character', 'NEXT_CHARACTER', false) YIELD path RETURN path", Map.of( "text", text), result -> {
//        db.executeTransactionally("CALL atag.chains.chain($text, '', 'Character', 'NEXT_CHARACTER', true) YIELD path RETURN path", Map.of( "text", text), result -> {

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
        db.executeTransactionally("CALL atag.chains.characterChain($text) YIELD path RETURN path", Map.of( "text", text), result -> {
            Map<String, Object> map = Iterators.single(result);
            Path path = (Path) map.get("path");

            assertEquals(15, path.length());
            List<Node> nodes = Iterables.asList(path.nodes());

            for (int index=0; index<text.length(); index++) {
                assertEquals(Character.toString(text.charAt(index)), nodes.get(index).getProperty("text"));
                assertTrue(nodes.get(index).hasProperty(ChainsProcedure.PROPERTY_START_INDEX));
                assertTrue(nodes.get(index).hasProperty(ChainsProcedure.PROPERTY_START_INDEX));
            }
            return true;
        });
    }

    @Test
    public void testCharacterChainWithIndex(GraphDatabaseService db) {
        String text = "what a nice text";
        db.executeTransactionally("CALL atag.chains.characterChain($text, true) YIELD path RETURN path", Map.of( "text", text), result -> {
            Map<String, Object> map = Iterators.single(result);
            Path path = (Path) map.get("path");

            assertEquals(15, path.length());
            List<Node> nodes = Iterables.asList(path.nodes());
            assertEquals("w", nodes.get(0).getProperty("text"));
            assertEquals("t", nodes.get(nodes.size()-1).getProperty("text"));
            for (int i = 0; i < nodes.size(); i++) {
                assertEquals(i, nodes.get(i).getProperty(ChainsProcedure.PROPERTY_START_INDEX));
                assertEquals(i, nodes.get(i).getProperty(ChainsProcedure.PROPERTY_END_INDEX));
            }
            return true;
        });
    }

    @Test
    public void testFullChain(GraphDatabaseService db) {

        String text = "what a nice text";
        db.executeTransactionally("CREATE (s:Text{text:$text}) WITH s CALL atag.chains.fullChain(s, 'text') RETURN s", Map.of( "text", text), result -> {

            Map<String, Object> map = Iterators.single(result);
            Node startNode = (Node) map.get("s");

            assertNotNull(startNode.getSingleRelationship(ChainsProcedure.REL_NEXT_TOKEN, Direction.OUTGOING));
            assertNotNull(startNode.getSingleRelationship(ChainsProcedure.REL_NEXT_CHARACTER, Direction.OUTGOING));

            return true;
        });
    }

    @ParameterizedTest
    @MethodSource
    public void testTokenChain(String text, int expected, GraphDatabaseService db) {
        int pathLength = db.executeTransactionally("CALL atag.chains.tokenChain($text) YIELD path RETURN path",
                Map.of("text", text),
                result -> ((Path) Iterators.single(result).get("path")).length()
        );
        assertEquals(expected, pathLength);
    }

    @ParameterizedTest
    @MethodSource
    public void testUpdateInitial(List<Map<String, Object>> characterList, int expectedLength, int expectedQueryLength,
                                  Consumer<GraphDatabaseService> additionalAssertion, GraphDatabaseService db) {
        String uuidText = uuid();
        String uuidStart = uuid();
        String uuidEnd = uuid();
        Map<String, Object> params = Map.of("uuidText", uuidText, "uuidStart", uuidStart, "uuidEnd", uuidEnd,"config", configuration,
                "characterList", characterList);

        // fixture
        db.executeTransactionally("""
                CREATE (t:Text{uuid:$uuidText})
                CREATE (s:Token{uuid:$uuidStart})
                CREATE (e:Token{uuid:$uuidEnd})
                CREATE (t)-[:NEXT_TOKEN]->(s)
                CREATE (s)-[:NEXT_TOKEN]->(e)
                """, params);

        // empty modification list
        validatePathLength(db, """
                CALL atag.chains.update($uuidText, null, null, $characterList, $config) YIELD path
                RETURN path
                """, params, expectedLength);

        validatePathLength(db, """
                MATCH path=(:Text{uuid:$uuidText})-[:NEXT_TOKEN*0..]->(x)
                WHERE NOT (x)-[:NEXT_TOKEN]->()
                RETURN path
                """, params, expectedQueryLength);
        if (additionalAssertion != null) {
            additionalAssertion.accept(db);
        }
    }

    @Test
    public void testUpdateEmptyList(GraphDatabaseService db) {
        String uuidText = uuid();
        String uuidStart = uuid();
        String uuidEnd = uuid();
        Map<String, Object> params = Map.of("uuidText", uuidText, "uuidStart", uuidStart, "uuidEnd", uuidEnd,
        "config", configuration);

        // fixture
        db.executeTransactionally("""
                CREATE (t:Text{uuid:$uuidText})
                CREATE (s:Token{uuid:$uuidStart})
                CREATE (e:Token{uuid:$uuidEnd})
                CREATE (t)-[:NEXT_TOKEN]->(s)
                CREATE (s)-[:NEXT_TOKEN]->(e)
                """, params);

        // empty modification list
        validatePathLength(db, """
                CALL atag.chains.update($uuidText, $uuidStart, $uuidEnd, [], $config) YIELD path
                RETURN path
                """, params, 0);

        validatePathLength(db, """
                MATCH path=(:Token{uuid:$uuidStart})-[:NEXT_TOKEN*]->(:Token{uuid:$uuidEnd})
                RETURN path
                """, params, 1);
    }

    @Test
    public void testUpdateAdd(GraphDatabaseService db) {
        String uuidText = uuid();
        String uuidStart = uuid();
        String uuidEnd = uuid();
        Map<String, Object> params = Map.of("uuidText", uuidText, "uuidStart", uuidStart, "uuidEnd", uuidEnd,
                "config", configuration);

        // fixture
        db.executeTransactionally("""
                CREATE (t:Text{uuid:$uuidText})
                CREATE (s:Token{uuid:$uuidStart})
                CREATE (e:Token{uuid:$uuidEnd})
                CREATE (t)-[:NEXT_TOKEN]->(s)
                CREATE (s)-[:NEXT_TOKEN]->(e)
                """, params);

        // insert two nodes
        validatePathLength(db, """
                CALL atag.chains.update($uuidText, $uuidStart, $uuidEnd, [
                   {
                        uuid: '0',
                        tagName: 'a'
                    },
                    {
                        uuid: '1',
                        tagName: 'b'
                    }
                ], $config) YIELD path
                RETURN path
                """, params, 1);
        validatePathLength(db, """
                MATCH path=(:Token{uuid:$uuidStart})-[:NEXT_TOKEN*]->(:Token{uuid:$uuidEnd})
                RETURN path
                """, params, 3);
    }

    @Test
    public void testUpdateModify(GraphDatabaseService db, Neo4j neo4j) {
//        System.out.println(neo4j.boltURI());
        String uuidText = "uuidText";
        String uuidStart = "uuidStart";
        String uuidEnd = "uuidEnd";
        String uuidMiddle1 = "uuidMiddle1";
        String uuidMiddle2 = "uuidMiddle2";
        Map<String, Object> params = Map.of("uuidText", uuidText, "uuidStart", uuidStart, "uuidEnd", uuidEnd,
                "uuidMiddle1", uuidMiddle1, "uuidMiddle2", uuidMiddle2, "config", configuration);

        // fixture
        db.executeTransactionally("""
                CREATE (t:Text{uuid:$uuidText})
                CREATE (s:Token{uuid:$uuidStart})
                CREATE (m1:Token{uuid:$uuidMiddle1, tagName:'a'})
                CREATE (m2:Token{uuid:$uuidMiddle2, tagName:'b'})
                CREATE (e:Token{uuid:$uuidEnd})
                CREATE (t)-[:NEXT_TOKEN]->(s)
                CREATE (s)-[:NEXT_TOKEN]->(m1)
                CREATE (m1)-[:NEXT_TOKEN]->(m2)
                CREATE (m2)-[:NEXT_TOKEN]->(e)
                """, params);

        // change one one
        validatePathLength(db, """
                CALL atag.chains.update($uuidText, $uuidStart, $uuidEnd, [
                   {
                        uuid: $uuidMiddle1,
                        tagName: 'a'
                    },
                    {
                        uuid:  $uuidMiddle2,
                        tagName: 'c'
                    }
                ], $config) YIELD path
                RETURN path
                """, params, 1);
        validatePathLength(db, """
                MATCH path=(:Token{uuid:$uuidStart})-[:NEXT_TOKEN*]->(:Token{uuid:$uuidEnd})
                RETURN path
                """, params, 3, path -> {
            assertEquals("c", path.lastRelationship().getStartNode().getProperty("tagName"));
        });
    }

    @Test
    public void testUpdateDelete(GraphDatabaseService db, Neo4j neo4j) {
        System.out.println(neo4j.boltURI());
        String uuidText = "uuidText";
        String uuidStart = "uuidStart";
        String uuidEnd = "uuidEnd";
        String uuidMiddle1 = "uuidMiddle1";
        String uuidMiddle2 = "uuidMiddle2";
        Map<String, Object> params = Map.of("uuidText", uuidText, "uuidStart", uuidStart, "uuidEnd", uuidEnd,
                "uuidMiddle1", uuidMiddle1, "uuidMiddle2", uuidMiddle2, "config", configuration);

        // fixture
        db.executeTransactionally("""
                CREATE (t:Text{uuid:$uuidText})
                CREATE (s:Token{uuid:$uuidStart})
                CREATE (m1:Token{uuid:$uuidMiddle1, tagName:'a'})
                CREATE (m2:Token{uuid:$uuidMiddle2, tagName:'b'})
                CREATE (e:Token{uuid:$uuidEnd})
                CREATE (t)-[:NEXT_TOKEN]->(s)
                CREATE (s)-[:NEXT_TOKEN]->(m1)
                CREATE (m1)-[:NEXT_TOKEN]->(m2)
                CREATE (m2)-[:NEXT_TOKEN]->(e)
                """, params);

        // change one one
        validatePathLength(db, """
                CALL atag.chains.update($uuidText, $uuidStart, $uuidEnd, [
                   {
                        uuid: $uuidMiddle1,
                        tagName: 'a'
                    }
                ], $config) YIELD path
                RETURN path
                """, params, 0);
        validatePathLength(db, """
                MATCH path=(:Token{uuid:$uuidStart})-[:NEXT_TOKEN*]->(:Token{uuid:$uuidEnd})
                RETURN path
                """, params, 2);
    }
}
