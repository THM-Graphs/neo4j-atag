package atag.export.format.tei;

import atag.export.format.DocumentExporter;
import atag.export.format.PropertyValues;
import atag.export.map.MappedExport;
import atag.export.map.MappedExport.MappedAnnotation;
import atag.export.map.MappedExport.MappedDocument;
import atag.export.map.MappedExport.MappedEntity;
import atag.model.Ramen.Concept;
import atag.profile.ExportProfile;
import atag.profile.StandoffVocabulary;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serializes the mapped document model as TEI/XML.
 * <p>
 * Annotations are written inline wherever the XML hierarchy permits it: an annotation
 * whose range is properly nested within the annotations already placed becomes an
 * element around that range. Everything that cannot be expressed that way - an annotation
 * overlapping another one, an annotation on an annotation, or any annotation at all when
 * the profile asks for {@code serialization: 'standoff'} - is written into
 * {@code <standOff>} as an annotation pointing back at the text with a
 * {@code string-range()} pointer. Both encodings therefore express the same graph
 * relation, which is the point of the profile-driven approach: the serialization
 * strategy is a decision of the profile, not of the data.
 */
public class TeiExporter extends DocumentExporter {

    private static final XMLOutputFactory FACTORY = XMLOutputFactory.newFactory();
    private static final String TEI_NS = "http://www.tei-c.org/ns/1.0";

    @Override
    protected Object value(MappedExport export, ExportProfile profile) {
        return serialize(export, profile);
    }

    @Override
    protected String serialize(MappedExport export, ExportProfile profile) {
        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter writer = FACTORY.createXMLStreamWriter(out);
            new Serialization(writer, profile).write(export);
            writer.close();
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
        return out.toString();
    }

    /** An annotation that has to be written as stand-off markup, and what it points at. */
    private record Deferred(MappedAnnotation annotation, String target) {
    }

    /** An annotation placed inline, together with the annotations nested inside its range. */
    private static final class Placed {
        private final MappedAnnotation annotation;
        private final long startIndex;
        private final long endIndex;
        private final List<Placed> nested = new ArrayList<>();

        Placed(MappedAnnotation annotation) {
            this.annotation = annotation;
            this.startIndex = annotation.startIndex();
            this.endIndex = annotation.endIndex();
        }

        boolean contains(Placed other) {
            return startIndex <= other.startIndex && other.endIndex <= endIndex;
        }

        boolean conflictsWith(Placed other) {
            boolean disjoint = endIndex <= other.startIndex || other.endIndex <= startIndex;
            return !disjoint && !contains(other) && !other.contains(this);
        }
    }

    private static final class Serialization {

        private final XMLStreamWriter writer;
        private final ExportProfile profile;
        private final List<Deferred> standoff = new ArrayList<>();
        private final Map<Object, String> ids = new IdentityHashMap<>();
        private final Set<String> reserved;
        private int generatedIds;

        Serialization(XMLStreamWriter writer, ExportProfile profile) {
            this.writer = writer;
            this.profile = profile;
            this.reserved = new HashSet<>(profile.textProperties());
            reserved.add("startIndex");
            reserved.add("endIndex");
            reserved.add(profile.idProperty());
            reserved.add(profile.dictionary().elementProperty());
        }

        void write(MappedExport export) throws XMLStreamException {
            writer.writeStartDocument("UTF-8", "1.0");
            writer.setDefaultNamespace(TEI_NS);
            writer.writeStartElement(TEI_NS, "TEI");
            writer.writeDefaultNamespace(TEI_NS);

            writeHeader(export.root());
            writer.writeStartElement(TEI_NS, "text");
            writer.writeStartElement(TEI_NS, "body");
            writeAnchor(export.root());
            writer.writeEndElement();
            writer.writeEndElement();
            writeStandoff(export.entities());

            writer.writeEndElement();
            writer.writeEndDocument();
        }

        private void writeHeader(MappedDocument root) throws XMLStreamException {
            Object title = root.properties().get("label");
            writer.writeStartElement(TEI_NS, "teiHeader");
            writer.writeStartElement(TEI_NS, "fileDesc");
            writer.writeStartElement(TEI_NS, "titleStmt");
            writeTextElement("title", title == null ? "ATAG export" : PropertyValues.asString(title));
            writer.writeEndElement();
            writer.writeStartElement(TEI_NS, "publicationStmt");
            writeTextElement("p", "exported from a Neo4j property graph by neo4j-atag");
            writer.writeEndElement();
            writer.writeStartElement(TEI_NS, "sourceDesc");
            writeTextElement("p", "born-digital graph data");
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndElement();
        }

