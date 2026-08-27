package atag.text.pipeline;

import atag.profile.ImportProfile;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

/**
 * Phase 1 for XML sources. DTD declarations and entity resolution are disabled, so a
 * document cannot pull in external resources. If the profile names an expected root
 * element, a document with a different one is rejected here rather than silently
 * producing an empty import.
 */
public class XmlSourceReader implements SourceReader<Document> {

    @Override
    public Document read(String source, ImportProfile profile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setIgnoringElementContentWhitespace(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));

            Document document = builder.parse(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
            validate(document, profile);
            return document;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void validate(Document document, ImportProfile profile) {
        String expected = profile.rootElement();
        if (expected.isEmpty()) {
            return;
        }
        String actual = document.getDocumentElement().getLocalName() != null
                ? document.getDocumentElement().getLocalName()
                : document.getDocumentElement().getNodeName();
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(
                    String.format("expected root element <%s>, but found <%s>", expected, actual));
        }
    }
}
