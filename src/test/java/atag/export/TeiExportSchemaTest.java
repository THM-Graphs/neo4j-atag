package atag.export;

import org.junit.jupiter.api.Test;
import org.xmlunit.assertj3.XmlAssert;

import javax.xml.transform.stream.StreamSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link TeiExportTest} validates every exported document against
 * {@code tei-atag-export.xsd}. That is only worth something as long as the schema
 * actually rejects documents, so these cases pin down what the export contract forbids:
 * a pointer that is not resolvable, markup from a foreign vocabulary inside the text, and
 * a document that is not a TEI document.
 */
class TeiExportSchemaTest {

    private static final String HEADER = """
            <teiHeader><fileDesc><titleStmt><title>t</title></titleStmt>
            <publicationStmt><p>p</p></publicationStmt><sourceDesc><p>s</p></sourceDesc></fileDesc></teiHeader>
            """;

    private StreamSource schema() {
        return new StreamSource(TeiExportSchemaTest.class.getResourceAsStream("/tei-atag-export.xsd"));
    }

    private void assertRejected(String tei) {
        assertThrows(AssertionError.class, () -> XmlAssert.assertThat(tei).isValidAgainst(schema()));
    }

    @Test
    void aStandoffAnnotationWithoutAResolvableTargetIsRejected() {
        assertRejected("<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">" + HEADER + """
                <text><body><ab xml:id="x">hi</ab></body></text>
                <standOff><listAnnotation><annotation target="nonsense pointer"/></listAnnotation></standOff></TEI>
                """);
        assertRejected("<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">" + HEADER + """
                <text><body><ab xml:id="x">hi</ab></body></text>
                <standOff><listAnnotation><annotation type="phrase"/></listAnnotation></standOff></TEI>
                """);
    }

    @Test
    void markupFromAForeignVocabularyInsideTheTextIsRejected() {
        assertRejected("<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">" + HEADER + """
                <text><body><ab xml:id="x">hi <b xmlns="http://example.org">there</b></ab></body></text></TEI>
                """);
    }

    @Test
    void aDocumentWithoutATeiHeaderIsRejected() {
        assertRejected("<TEI xmlns=\"http://www.tei-c.org/ns/1.0\"><text><body><ab>hi</ab></body></text></TEI>");
    }
}
