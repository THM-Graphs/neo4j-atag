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
import java.util.HashMap;
import java.util.Map;

public class Utils {

    private static Processor processor = new Processor(false);
    private static XsltCompiler compiler = processor.newXsltCompiler();
    private static Map<String, XsltExecutable> xsltCache = new HashMap<>();

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
}
