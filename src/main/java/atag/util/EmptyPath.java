package atag.util;

import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.internal.helpers.collection.Iterables;

import java.util.Iterator;

public class EmptyPath implements Path {
    @Override
    public Node startNode() {
        return null;
    }

    @Override
    public Node endNode() {
        return null;
    }

    @Override
    public Relationship lastRelationship() {
        return null;
    }

    @Override
    public Iterable<Relationship> relationships() {
        return Iterables.empty();
    }

    @Override
    public Iterable<Relationship> reverseRelationships() {
        return Iterables.empty();
    }

    @Override
    public Iterable<Node> nodes() {
        return Iterables.empty();
    }

    @Override
    public Iterable<Node> reverseNodes() {
        return Iterables.empty();
    }

    @Override
    public int length() {
        return 0;
    }

    @Override
    public Iterator<Entity> iterator() {
        return Iterables.<Entity>empty().iterator();
    }
}
