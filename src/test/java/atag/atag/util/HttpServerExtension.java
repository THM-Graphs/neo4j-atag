package atag.atag.util;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import org.junit.jupiter.api.extension.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;

public class HttpServerExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    public static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(HttpServerExtension.class);
    public static final String HTTP_SERVER_INFO = "httpServerInfo";
    private HttpServer server;

    /**
     * used for parameter resolution
     */
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
        int port = findAvailablePort();
        server = SimpleFileServer.createFileServer(new InetSocketAddress(port),
                FileSystems.getDefault().getPath("src/test/resources").toAbsolutePath(),
                SimpleFileServer.OutputLevel.INFO
        );
        server.start();
        context.getStore(NAMESPACE).put(HTTP_SERVER_INFO, new HttpServerInfo(server.getAddress()));
    }

    private int findAvailablePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        server.stop(0);
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