import java.util.*;
import java.util.stream.Collectors;

public class MoviesStore {
    private static final Map<Long, Movie> movies = new HashMap<>();
    private long nextId = 1;

    public List<Movie> getAll() {
        return new ArrayList<>(movies.values());
    }

    public List<Movie> getByYear(int year) {
        return movies.values().stream()
                .filter(m -> m.getYear() == year)
                .collect(Collectors.toList());
    }

    public Optional<Movie> getById(long id) {
        return Optional.ofNullable(movies.get(id));
    }

    public Movie add(String title, int year) {
        long currentId = nextId++;
        Movie movie = new Movie(currentId, title, year);
        movies.put(movie.getId(), movie);
        return movie;
    }

    public boolean delete(long id) {
        return movies.remove(id) != null;
    }

    public static void clear() {
        movies.clear();
    }
}
