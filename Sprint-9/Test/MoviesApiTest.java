import org.junit.jupiter.api.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static final int PORT = 8080;
    private static MoviesServer server;
    private static HttpClient client;

    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer();
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    void setUp() {
        MoviesStore.clear();
    }

    private Movie addMovie(String title, int year) throws Exception {
        String json = String.format("{\"title\":\"%s\",\"year\":%d}", title, year);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode());

        String body = response.body();
        int idStart = body.indexOf("\"id\":") + 5;
        int idEnd = body.indexOf(",", idStart);
        if (idEnd == -1) idEnd = body.indexOf("}", idStart);
        long id = Long.parseLong(body.substring(idStart, idEnd));

        return new Movie(id, title, year);
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    void testGetAllMovies_EmptyList() throws Exception {
        server.getStore().clear();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Статус ответа должен быть 200 OK");
        assertEquals("[]", response.body(), "Ответ должен содержать пустой JSON‑массив");
        assertTrue(response.headers().firstValue("Content-Type")
                        .orElse("").contains("application/json; charset=UTF-8"),
                "Заголовок Content-Type должен указывать на JSON с кодировкой UTF-8");
    }

    @Test
    void testGetAllMovies_WithMovies() throws Exception {
        addMovie("Интерстеллар", 2014);
        addMovie("Матрица", 1999);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"title\":\"Интерстеллар\""));
        assertTrue(response.body().contains("\"title\":\"Матрица\""));
        assertTrue(response.headers().firstValue("Content-Type")
                .orElse("").contains("application/json; charset=UTF-8"));
    }

    @Test
    void testGetMovieById_Found() throws Exception {
        Movie movie = addMovie("Матрица", 1999);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + movie.getId()))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(String.format("\"title\":\"%s\"", movie.getTitle())));
        assertTrue(response.body().contains(String.format("\"year\":%d", movie.getYear())));
        assertTrue(response.body().contains(String.format("\"id\":%d", movie.getId())));
    }

    @Test
    void testGetMovieById_NotFound() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/9999"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("Фильм не найден"));
    }

    @Test
    void testPostMovie_Success() throws Exception {
        String json = "{\"title\":\"Начало\",\"year\":2010}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("\"title\":\"Начало\""));
        assertTrue(response.body().contains("\"year\":2010"));
        assertTrue(response.body().contains("\"id\":"));
        assertTrue(response.headers().firstValue("Content-Type")
                .orElse("").contains("application/json; charset=UTF-8"));
    }

    @Test
    void testPostMovie_EmptyTitle() throws Exception {
        String json = "{\"title\":\"\",\"year\":2010}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("\"error\":"));
    }

    @Test
    void testPostMovie_LongTitle() throws Exception {
        String longTitle = "A".repeat(101);
        String json = String.format("{\"title\":\"%s\",\"year\":2010}", longTitle);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("\"error\":"));
    }

    @Test
    void testPostMovie_InvalidYear_TooOld() throws Exception {
        String json = "{\"title\":\"Фильм\",\"year\":1800}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("\"error\":"));
    }

    @Test
    void testPostMovie_InvalidYear_Future() throws Exception {
        int futureYear = LocalDate.now().getYear() + 2;
        String json = String.format("{\"title\":\"Фильм\",\"year\":%d}", futureYear);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("\"error\":"));
    }

    @Test
    void testPostMovie_WrongContentType() throws Exception {
        String json = "{\"title\":\"Начало\",\"year\":2010}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(415, response.statusCode());
        assertTrue(response.body().contains("\"error\":"));
    }

    @Test
    void testPostMovie_InvalidJson() throws Exception {
        String invalidJson = "{\"title\":\"Начало\", \"year\":}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertTrue(List.of(400, 422).contains(response.statusCode()));
        assertTrue(response.body().contains("\"error\":"));
    }

    @Test
    void testGetMovieById_InvalidId() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"error\":"));
    }

    @Test
    void testDeleteMovie_Found() throws Exception {
        Movie movie = addMovie("Удали меня", 2020);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + movie.getId()))
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(204, response.statusCode());
    }

    @Test
    void testDeleteMovie_NotFound() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/9999"))
                .DELETE()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("\"error\":\"Фильм не найден\""));
    }

    @Test
    void testDeleteMovie_InvalidId() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/xyz"))
                .DELETE()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"error\":"));
    }

    @Test
    void testGetMoviesByYear_ValidYear() throws Exception {
        addMovie("Интерстеллар", 2014);
        addMovie("Начало", 2010);
        addMovie("Дюна", 2021);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2014"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"title\":\"Интерстеллар\""));
        assertFalse(response.body().contains("\"title\":\"Начало\""));
    }

    @Test
    void testGetMoviesByYear_EmptyList() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=1900"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("[]", response.body());
    }

    @Test
    void testGetMoviesByYear_InvalidYearParam() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=abc"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"error\":"));
    }

    @Test
    void testUnsupportedMethod_Returns405() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode(), "Ожидался статус 405 Method Not Allowed");
        assertTrue(response.body().contains("\"error\""),
                "Ответ должен содержать поле 'error' в JSON");
    }
}
