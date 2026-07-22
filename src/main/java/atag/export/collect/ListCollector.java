package atag.export.collect;

import atag.export.Subgraph;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import java.util.List;

/**
 * Schema-agnostic collector: the caller has already selected the nodes and
 * relationships (typically via a Cypher {@code MATCH ... collect(...)}).
 */
public class ListCollector implements SubgraphCollector {

    private final List<Node> nodes;
    private final List<Relationship> relationships;

    public ListCollector(List<Node> nodes, List<Relationship> relationships) {
        this.nodes = nodes;
        this.relationships = relationships;
    }

    @Override
    public Subgraph collect() {
        return new Subgraph(nodes, relationships);
    }
}
