package atag.export.format.standoff;

import atag.export.format.DocumentExporter;
import atag.export.format.PropertyValues;
import atag.export.map.MappedExport;
import atag.export.map.MappedExport.MappedAnnotation;
import atag.export.map.MappedExport.MappedDocument;
import atag.profile.ExportProfile;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.util.Map;

/**
 * Renders the mapped document model as generic XML, without committing to a markup
 * vocabulary: each anchor becomes an element named after its label (its properties are
 * attributes), its annotations become {@code <annotation>} child elements, and its
 * nested child anchors are written recursively inside it. Nested annotations become
 * {@code <annotation>} elements inside their originating annotation.
 */
public class StandoffXmlExporter extends DocumentExporter {

    private static final XMLOutputFactory FACTORY = XMLOutputFactory.newFactory();

    @Override
    protected String value(MappedExport export, ExportProfile profile) {
        return serialize(export, profile);
    }

    @Override
    protected String serialize(MappedExport export, ExportProfile profile) {
        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter writer = FACTORY.createXMLStreamWriter(out);
            writer.writeStartDocument("UTF-8", "1.0");
            writeNode(writer, export.root());
            writer.writeEndDocument();
            writer.close();
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
        return out.toString();
    }

    private void writeNode(XMLStreamWriter writer, MappedDocument node) throws XMLStreamException {
        if (node.annotations().isEmpty() && node.children().isEmpty()) {
            writer.writeEmptyElement(node.name());
            writeAttributes(writer, node.properties());
            return;
        }
        writer.writeStartElement(node.name());
        writeAttributes(writer, node.properties());
        for (MappedAnnotation annotation : node.annotations()) {
            writeAnnotation(writer, annotation);
        }
        for (MappedDocument child : node.children()) {
            writeNode(writer, child);
        }
        writer.writeEndElement();
    }

    private void writeAnnotation(XMLStreamWriter writer, MappedAnnotation annotation) throws XMLStreamException {
        if (annotation.children().isEmpty()) {
            writer.writeEmptyElement("annotation");
            writeAttributes(writer, annotation.properties());
            return;
        }
        writer.writeStartElement("annotation");
        writeAttributes(writer, annotation.properties());
        for (MappedAnnotation child : annotation.children()) {
            writeAnnotation(writer, child);
        }
        writer.writeEndElement();
    }

    private void writeAttributes(XMLStreamWriter writer, Map<String, Object> properties) throws XMLStreamException {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            writer.writeAttribute(entry.getKey(), PropertyValues.asString(entry.getValue()));
        }
    }
}
