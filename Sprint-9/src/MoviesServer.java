import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;

class MoviesServer {
    private final HttpServer server;
    private final MoviesStore store;

    public MoviesServer(MoviesStore store, int port) {
        this.store = store;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/movies", new MoviesHandler(store));
            server.setExecutor(null);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать HTTP-сервер", e);
        }
    }

    public MoviesServer() {
        this(new MoviesStore(), 8080);
    }

    public void start() {
        server.start();
        System.out.println("Сервер запущен");
    }

    public void stop() {
        server.stop(0);
        System.out.println("Сервер остановлен");
    }

    public MoviesStore getStore() {
        return store;
    }

    private int getPort() {
        return server.getAddress().getPort();
    }
}

