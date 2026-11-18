package atag.text;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlFragmentsTest {

    private static final String TEI = "http://www.tei-c.org/ns/1.0";

    private static Element parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        return document.getDocumentElement();
    }

    /** Copy the fragment into a TEI document whose root already declares the default namespace. */
    private static String copiedInto(String fragment) throws XMLStreamException {
        StringWriter out = new StringWriter();
        XMLStreamWriter writer = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(out);
        writer.setDefaultNamespace(TEI);
        writer.writeStartElement(TEI, "TEI");
        writer.writeDefaultNamespace(TEI);
        XmlFragments.copy(fragment, writer, TEI, "test");
        writer.writeEndElement();
        writer.close();
        return out.toString();
    }

    @Test
    void serializesASubtreeWithoutDeclarationAndWithItsNamespace() throws Exception {
        Element root = parse("<TEI xmlns=\"" + TEI + "\"><teiHeader>\n  <title xml:id=\"t\">A &amp; B</title>\n</teiHeader></TEI>");
        Element header = (Element) root.getElementsByTagNameNS(TEI, "teiHeader").item(0);

        String serialized = XmlFragments.serialize(header);

        assertEquals("<teiHeader xmlns=\"" + TEI + "\">\n  <title xml:id=\"t\">A &amp; B</title>\n</teiHeader>", serialized);
    }

    @Test
    void copiesAFragmentIntoTheDocumentsNamespaceWithoutRepeatingTheDeclaration() throws Exception {
        String copied = copiedInto("<teiHeader xmlns=\"" + TEI + "\"><title xml:id=\"t\">A &amp; B</title>\n<!-- c --></teiHeader>");

        assertEquals("<TEI xmlns=\"" + TEI + "\"><teiHeader><title xml:id=\"t\">A &amp; B</title>\n<!-- c --></teiHeader></TEI>",
                copied);
    }

    @Test
    void adoptsAFragmentWrittenWithoutNamespace() throws Exception {
        String copied = copiedInto("<teiHeader><title>plain</title></teiHeader>");

        assertEquals("<TEI xmlns=\"" + TEI + "\"><teiHeader><title>plain</title></teiHeader></TEI>", copied);
    }

    @Test
    void keepsForeignPrefixedContent() throws Exception {
        String copied = copiedInto("<teiHeader xmlns:x=\"urn:x\"><x:note x:kind=\"k\">n</x:note></teiHeader>");

        assertEquals("<TEI xmlns=\"" + TEI + "\"><teiHeader xmlns:x=\"urn:x\"><x:note x:kind=\"k\">n</x:note></teiHeader></TEI>",
                copied);
    }

    @Test
    void namesTheOriginOfAMalformedFragment() {
        XMLStreamException exception = assertThrows(XMLStreamException.class, () -> copiedInto("<teiHeader><title></teiHeader>"));

        assertTrue(Objects.requireNonNull(exception.getMessage()).startsWith("fragment stored in test is not well-formed"),
                exception.getMessage());
    }
}
