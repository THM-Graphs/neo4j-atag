package atag.text.pipeline;

import atag.model.Ramen.Concept;
import atag.profile.ImportProfile;
import atag.text.pipeline.MappedStructure.MappedAnnotation;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 4 of the import pipeline: write the mapped structures to the graph. The content
 * node receives the plain text every range refers to, each annotation becomes a node
 * attached to it, and every entity reference becomes a relationship to the entity it
 * resolves to.
 */
public class GraphConstructor {

    private final Log log;

    public GraphConstructor(Log log) {
        this.log = log;
    }

    public List<Node> construct(Transaction tx, Node contentNode, MappedStructure structure, ImportProfile profile) {
        contentNode.setProperty(profile.plainTextProperty(), structure.plainText());

        Label label = Label.label(profile.annotationLabel());
        RelationshipType relationshipType = RelationshipType.withName(profile.relationshipType());

        List<Node> annotations = new ArrayList<>(structure.annotations().size());
        for (MappedAnnotation annotation : structure.annotations()) {
            Node node = tx.createNode(label);
            annotation.properties().forEach(node::setProperty);
            if (profile.addUuid() && !node.hasProperty(profile.idProperty())) {
                node.setProperty(profile.idProperty(), UUID.randomUUID().toString());
            }
            contentNode.createRelationshipTo(node, relationshipType);
            reference(tx, node, annotation, profile);
            annotations.add(node);
        }
        return annotations;
    }

    private void reference(Transaction tx, Node node, MappedAnnotation annotation, ImportProfile profile) {
        for (String reference : annotation.references()) {
            Node entity = tx.findNode(profile.model().primaryLabel(Concept.ENTITY), profile.entityKey(), reference);
            if (entity == null) {
                log.info("no entity with {} = {} found, reference not created", profile.entityKey(), reference);
            } else {
                node.createRelationshipTo(entity, profile.model().refersTo());
            }
        }
    }
}
