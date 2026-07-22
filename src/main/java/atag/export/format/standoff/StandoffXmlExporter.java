package atag.export.format.standoff;

import atag.export.Subgraph;
import atag.export.format.Exporter;
import atag.export.format.PropertyValues;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.util.Map;

/**
 * Renders the {@link StandoffDocument} model as XML, demonstrating that the export
 * pipeline is not bound to JSON: each anchor becomes an element named after its label
 * (its properties are attributes), its annotations become {@code <annotation>} child
 * elements, and its nested child anchors are written recursively inside it. Nested
 * annotations become {@code <annotation>} elements inside their originating annotation.
 */
public class StandoffXmlExporter implements Exporter {

    private static final XMLOutputFactory FACTORY = XMLOutputFactory.newFactory();

    private final StandoffModelBuilder builder = new StandoffModelBuilder();

    @Override
    public String toValue(Subgraph subgraph) {
        return render(subgraph);
    }

    @Override
    public String render(Subgraph subgraph) {
        StandoffDocument document = builder.build(subgraph);
        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter writer = FACTORY.createXMLStreamWriter(out);
            writer.writeStartDocument("UTF-8", "1.0");
            writeNode(writer, document);
            writer.writeEndDocument();
            writer.close();
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
        return out.toString();
    }

    private void writeNode(XMLStreamWriter writer, StandoffDocument node) throws XMLStreamException {
        if (node.annotations().isEmpty() && node.children().isEmpty()) {
            writer.writeEmptyElement(node.name());
            writeAttributes(writer, node.properties());
            return;
        }
        writer.writeStartElement(node.name());
        writeAttributes(writer, node.properties());
        for (StandoffAnnotation annotation : node.annotations()) {
            writeAnnotation(writer, annotation);
        }
        for (StandoffDocument child : node.children()) {
            writeNode(writer, child);
        }
        writer.writeEndElement();
    }

    private void writeAnnotation(XMLStreamWriter writer, StandoffAnnotation annotation) throws XMLStreamException {
        if (annotation.children().isEmpty()) {
            writer.writeEmptyElement("annotation");
            writeAttributes(writer, annotation.properties());
            return;
        }
        writer.writeStartElement("annotation");
        writeAttributes(writer, annotation.properties());
        for (StandoffAnnotation child : annotation.children()) {
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