        /**
         * A collection becomes a {@code <div>} holding its parts, a content node becomes
         * an {@code <ab>} holding its text with the inline markup placed over it.
         */
        private void writeAnchor(MappedDocument anchor) throws XMLStreamException {
            String id = idOf(anchor, anchor.id());
            if (anchor.concept() == Concept.CONTENT) {
                writer.writeStartElement(TEI_NS, "ab");
                writeId(id);
                writeProperties(anchor.properties());
                writeText(anchor, id);
                writer.writeEndElement();
                return;
            }

            writer.writeStartElement(TEI_NS, "div");
            writeId(id);
            writeProperties(anchor.properties());
            for (MappedAnnotation annotation : anchor.annotations()) {
                defer(annotation, "#" + id);
            }
            for (MappedDocument child : anchor.children()) {
                writeAnchor(child);
            }
            writer.writeEndElement();
        }

        private void writeText(MappedDocument anchor, String anchorId) throws XMLStreamException {
            String text = anchor.text() == null ? "" : anchor.text();
            List<Placed> roots = plan(anchor.annotations(), text.length(), anchorId);
            long position = 0;
            for (Placed placed : roots) {
                writeCharacters(text, position, placed.startIndex);
                writeInline(placed, text);
                position = placed.endIndex;
            }
            writeCharacters(text, position, text.length());
        }

        /**
         * Decide which annotations can be written inline and defer the rest. Candidates are
         * visited from the longest range at the earliest position, so that outer structures
         * are placed before the ones they contain.
         */
        private List<Placed> plan(List<MappedAnnotation> annotations, int textLength, String anchorId) {
            List<MappedAnnotation> candidates = new ArrayList<>(annotations);
            candidates.sort((left, right) -> {
                int byStart = Long.compare(order(left.startIndex()), order(right.startIndex()));
                return byStart != 0 ? byStart : Long.compare(order(right.endIndex()), order(left.endIndex()));
            });

            List<Placed> accepted = new ArrayList<>();
            for (MappedAnnotation annotation : candidates) {
                if (!inlinable(annotation, textLength)) {
                    defer(annotation, pointerTo(annotation, anchorId));
                    continue;
                }
                Placed placed = new Placed(annotation);
                if (accepted.stream().anyMatch(placed::conflictsWith)) {
                    defer(annotation, pointerTo(annotation, anchorId));
                } else {
                    accepted.add(placed);
                }
            }
            return forest(accepted);
        }

        private boolean inlinable(MappedAnnotation annotation, int textLength) {
            return profile.serialization() == ExportProfile.Serialization.INLINE
                    && annotation.startIndex() != null
                    && annotation.endIndex() != null
                    && annotation.startIndex() >= 0
                    && annotation.endIndex() >= annotation.startIndex()
                    && annotation.endIndex() <= textLength;
        }

        /** Nest the accepted annotations, which are already ordered outermost first. */
        private List<Placed> forest(List<Placed> accepted) {
            List<Placed> roots = new ArrayList<>();
            Deque<Placed> open = new ArrayDeque<>();
            for (Placed placed : accepted) {
                while (!open.isEmpty() && !open.peek().contains(placed)) {
                    open.pop();
                }
                if (open.isEmpty()) {
                    roots.add(placed);
                } else {
                    open.peek().nested.add(placed);
                }
                open.push(placed);
            }
            return roots;
        }

        private void writeInline(Placed placed, String text) throws XMLStreamException {
            MappedAnnotation annotation = placed.annotation;
            String name = elementName(annotation);
            String id = idOf(annotation, annotation.id());

            if (placed.nested.isEmpty() && placed.startIndex == placed.endIndex) {
                writer.writeEmptyElement(TEI_NS, name);
                writeAnnotationAttributes(annotation, id, true);
            } else {
                writer.writeStartElement(TEI_NS, name);
                writeAnnotationAttributes(annotation, id, true);
                long position = placed.startIndex;
                for (Placed nested : placed.nested) {
                    writeCharacters(text, position, nested.startIndex);
                    writeInline(nested, text);
                    position = nested.endIndex;
                }
                writeCharacters(text, position, placed.endIndex);
                writer.writeEndElement();
            }
            deferChildren(annotation, id);
        }

        /**
         * An annotation on an annotation has no range of its own; it is always written as
         * stand-off markup pointing at the annotation it belongs to.
         */
        private void deferChildren(MappedAnnotation annotation, String id) {
            for (MappedAnnotation child : annotation.children()) {
                defer(child, "#" + id);
            }
        }

        private void defer(MappedAnnotation annotation, String target) {
            standoff.add(new Deferred(annotation, target));
            deferChildren(annotation, idOf(annotation, annotation.id()));
        }

        private String pointerTo(MappedAnnotation annotation, String anchorId) {
            return annotation.startIndex() == null
                    ? "#" + anchorId
                    : StandoffVocabulary.stringRange(anchorId, annotation.startIndex(), annotation.endIndex());
        }

