package atag.text.pipeline;

import atag.model.Ramen.Concept;
import atag.profile.ImportProfile;
import atag.text.XPathSyntax;
import atag.text.XmlFragments;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The phase that turns one source document into the collections and content nodes it
 * describes, before their texts are imported.
 * <p>
 * The profile's {@code documents} section lists the levels of the hierarchy, outermost
 * first. Each level selects its parts within the fragment of the level above - the
 * expression is absolute within that fragment, exactly as it would be if the fragment
 * were handed to {@code atag.text.xpath} - and says whether they are collections or
 * content. Content levels are leaves: their fragment goes through the text import, so
 * that a corpus enters the graph as collections, contents, annotations and entities in
 * one call rather than as hand-written Cypher.
 */
public class DocumentBuilder {

    private final Log log;
    private final XmlSourceReader reader = new XmlSourceReader();
    private final XPath xPath = XPathFactory.newInstance().newXPath();

    public DocumentBuilder(Log log) {
        this.log = log;
    }

    public List<Node> build(Transaction tx, String source, ImportProfile profile) {
        List<Map<String, Object>> levels = profile.documents();
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("the profile has no documents section to build a hierarchy from");
        }
        // validates the document element once; the fragments cut out of it are not re-checked
        reader.read(source, profile);

        List<Node> created = new ArrayList<>();
        level(tx, 0, source, null, profile, created);
        return created;
    }

    private void level(Transaction tx, int index, String fragment, Node parent,
                       ImportProfile profile, List<Node> created) {
        List<Map<String, Object>> levels = profile.documents();
        Map<String, Object> level = levels.get(index);
        ImportProfile levelProfile = profile.derive(overrides(level), tx);
        Concept concept = conceptOf(level);

        for (Element element : select(fragment, (String) level.get("xpath"))) {
            String source = XmlFragments.serialize(element);
            Node node = create(tx, element, source, level, concept, levelProfile);
            if (parent != null) {
                node.createRelationshipTo(parent, profile.model().partOf());
            }
            created.add(node);

            // the register of the outermost document is imported before any text, so that
            // the references of those texts have entities to resolve against
            if (index == 0) {
                registers(tx, source, node, profile);
            }
            if (concept == Concept.CONTENT) {
                // the fragment is a document of its own now, so its root is not the one
                // the profile expects of the whole source
                ImportProfile textProfile = levelProfile.derive(Map.of("rootElement", ""), tx);
                ImportPipeline.xml(log).run(tx, node, source, textProfile);
            }
            if (index + 1 < levels.size()) {
                level(tx, index + 1, source, node, profile, created);
            }
        }
    }

    private Node create(Transaction tx, Element element, String source, Map<String, Object> level,
                        Concept concept, ImportProfile profile) {
        Node node = tx.createNode(labelOf(level, concept, profile));
        properties(element, level, profile).forEach(node::setProperty);

        String id = (String) level.get("id");
        if (id == null && !profile.idAttribute().isEmpty()) {
            id = attribute(element, profile.idAttribute());
        }
        if (id == null && profile.addUuid()) {
            id = UUID.randomUUID().toString();
        }
        if (id != null) {
            node.setProperty(profile.idProperty(), id);
        }

        String header = header(source, profile);
        if (header != null) {
            node.setProperty(profile.headerProperty(), header);
        }
        String sourceProperty = (String) level.get("sourceProperty");
        if (sourceProperty != null) {
            node.setProperty(sourceProperty, source);
        }
        return node;
    }

    /**
     * The attributes the level keeps as properties: the ones it names, or all of them.
     * The identifier is not among them - it has a property of its own.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> properties(Element element, Map<String, Object> level, ImportProfile profile) {
        List<String> wanted = (List<String>) level.get("properties");
        Map<String, Object> result = new LinkedHashMap<>();
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            String name = attributes.item(i).getNodeName();
            if (name.equals("xmlns") || name.startsWith("xmlns:") || name.equals(profile.idAttribute())) {
                continue;
            }
            if (wanted == null || wanted.contains(name)) {
                result.put(profile.dictionary().propertyFor(name), attributes.item(i).getNodeValue());
            }
        }
        return result;
    }

    private void registers(Transaction tx, String source, Node root, ImportProfile profile) {
        for (Map<String, Object> register : profile.registers()) {
            Map<String, Object> overrides = new LinkedHashMap<>();
            overrides.put("entityXPath", register.get("xpath"));
            overrides.put("createMissingEntities", register.getOrDefault("createMissingEntities", true));
            copy(register, overrides, "labels", "entityLabels");
            copy(register, overrides, "labelXPath", "entityLabelXPath");
            copy(register, overrides, "sourceProperty", "entitySourceProperty");

            List<Node> entities = ImportPipeline.xml(log)
                    .importEntities(tx, source, profile.derive(overrides, tx));
            for (Node entity : entities) {
                relate(entity, root, profile);
            }
        }
    }

    /** An entity may be declared for several documents; it belongs to each of them once. */
    private void relate(Node entity, Node root, ImportProfile profile) {
        for (org.neo4j.graphdb.Relationship existing :
                entity.getRelationships(org.neo4j.graphdb.Direction.OUTGOING, profile.model().partOf())) {
            if (existing.getEndNode().equals(root)) {
                return;
            }
        }
        entity.createRelationshipTo(root, profile.model().partOf());
    }

    private static void copy(Map<String, Object> register, Map<String, Object> overrides, String from, String to) {
        if (register.get(from) != null) {
            overrides.put(to, register.get(from));
        }
    }

    private Map<String, Object> overrides(Map<String, Object> level) {
        Map<String, Object> overrides = new LinkedHashMap<>();
        for (String key : List.of("idAttribute", "headerXPath", "headerProperty", "xpath", "rootElement")) {
            if (!"xpath".equals(key) && level.get(key) != null) {
                overrides.put(key, level.get(key));
            }
        }
        if (level.get("textXPath") != null) {
            overrides.put("xpath", level.get("textXPath"));
        }
        return overrides;
    }

    private Label labelOf(Map<String, Object> level, Concept concept, ImportProfile profile) {
        String label = (String) level.get("label");
        if (label == null) {
            return profile.model().primaryLabel(concept);
        }
        if (!profile.model().labels(concept).contains(label)) {
            throw new IllegalArgumentException(String.format(
                    "the model does not know %s as a label for %s; its labels are %s",
                    label, concept.name().toLowerCase(Locale.ROOT), profile.model().labels(concept)));
        }
        return Label.label(label);
    }

    private static Concept conceptOf(Map<String, Object> level) {
        String concept = (String) level.getOrDefault("concept", "content");
        return switch (concept.toLowerCase(Locale.ROOT)) {
            case "collection" -> Concept.COLLECTION;
            case "content" -> Concept.CONTENT;
            default -> throw new IllegalArgumentException(
                    "a document level is a collection or a content, not " + concept);
        };
    }

    private String header(String fragment, ImportProfile profile) {
        if (profile.headerXPath().isEmpty()) {
            return null;
        }
        List<Element> headers = select(fragment, profile.headerXPath());
        return headers.isEmpty() ? null : XmlFragments.serialize(headers.get(0));
    }

    private String attribute(Element element, String name) {
        String value = element.getAttribute(name);
        return value.isEmpty() ? null : value;
    }

    /** The elements an expression selects, the fragment being a document of its own. */
    private List<Element> select(String fragment, String xpath) {
        if (xpath == null || xpath.isEmpty()) {
            throw new IllegalArgumentException("a document level needs an xpath");
        }
        Document document = reader.read(fragment, null);
        try {
            NodeList nodes = (NodeList) xPath.compile(XPathSyntax.expand(xpath))
                    .evaluate(document, XPathConstants.NODESET);
            List<Element> result = new ArrayList<>(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                if (nodes.item(i) instanceof Element element) {
                    result.add(element);
                }
            }
            return result;
        } catch (XPathExpressionException e) {
            throw new RuntimeException("invalid xpath expression: " + xpath, e);
        }
    }
}
