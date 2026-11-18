package atag.text;

import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * XML fragments that travel through the graph verbatim: a subtree of a source document is
 * serialized into a string property on import, and copied back into a document on export.
 * <p>
 * The fragment is copied event by event, so it can only be re-emitted as well-formed XML,
 * and an unprefixed element is written into the namespace of the document it lands in
 * rather than repeating the declaration it was cut out with.
 */
public final class XmlFragments {

    // the JDK's own implementations - JAXP would otherwise pick the shaded Saxon, whose
    // serialization differs in detail from the one this class is tested against
    private static final TransformerFactory TRANSFORMERS = TransformerFactory.newDefaultInstance();
    private static final XMLInputFactory READERS = XMLInputFactory.newDefaultFactory();

    static {
        READERS.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        READERS.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        READERS.setProperty(XMLInputFactory.IS_COALESCING, true);
    }

    private XmlFragments() {
    }

    /** Serialize a subtree without XML declaration and without touching its whitespace. */
    public static String serialize(Node node) {
        try {
            Transformer transformer = TRANSFORMERS.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(node), new StreamResult(writer));
            return writer.toString();
        } catch (TransformerException e) {
            throw new IllegalStateException("cannot serialize <" + node.getNodeName() + ">", e);
        }
    }

    /**
     * Copy a stored fragment into a document that is being written.
     *
     * @param defaultNamespace namespace unprefixed elements of the fragment are written into
     * @param origin           where the fragment came from, for the error message
     */
    public static void copy(String fragment, XMLStreamWriter writer, String defaultNamespace, String origin)
            throws XMLStreamException {
        XMLStreamReader reader;
        try {
            reader = READERS.createXMLStreamReader(new StringReader(fragment));
        } catch (XMLStreamException e) {
            throw new XMLStreamException("fragment stored in " + origin + " is not well-formed: " + e.getMessage(), e);
        }
        try {
            while (reader.hasNext()) {
                copyEvent(reader, writer, defaultNamespace);
                reader.next();
            }
        } catch (XMLStreamException e) {
            throw new XMLStreamException("fragment stored in " + origin + " is not well-formed: " + e.getMessage(), e);
        } finally {
            reader.close();
        }
    }

    private static void copyEvent(XMLStreamReader reader, XMLStreamWriter writer, String defaultNamespace)
            throws XMLStreamException {
        switch (reader.getEventType()) {
            case XMLStreamConstants.START_ELEMENT -> copyStartElement(reader, writer, defaultNamespace);
            case XMLStreamConstants.END_ELEMENT -> writer.writeEndElement();
            case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE ->
                    writer.writeCharacters(reader.getText());
            case XMLStreamConstants.COMMENT -> writer.writeComment(reader.getText());
            case XMLStreamConstants.PROCESSING_INSTRUCTION ->
                    writer.writeProcessingInstruction(reader.getPITarget(), reader.getPIData());
            default -> {
                // document boundaries and the like have nothing to contribute
            }
        }
    }

    private static void copyStartElement(XMLStreamReader reader, XMLStreamWriter writer, String defaultNamespace)
            throws XMLStreamException {
        String prefix = reader.getPrefix();
        if (prefix == null || prefix.isEmpty()) {
            writer.writeStartElement(defaultNamespace, reader.getLocalName());
        } else {
            writer.writeStartElement(prefix, reader.getLocalName(), reader.getNamespaceURI());
        }
        for (int i = 0; i < reader.getNamespaceCount(); i++) {
            String declared = reader.getNamespacePrefix(i);
            if (declared != null && !declared.isEmpty()) {
                writer.writeNamespace(declared, reader.getNamespaceURI(i));
            }
        }
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String namespace = reader.getAttributeNamespace(i);
            String local = reader.getAttributeLocalName(i);
            String value = reader.getAttributeValue(i);
            if (namespace == null || namespace.isEmpty()) {
                writer.writeAttribute(local, value);
            } else if (XMLConstants.XML_NS_URI.equals(namespace)) {
                writer.writeAttribute("xml", XMLConstants.XML_NS_URI, local, value);
            } else {
                writer.writeAttribute(reader.getAttributePrefix(i), namespace, local, value);
            }
        }
    }
}
