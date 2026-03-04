import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static jdk.internal.icu.impl.Utility.escape;

class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery();

        if (method.equals("GET") && path.equals("/movies") && query == null) {
            handleGetAllMovies(ex);
        } else if (method.equals("POST") && path.equals("/movies")) {
            handlePostMovie(ex);
        } else if (method.equals("GET") && path.startsWith("/movies/") && path.length() > 8) {
            handleGetMovieById(ex, path);
        } else if (method.equals("DELETE") && path.startsWith("/movies/") && path.length() > 8) {
            handleDeleteMovie(ex, path);
        } else if (method.equals("GET") && path.equals("/movies") && query != null) {
            handleGetMoviesByYear(ex, query);
        } else {
            sendJson(ex, 405, "{\"error\":\"Метод не разрешён\"}");
        }
    }

    private void handleGetAllMovies(HttpExchange ex) throws IOException {
        List<Movie> movies = store.getAll();
        sendJson(ex, 200, moviesToJsonArray(movies));
    }

    private void handlePostMovie(HttpExchange ex) throws IOException {
        if (!isValidContentType(ex)) {
            sendJson(ex, 415, "{\"error\":\"Unsupported Media Type\"}");
            return;
        }

        try {
            String body = getRequestBody(ex);
            if (body.isEmpty()) {
                sendJson(ex, 400, "{\"error\":\"Тело запроса не может быть пустым\"}");
                return;
            }

            int titleStart = body.indexOf("\"title\":\"");
            if (titleStart == -1) {
                sendJson(ex, 400, "{\"error\":\"Некорректный JSON: отсутствует поле title\"}");
                return;
            }
            titleStart += 9;
            int titleEnd = body.indexOf("\"", titleStart);
            if (titleEnd == -1) {
                sendJson(ex, 400, "{\"error\":\"Некорректный JSON: не закрыта строка title\"}");
                return;
            }
            String title = body.substring(titleStart, titleEnd);

            int yearStart = body.indexOf("\"year\":");
            if (yearStart == -1) {
                sendJson(ex, 400, "{\"error\":\"Некорректный JSON: отсутствует поле year\"}");
                return;
            }
            yearStart += 7;
            int yearEnd = yearStart;
            while (yearEnd < body.length() && Character.isDigit(body.charAt(yearEnd))) {
                yearEnd++;
            }
            if (yearEnd == yearStart) {
                sendJson(ex, 400, "{\"error\":\"Некорректный JSON: год должен быть числом\"}");
                return;
            }
            int year = Integer.parseInt(body.substring(yearStart, yearEnd));

            String validationError = validateMovie(title, year);
            if (validationError != null) {
                sendJson(ex, 422, validationError);
                return;
            }

            Movie movie = store.add(title, year);
            sendJson(ex, 201, movieToJson(movie));
        } catch (NumberFormatException e) {
            sendJson(ex, 400, "{\"error\":\"Некорректный JSON: поле year должно быть целым числом\"}");
        } catch (Exception e) {
            sendJson(ex, 400, "{\"error\":\"Некорректный JSON\"}");
        }
    }

    private boolean isValidContentType(HttpExchange ex) {
        var headers = ex.getRequestHeaders();
        return headers.containsKey("Content-Type") &&
                headers.getFirst("Content-Type").contains("application/json");
    }

    private String validateMovie(String title, int year) {
        List<String> errors = new ArrayList<>();
        int currentYear = LocalDate.now().getYear();

        if (title == null || title.trim().isEmpty()) {
            errors.add("название не должно быть пустым");
        } else if (title.length() > 100) {
            errors.add("длина названия не должна превышать 100 символов");
        }

        if (year < 1888 || year > currentYear + 1) {
            errors.add(String.format("год должен быть между 1888 и %d", currentYear + 1));
        }

        if (!errors.isEmpty()) {
            StringBuilder errorJson = new StringBuilder("{\"error\":\"Ошибка валидации\",\"details\":[");
            for (int i = 0; i < errors.size(); i++) {
                errorJson.append("\"").append(escape(errors.get(i))).append("\"");
                if (i < errors.size() - 1) errorJson.append(",");
            }
            errorJson.append("]}");
            return errorJson.toString();
        }
        return null;
    }

    private void handleGetMovieById(HttpExchange ex, String path) throws IOException {
        try {
            long id = extractIdFromPath(path);
            Optional<Movie> movieOpt = store.getById(id);

            if (movieOpt.isPresent()) {
                Movie movie = movieOpt.get();
                sendJson(ex, 200, movieToJson(movie));
            } else {
                sendJson(ex, 404, "{\"error\":\"Фильм не найден\"}");
            }
        } catch (NumberFormatException e) {
            sendJson(ex, 400, "{\"error\":\"Некорректный ID\"}");
        }
    }

    private long extractIdFromPath(String path) {
        String idStr = path.substring(8);
        return Long.parseLong(idStr);
    }

    private void handleDeleteMovie(HttpExchange ex, String path) throws IOException {
        try {
            long id = extractIdFromPath(path);

            if (store.delete(id)) {
                sendNoContent(ex);
            } else {
                sendJson(ex, 404, "{\"error\":\"Фильм не найден\"}");
            }
        } catch (NumberFormatException e) {
            sendJson(ex, 400, "{\"error\":\"Некорректный ID\"}");
        }
    }

    private void handleGetMoviesByYear(HttpExchange ex, String query) throws IOException {
        if (query == null || !query.startsWith("year=")) {
            sendJson(ex, 400, "{\"error\":\"Некорректный параметр запроса — 'year'\"}");
            return;
        }

        try {
            int year = Integer.parseInt(query.substring(5));
            List<Movie> movies = store.getByYear(year);
            sendJson(ex, 200, moviesToJsonArray(movies));
        } catch (NumberFormatException e) {
            sendJson(ex, 400, "{\"error\":\"Некорректный параметр запроса — 'year'\"}");
        }
    }

    private String movieToJson(Movie movie) {
        return String.format("{\"id\":%d,\"title\":\"%s\",\"year\":%d}",
                movie.getId(), escape(movie.getTitle()), movie.getYear());
    }

    private String moviesToJsonArray(List<Movie> movies) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < movies.size(); i++) {
            json.append(movieToJson(movies.get(i)));
            if (i < movies.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("/", "\\/")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }
}
