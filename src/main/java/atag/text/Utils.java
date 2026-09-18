package atag.text;

import net.sf.saxon.lib.StandardErrorListener;
import net.sf.saxon.s9api.*;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;

import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {

    private static Processor processor = new Processor(false);
    private static XsltCompiler compiler = processor.newXsltCompiler();
    private static Map<String, XsltExecutable> xsltCache = new HashMap<>();
    private static XPathCompiler xpathCompiler = processor.newXPathCompiler();

    @Context
    public org.neo4j.logging.Log log;

    /**
     * load the contents of an URI
     * @param uri
     * @return the contents of the given URI
     */
    @UserFunction
    public String load(@Name("uri") String uri) {
        try {
            if (uri.startsWith("file://")) {
                uri = uri.substring(7); // remove "file://" prefix
            } else if (!uri.startsWith("http://") && !uri.startsWith("https://")) {
                throw new IllegalArgumentException("URI must start with 'http://' or 'https://'");
            }

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return switch (response.statusCode()) {
                case HttpURLConnection.HTTP_OK -> response.body();
                case HttpURLConnection.HTTP_NOT_FOUND ->
                        throw new IllegalArgumentException(String.format("could not find resource %s", uri));
                default -> throw new RuntimeException("Unexpected status code: " + response.statusCode());
            };
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @UserFunction
    public String xslt(@Name("xml") String xml, @Name("xslt") String xslt) {
        try {
            XsltExecutable stylesheet = xsltCache.computeIfAbsent(xslt, s -> {
                try {
                    return compiler.compile(new StreamSource(new StringReader(xslt)));
                } catch (SaxonApiException e) {
                    throw new RuntimeException(e);
                }
            });

            XdmNode source = processor.newDocumentBuilder().build(new StreamSource(new StringReader(xml)));

            // Create a XsltTransformer
            XsltTransformer transformer = stylesheet.load();

            StandardErrorListener listener = new StandardErrorListener();
            listener.setLogger(new Neo4jLoggerBridge(log));
            transformer.setErrorListener(listener);

            // Set the root node of the source document to be the initial context node
            transformer.setInitialContextNode(source);

            // Create a Serializer
            Serializer out = processor.newSerializer();
            out.setOutputProperty(Serializer.Property.METHOD, "xml");
            out.setOutputProperty(Serializer.Property.INDENT, "yes");
            StringWriter writer = new StringWriter();
            out.setOutputWriter(writer);

            // Transform the source XML to System.out.
            transformer.setDestination(out);
            transformer.transform();

            // Print the transformed result
            return writer.toString();
        } catch (SaxonApiException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Evaluate an XPath 3.1 expression against an XML string. Matched elements are
     * returned serialized, so a subtree can be cut out of a document and stored on its
     * own; attributes, text nodes and atomic values are returned as their string value.
     * The {@code *:name} wildcard matches an element in any namespace.
     */
    @UserFunction
    public List<String> xpath(@Name("xml") String xml, @Name("xpath") String xpath) {
        try {
            XdmNode document = processor.newDocumentBuilder().build(new StreamSource(new StringReader(xml)));
            XPathSelector selector = xpathCompiler.compile(xpath).load();
            selector.setContextItem(document);

            List<String> result = new ArrayList<>();
            for (XdmItem item : selector.evaluate()) {
                result.add(isSubtree(item) ? serialize((XdmNode) item) : item.getStringValue());
            }
            return result;
        } catch (SaxonApiException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isSubtree(XdmItem item) {
        return item instanceof XdmNode node
                && (node.getNodeKind() == XdmNodeKind.ELEMENT || node.getNodeKind() == XdmNodeKind.DOCUMENT);
    }

    /** Serialized as it is: no declaration, and no indentation that would shift text offsets. */
    private static String serialize(XdmNode node) throws SaxonApiException {
        StringWriter writer = new StringWriter();
        Serializer serializer = processor.newSerializer(writer);
        serializer.setOutputProperty(Serializer.Property.METHOD, "xml");
        serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
        serializer.setOutputProperty(Serializer.Property.INDENT, "no");
        serializer.serializeNode(node);
        return writer.toString();
    }
}
