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
import atag.text.XmlFragments;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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
 * <p>
 * An anchor that carries a verbatim header is written as a {@code <TEI>} of its own,
 * nested into the {@code <TEI>} of its parent - the document then has the shape of the
 * corpus it was imported from. Annotations over the same range are nested by the depth
 * the import recorded, and an identifier is only written where the source had one or
 * where a pointer needs one.
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

    /**
     * An annotation placed inline, together with the annotations nested inside its range.
     *
     * @param byDepth whether every candidate of the text carries a depth, in which case an
     *                annotation only contains another one that was nested deeper - the
     *                one thing that tells two annotations over the same range apart
     */
    private static final class Placed {
        private final MappedAnnotation annotation;
        private final long startIndex;
        private final long endIndex;
        private final Long depth;
        private final boolean byDepth;
        private final List<Placed> nested = new ArrayList<>();

        Placed(MappedAnnotation annotation, boolean byDepth) {
            this.annotation = annotation;
            this.startIndex = annotation.startIndex();
            this.endIndex = annotation.endIndex();
            this.depth = annotation.depth();
            this.byDepth = byDepth;
        }

        boolean contains(Placed other) {
            return startIndex <= other.startIndex && other.endIndex <= endIndex
                    && (!byDepth || other.depth > depth);
        }

        boolean conflictsWith(Placed other) {
            boolean disjoint = endIndex <= other.startIndex || other.endIndex <= startIndex;
            return !disjoint && !contains(other) && !other.contains(this);
        }
    }

    /**
     * Serialization runs in two passes: the first plans every text - which annotations go
     * inline, which are deferred, which identifiers pointers need - and the second writes.
     * Two passes are necessary because the deferred annotations of every nested
     * {@code <TEI>} are collected in the one {@code <standOff>} of the root, which is
     * written before the nested members.
     */
    private static final class Serialization {

        private final XMLStreamWriter writer;
        private final ExportProfile profile;
        private final List<Deferred> standoff = new ArrayList<>();
        private final Map<Object, String> ids = new IdentityHashMap<>();
        private final Map<MappedDocument, List<Placed>> plans = new IdentityHashMap<>();
        /** the {@code <ab>} inside a content node's own {@code <TEI>}, when a pointer needs it */
        private final Map<MappedDocument, String> textIds = new IdentityHashMap<>();
        private final Set<MappedDocument> teiLevel = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<String> reserved;
        private int generatedIds;

        Serialization(XMLStreamWriter writer, ExportProfile profile) {
            this.writer = writer;
            this.profile = profile;
            this.reserved = new HashSet<>(profile.textProperties());
            reserved.add("startIndex");
            reserved.add("endIndex");
            reserved.add("depth");
            reserved.add(profile.idProperty());
            reserved.add(profile.dictionary().elementProperty());
            reserved.add(profile.headerProperty());
            if (profile.entitySourceProperty() != null) {
                reserved.add(profile.entitySourceProperty());
            }
        }

        void write(MappedExport export) throws XMLStreamException {
            plan(export.root(), carriesHeader(export.root()));

            writer.writeStartDocument("UTF-8", "1.0");
            writer.setDefaultNamespace(TEI_NS);
            writer.writeStartElement(TEI_NS, "TEI");
            writer.writeDefaultNamespace(TEI_NS);
            writeTei(export.root(), true, export.entities());
            writer.writeEndElement();
            writer.writeEndDocument();
        }

        // ---- pass 1: planning

        /**
         * @param asTei whether the anchor is written as a {@code <TEI>} of its own, carrying
         *              its identifier, its properties and its header: a child is when it
         *              has a header and its parent is one; the root is when it has a header
         *              or when a child has one, so that no header is lost for lack of a
         *              place to write it
         */
        private void plan(MappedDocument anchor, boolean asTei) {
            if (asTei) {
                teiLevel.add(anchor);
            }
            if (anchor.concept() == Concept.CONTENT) {
                plans.put(anchor, planText(anchor));
                return;
            }
            for (MappedAnnotation annotation : anchor.annotations()) {
                defer(annotation, "#" + idOf(anchor, anchor.id()));
            }
            for (MappedDocument child : anchor.children()) {
                plan(child, asTei && child.header() != null);
            }
        }

        private static boolean carriesHeader(MappedDocument anchor) {
            return anchor.header() != null || anchor.children().stream().anyMatch(child -> child.header() != null);
        }

        private List<Placed> planText(MappedDocument anchor) {
            String text = anchor.text() == null ? "" : anchor.text();
            List<Placed> roots = plan(anchor.annotations(), text.length(), anchor);
            for (Placed placed : roots) {
                deferNested(placed);
            }
            return roots;
        }

        /** Annotations on inline annotations are deferred in the order the elements are written. */
        private void deferNested(Placed placed) {
            for (Placed nested : placed.nested) {
                deferNested(nested);
            }
            deferChildren(placed.annotation);
        }

        /**
         * Decide which annotations can be written inline and defer the rest. Candidates are
         * visited from the longest range at the earliest position, so that outer structures
         * are placed before the ones they contain; where the depth is known it decides
         * instead, so that two annotations over the same range keep their nesting.
         */
        private List<Placed> plan(List<MappedAnnotation> annotations, int textLength, MappedDocument anchor) {
            List<MappedAnnotation> candidates = new ArrayList<>(annotations);
            boolean byDepth = !candidates.isEmpty() && candidates.stream().allMatch(a -> a.depth() != null);
            Comparator<MappedAnnotation> byStart = Comparator.comparingLong(a -> order(a.startIndex()));
            candidates.sort(byDepth
                    ? byStart.thenComparingLong(MappedAnnotation::depth).thenComparingLong(a -> order(a.endIndex()))
                    : byStart.thenComparing(a -> order(a.endIndex()), Comparator.reverseOrder()));

            List<Placed> accepted = new ArrayList<>();
            for (MappedAnnotation annotation : candidates) {
                if (!inlinable(annotation, textLength)) {
                    defer(annotation, pointerTo(annotation, anchor));
                    continue;
                }
                Placed placed = new Placed(annotation, byDepth);
                if (accepted.stream().anyMatch(placed::conflictsWith)) {
                    defer(annotation, pointerTo(annotation, anchor));
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

        /**
         * An annotation on an annotation has no range of its own; it is always written as
         * stand-off markup pointing at the annotation it belongs to.
         */
        private void deferChildren(MappedAnnotation annotation) {
            if (annotation.children().isEmpty()) {
                return;
            }
            String id = idOf(annotation, annotation.id());
            for (MappedAnnotation child : annotation.children()) {
                defer(child, "#" + id);
            }
        }

        private void defer(MappedAnnotation annotation, String target) {
            standoff.add(new Deferred(annotation, target));
            deferChildren(annotation);
        }

        private String pointerTo(MappedAnnotation annotation, MappedDocument anchor) {
            return annotation.startIndex() == null
                    ? "#" + idOf(anchor, anchor.id())
                    : StandoffVocabulary.stringRange(textIdOf(anchor), annotation.startIndex(), annotation.endIndex());
        }

        /**
         * The identifier a range of the anchor's text is addressed by: the {@code <ab>} of
         * a content node that has its own {@code <TEI>}, otherwise the content node itself.
         */
        private String textIdOf(MappedDocument anchor) {
            if (teiLevel.contains(anchor)) {
                return textIds.computeIfAbsent(anchor, key -> generateId());
            }
            return idOf(anchor, anchor.id());
        }

        // ---- pass 2: writing

        private void writeTei(MappedDocument anchor, boolean root, List<MappedEntity> entities)
                throws XMLStreamException {
            boolean own = teiLevel.contains(anchor);
            if (!root) {
                writer.writeStartElement(TEI_NS, "TEI");
            }
            if (own) {
                writeId(assignedId(anchor));
                writeProperties(anchor.properties());
            }
            if (anchor.header() != null) {
                XmlFragments.copy(anchor.header(), writer, TEI_NS,
                        profile.headerProperty() + " of " + assignedId(anchor));
            } else {
                writeHeader(anchor);
            }

            List<MappedDocument> members = new ArrayList<>();
            List<MappedDocument> plain = new ArrayList<>();
            for (MappedDocument child : anchor.children()) {
                (teiLevel.contains(child) ? members : plain).add(child);
            }

            if (anchor.concept() == Concept.CONTENT) {
                writeTextBody(() -> writeContent(anchor, own));
            } else if (!own) {
                writeTextBody(() -> writeAnchor(anchor));
            } else if (!plain.isEmpty()) {
                writeTextBody(() -> {
                    for (MappedDocument child : plain) {
                        writeAnchor(child);
                    }
                });
            }

            if (root) {
                writeStandoff(entities);
            }
            for (MappedDocument member : members) {
                writeTei(member, false, null);
            }
            if (!root) {
                writer.writeEndElement();
            }
        }

        private interface Body {
            void write() throws XMLStreamException;
        }

        private void writeTextBody(Body body) throws XMLStreamException {
            writer.writeStartElement(TEI_NS, "text");
            writer.writeStartElement(TEI_NS, "body");
            body.write();
            writer.writeEndElement();
            writer.writeEndElement();
        }

        /** The placeholder header of a document whose root has none of its own. */
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
         * Inside a body, a collection becomes a {@code <div>} holding its parts, a content
         * node becomes an {@code <ab>} holding its text with the inline markup placed over
         * it. Parts written as a {@code <TEI>} of their own are not repeated here.
         */
        private void writeAnchor(MappedDocument anchor) throws XMLStreamException {
            if (anchor.concept() == Concept.CONTENT) {
                writeContent(anchor, false);
                return;
            }
            writer.writeStartElement(TEI_NS, "div");
            writeId(assignedId(anchor));
            writeProperties(anchor.properties());
            for (MappedDocument child : anchor.children()) {
                if (!teiLevel.contains(child)) {
                    writeAnchor(child);
                }
            }
            writer.writeEndElement();
        }

        /**
         * @param wrapped whether the content node's identity and properties already sit on
         *                its own {@code <TEI>}, leaving the {@code <ab>} to carry the text
         */
        private void writeContent(MappedDocument anchor, boolean wrapped) throws XMLStreamException {
            writer.writeStartElement(TEI_NS, "ab");
            if (wrapped) {
                writeId(textIds.get(anchor));
            } else {
                writeId(assignedId(anchor));
                writeProperties(anchor.properties());
            }
            writeText(anchor);
            writer.writeEndElement();
        }

        private void writeText(MappedDocument anchor) throws XMLStreamException {
            String text = anchor.text() == null ? "" : anchor.text();
            long position = 0;
            for (Placed placed : plans.get(anchor)) {
                writeCharacters(text, position, placed.startIndex);
                writeInline(placed, text);
                position = placed.endIndex;
            }
            writeCharacters(text, position, text.length());
        }

        private void writeInline(Placed placed, String text) throws XMLStreamException {
            MappedAnnotation annotation = placed.annotation;
            String name = elementName(annotation);
            String id = assignedId(annotation);

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
                    writeAnnotationAttributes(deferred.annotation(), assignedId(deferred.annotation()), false);
                }
                writer.writeEndElement();
            }
            writeEntities(identified);
            writer.writeEndElement();
        }

        /**
         * An entity whose declaration was kept verbatim is written back into the TEI list
         * its declaring element belongs to; the others are declared from their properties.
         */
        private void writeEntities(List<MappedEntity> entities) throws XMLStreamException {
            Map<String, List<MappedEntity>> verbatim = new LinkedHashMap<>();
            List<MappedEntity> declared = new ArrayList<>();
            for (MappedEntity entity : entities) {
                if (entity.source() != null) {
                    verbatim.computeIfAbsent(StandoffVocabulary.listFor(entity.element()), key -> new ArrayList<>())
                            .add(entity);
                } else {
                    declared.add(entity);
                }
            }
            for (String list : StandoffVocabulary.ENTITY_LIST_ORDER) {
                if (!verbatim.containsKey(list)) {
                    continue;
                }
                writer.writeStartElement(TEI_NS, list);
                for (MappedEntity entity : verbatim.get(list)) {
                    XmlFragments.copy(entity.source(), writer, TEI_NS,
                            profile.entitySourceProperty() + " of " + entity.id());
                }
                writer.writeEndElement();
            }
            if (!declared.isEmpty()) {
                writer.writeStartElement(TEI_NS, StandoffVocabulary.ENTITY_LIST);
                writer.writeAttribute(StandoffVocabulary.TYPE_ATTRIBUTE, StandoffVocabulary.ENTITY_LIST_TYPE);
                for (MappedEntity entity : declared) {
                    writeEntity(entity);
                }
                writer.writeEndElement();
            }
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
            String referenceAttribute = profile.referenceAttribute();
            List<String> pointers = new ArrayList<>();
            for (String reference : annotation.references()) {
                pointers.add("#" + reference);
            }
            // pointers the import could not resolve stayed in the attribute they were written in
            Object unresolved = annotation.properties().get(referenceAttribute);
            if (unresolved != null) {
                pointers.addAll(Arrays.asList(PropertyValues.asString(unresolved).trim().split("\\s+")));
            }
            if (!pointers.isEmpty()) {
                writer.writeAttribute(referenceAttribute, String.join(" ", pointers));
            }
            String elementSource = inline ? typeUsedAsElement(annotation) : null;
            for (Map.Entry<String, Object> property : annotation.properties().entrySet()) {
                String key = property.getKey();
                boolean taken = key.equals(elementSource) || key.equals(referenceAttribute)
                        || !inline && key.equals(StandoffVocabulary.TARGET_ATTRIBUTE);
                if (!taken) {
                    writeProperty(key, property.getValue());
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

        /**
         * Identifiers have to be stable within a document, and unique across it. One is
         * generated only while planning, that is only when a pointer needs it.
         */
        private String idOf(Object owner, String id) {
            if (id != null) {
                return id;
            }
            return ids.computeIfAbsent(owner, key -> generateId());
        }

        /** The identifier an element is written with: its own, or the one a pointer required. */
        private String assignedId(MappedDocument anchor) {
            return anchor.id() != null ? anchor.id() : ids.get(anchor);
        }

        private String assignedId(MappedAnnotation annotation) {
            return annotation.id() != null ? annotation.id() : ids.get(annotation);
        }

        private String generateId() {
            return "atag-" + (++generatedIds);
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
