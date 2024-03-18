package atag.atag.util;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import org.junit.jupiter.api.extension.*;

import java.io.IOException;
import java.net.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HttpServerExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    public static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(HttpServerExtension.class);
    private HttpServer server;

    public static class HttpServerInfo {
        private final InetSocketAddress address;

        public HttpServerInfo(InetSocketAddress address) {
            this.address = address;
        }

        public InetSocketAddress getAddress() {
            return address;
        }

        public URI getURI() {
            try {
                return new URI("http", null, address.getHostString(), address.getPort(), null, null, null);
            } catch (URISyntaxException e) {
                throw new RuntimeException("Failed to build URI from InetSocketAddress", e);
            }
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws IOException {
        int port =  findAvailablePort();
        server = SimpleFileServer.createFileServer(new InetSocketAddress(port),
                FileSystems.getDefault().getPath("src/test/resources").toAbsolutePath(),
                SimpleFileServer.OutputLevel.VERBOSE
        );
        server.start();
        context.getStore(NAMESPACE).put("httpServerInfo", new HttpServerInfo(server.getAddress()));
    }

    private int findAvailablePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Path getPathFromResource(String resourceName) {
        try {
            URL resourceUrl = getClass().getResource(resourceName);
            if (resourceUrl != null) {
                URI resourceUri = resourceUrl.toURI();
                return Paths.get(resourceUri);
            } else {
                throw new IllegalArgumentException("Resource not found: " + resourceName);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to get path from resource: " + resourceName, e);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        server.stop(0);
    }

    public HttpServer getServer() {
        return server;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(HttpServerInfo.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> paramType = parameterContext.getParameter().getType();
        if (paramType.equals(HttpServerInfo.class)) {
            return extensionContext.getStore(NAMESPACE).get("httpServerInfo");
        } else {
            throw new ParameterResolutionException("Unsupported parameter type: " + paramType);
        }
    }
}