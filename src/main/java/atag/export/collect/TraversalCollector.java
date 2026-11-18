package atag.export.collect;

import atag.export.Subgraph;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Schema-dependent collector: starting from a single node, follow the relationships
 * described by a {@link TraversalRules} rule set (e.g. a text node down to all of its
 * annotations) and collect every reachable node and relationship.
 */
public class TraversalCollector implements SubgraphCollector {

    private final Node startNode;
    private final TraversalRules rules;

    public TraversalCollector(Node startNode, TraversalRules rules) {
        this.startNode = startNode;
        this.rules = rules;
    }

    @Override
    public Subgraph collect() {
        Set<String> visitedNodeIds = new HashSet<>();
        Set<String> visitedRelIds = new HashSet<>();
        List<Node> nodes = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        Queue<Node> queue = new LinkedList<>();

        visitedNodeIds.add(startNode.getElementId());
        nodes.add(startNode);
        queue.add(startNode);

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            for (Relationship rel : current.getRelationships(Direction.INCOMING, rules.incomingTypes())) {
                visit(rel, current, visitedRelIds, relationships, visitedNodeIds, nodes, queue);
            }
            for (Relationship rel : current.getRelationships(Direction.OUTGOING, rules.outgoingTypes())) {
                visit(rel, current, visitedRelIds, relationships, visitedNodeIds, nodes, queue);
            }
        }
        return new Subgraph(nodes, relationships);
    }

    private void visit(Relationship rel, Node current,
                       Set<String> visitedRelIds, List<Relationship> relationships,
                       Set<String> visitedNodeIds, List<Node> nodes, Queue<Node> queue) {
        Node other = rel.getOtherNode(current);
        if (!rules.accepts(other)) {
            return;
        }
        if (visitedRelIds.add(rel.getElementId())) {
            relationships.add(rel);
        }
        if (visitedNodeIds.add(other.getElementId())) {
            nodes.add(other);
            queue.add(other);
        }
    }
}
