package atag.text;

import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Load {

    /**
     * load the contents of an URI
     * @param uri
     * @return the contents of the given URI
     */
    @UserFunction
    public String load(@Name("uri") String uri) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
