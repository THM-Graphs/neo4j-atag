package atag.text.pipeline;

import atag.model.Ramen.Concept;
import atag.profile.ImportProfile;
import atag.text.pipeline.MappedStructure.MappedAnnotation;
import atag.text.pipeline.MappedStructure.MappedEntity;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 4 of the import pipeline: write the mapped structures to the graph. The content
 * node receives the plain text every range refers to, each annotation becomes a node
 * attached to the content node - or to the annotation it targets - and every entity
 * reference becomes a relationship to an entity node.
 */
public class GraphConstructor {

    private final Log log;

    public GraphConstructor(Log log) {
        this.log = log;
    }

    public List<Node> construct(Transaction tx, Node contentNode, MappedStructure structure, ImportProfile profile) {
        contentNode.setProperty(profile.plainTextProperty(), structure.plainText());

        Map<String, Node> entities = entities(tx, structure.entities(), profile);
        Label label = Label.label(profile.annotationLabel());
        RelationshipType relationshipType = RelationshipType.withName(profile.relationshipType());

        List<Node> annotations = new ArrayList<>(structure.annotations().size());
        Map<String, Node> byId = new HashMap<>();
        for (MappedAnnotation annotation : structure.annotations()) {
            Node node = tx.createNode(label);
            annotation.properties().forEach(node::setProperty);
            if (profile.addUuid() && !node.hasProperty(profile.idProperty())) {
                node.setProperty(profile.idProperty(), UUID.randomUUID().toString());
            }
            annotations.add(node);
            if (annotation.id() != null) {
                byId.put(annotation.id(), node);
            }
        }

        // wiring happens in a second pass, because an annotation may target another one
        // that is only created later in the document
        for (int i = 0; i < structure.annotations().size(); i++) {
            MappedAnnotation annotation = structure.annotations().get(i);
            Node node = annotations.get(i);
            anchorOf(annotation, byId, contentNode).createRelationshipTo(node, relationshipType);
            for (String reference : annotation.references()) {
                Node entity = entities.computeIfAbsent(reference, id -> find(tx, id, profile));
                if (entity == null) {
                    log.info("no entity with {} = {} found, reference not created", profile.entityKey(), reference);
                } else {
                    node.createRelationshipTo(entity, profile.model().refersTo());
                }
            }
        }
        return annotations;
    }

    private Node anchorOf(MappedAnnotation annotation, Map<String, Node> byId, Node contentNode) {
        if (annotation.parentId() == null) {
            return contentNode;
        }
        Node parent = byId.get(annotation.parentId());
        if (parent == null) {
            throw new IllegalArgumentException("annotation targets unknown annotation: " + annotation.parentId());
        }
        return parent;
    }

    private Map<String, Node> entities(Transaction tx, List<MappedEntity> declarations, ImportProfile profile) {
        Map<String, Node> result = new LinkedHashMap<>();
        for (MappedEntity entity : declarations) {
            Node existing = find(tx, entity.id(), profile);
            if (existing == null && !profile.createMissingEntities()) {
                log.info("no entity with {} = {} found, declaration ignored", profile.entityKey(), entity.id());
                continue;
            }
            Node node = existing != null ? existing : create(tx, entity);
            result.put(entity.id(), node);
        }
        return result;
    }

    private Node create(Transaction tx, MappedEntity entity) {
        Node node = tx.createNode();
        entity.labels().forEach(label -> node.addLabel(Label.label(label)));
        entity.properties().forEach(node::setProperty);
        return node;
    }

    private Node find(Transaction tx, String id, ImportProfile profile) {
        return id == null ? null : tx.findNode(profile.model().primaryLabel(Concept.ENTITY), profile.entityKey(), id);
    }
}