        private void writeStandoff(List<MappedEntity> entities) throws XMLStreamException {
            List<MappedEntity> identified = entities.stream().filter(entity -> entity.id() != null).toList();
            if (standoff.isEmpty() && identified.isEmpty()) {
                return;
            }
            writer.writeStartElement(TEI_NS, StandoffVocabulary.STAND_OFF);
            if (!standoff.isEmpty()) {
                writer.writeStartElement(TEI_NS, StandoffVocabulary.LIST_ANNOTATION);
                for (Deferred deferred : standoff) {
                    writer.writeEmptyElement(TEI_NS, StandoffVocabulary.ANNOTATION);
                    writer.writeAttribute(StandoffVocabulary.TARGET_ATTRIBUTE, deferred.target());
                    writeAnnotationAttributes(deferred.annotation(),
                            idOf(deferred.annotation(), deferred.annotation().id()), false);
                }
                writer.writeEndElement();
            }
            if (!identified.isEmpty()) {
                writer.writeStartElement(TEI_NS, StandoffVocabulary.ENTITY_LIST);
                writer.writeAttribute(StandoffVocabulary.TYPE_ATTRIBUTE, StandoffVocabulary.ENTITY_LIST_TYPE);
                for (MappedEntity entity : identified) {
                    writeEntity(entity);
                }
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }

        private void writeEntity(MappedEntity entity) throws XMLStreamException {
            writer.writeEmptyElement(TEI_NS, StandoffVocabulary.ENTITY_ITEM);
            writeId(entity.id());
            if (entity.label() != null) {
                writer.writeAttribute(StandoffVocabulary.NAME_ATTRIBUTE, entity.label());
            }
            if (!entity.labels().isEmpty()) {
                writer.writeAttribute(StandoffVocabulary.TYPE_ATTRIBUTE, String.join(",", entity.labels()));
            }
            for (Map.Entry<String, Object> property : entity.properties().entrySet()) {
                if (!property.getKey().equals(profile.entityKey()) && !property.getKey().equals("label")) {
                    writeProperty(property.getKey(), property.getValue());
                }
            }
        }

        /**
         * @param inline whether the annotation is written as an element of its own - only
         *               then can the element name carry the annotation's type
         */
        private void writeAnnotationAttributes(MappedAnnotation annotation, String id, boolean inline)
                throws XMLStreamException {
            writeId(id);
            if (!annotation.references().isEmpty()) {
                writer.writeAttribute("ref", annotation.references().stream()
                        .map(reference -> "#" + reference)
                        .reduce((left, right) -> left + " " + right).orElseThrow());
            }
            String elementSource = inline ? typeUsedAsElement(annotation) : null;
            for (Map.Entry<String, Object> property : annotation.properties().entrySet()) {
                if (!property.getKey().equals(elementSource)) {
                    writeProperty(property.getKey(), property.getValue());
                }
            }
        }

        /**
         * The type property is not written as an attribute when the dictionary already
         * expressed it as the element name.
         */
        private String typeUsedAsElement(MappedAnnotation annotation) {
            String typeProperty = profile.dictionary().typeProperty();
            Object type = annotation.properties().get(typeProperty);
            return type != null && profile.dictionary().elementFor(type.toString()) != null ? typeProperty : null;
        }

        private String elementName(MappedAnnotation annotation) {
            return annotation.element() != null && isName(annotation.element())
                    ? annotation.element()
                    : profile.dictionary().defaultElement();
        }

        private void writeProperties(Map<String, Object> properties) throws XMLStreamException {
            for (Map.Entry<String, Object> property : properties.entrySet()) {
                writeProperty(property.getKey(), property.getValue());
            }
        }

        private void writeProperty(String key, Object value) throws XMLStreamException {
            if (!reserved.contains(key) && isName(key)) {
                writer.writeAttribute(key, PropertyValues.asString(value));
            }
        }

        private void writeId(String id) throws XMLStreamException {
            if (id != null) {
                writer.writeAttribute("xml", XMLConstants.XML_NS_URI, "id", id);
            }
        }

        private void writeTextElement(String name, String text) throws XMLStreamException {
            writer.writeStartElement(TEI_NS, name);
            writer.writeCharacters(text);
            writer.writeEndElement();
        }

        private void writeCharacters(String text, long from, long to) throws XMLStreamException {
            if (to > from) {
                writer.writeCharacters(text.substring((int) from, (int) to));
            }
        }

        /** Identifiers have to be stable within a document, and unique across it. */
        private String idOf(Object owner, String id) {
            if (id != null) {
                return id;
            }
            return ids.computeIfAbsent(owner, key -> "atag-" + (++generatedIds));
        }

        private static long order(Long value) {
            return value == null ? Long.MAX_VALUE : value;
        }

        /** Property keys that are not valid XML names cannot become attributes. */
        private static boolean isName(String value) {
            if (value.isEmpty() || !Character.isLetter(value.charAt(0)) && value.charAt(0) != '_') {
                return false;
            }
            return value.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.');
        }
    }
}
