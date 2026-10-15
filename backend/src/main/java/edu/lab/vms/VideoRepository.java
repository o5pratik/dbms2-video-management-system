package edu.lab.vms;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import edu.lab.vms.DatabaseConfig;
import org.springframework.stereotype.Repository;

@Repository
public class VideoRepository {
    private static final String BASE = "SELECT v.video_id, v.title, v.category_id, c.category_name, v.duration, v.creator_name, v.upload_date, v.views, v.description FROM videos v INNER JOIN categories c ON c.category_id = v.category_id";
    private static final RowMapper<Video> MAPPER = (rs, rowNum) -> new Video(
        rs.getLong("video_id"), rs.getString("title"), rs.getLong("category_id"),
        rs.getString("category_name"), rs.getInt("duration"), rs.getString("creator_name"),
        LocalDate.parse(rs.getString("upload_date").substring(0, 10)), rs.getLong("views"),
        rs.getString("description") == null ? "" : rs.getString("description"));
    private final JdbcTemplate jdbc;
    private final DatabaseConfig config;
    public VideoRepository(JdbcTemplate jdbc, DatabaseConfig config) { this.jdbc = jdbc; this.config = config; }

    public List<Video> all() { return jdbc.query(BASE + " ORDER BY v.upload_date DESC, v.video_id DESC", MAPPER); }
    public Video one(long id) {
        return jdbc.query(BASE + " WHERE v.video_id = ?", MAPPER, id).stream().findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Video not found."));
    }
    public List<Video> search(String field, String query) {
        String column = switch (field) {
            case "id" -> "v.video_id";
            case "title" -> "v.title";
            case "category" -> "c.category_name";
            case "creator" -> "v.creator_name";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid search field.");
        };
        if (query == null || query.isBlank()) return all();
        if (field.equals("id")) {
            try { return jdbc.query(BASE + " WHERE v.video_id = ?", MAPPER, Long.parseLong(query.trim())); }
            catch (NumberFormatException ex) { return List.of(); }
        }
        return jdbc.query(BASE + " WHERE " + column + " LIKE ? ORDER BY v.upload_date DESC, v.video_id DESC",
            MAPPER, "%" + query.trim() + "%");
    }
    public List<Map<String,Object>> categories() {
        return jdbc.queryForList("SELECT category_id AS \"categoryId\", category_name AS \"categoryName\" FROM categories ORDER BY category_name");
    }
    public void validateCategory(long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE category_id = ?", Integer.class, id);
        if (count == null || count == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose an existing category.");
    }
    public Video insert(VideoInput input) {
        validateCategory(input.categoryId());
        if ("ORACLE".equalsIgnoreCase(config.mode())) {
            Long id = jdbc.queryForObject("SELECT videos_seq.NEXTVAL FROM dual", Long.class);
            jdbc.update("INSERT INTO videos(video_id, title, category_id, duration, creator_name, upload_date, views, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, input.title().trim(), input.categoryId(), input.duration(), input.creatorName().trim(),
                input.uploadDate().toString(), input.views(), clean(input.description()));
            return one(id);
        }
        if ("POSTGRES".equalsIgnoreCase(config.mode())) {
            Long id = jdbc.queryForObject(
                "INSERT INTO videos(title, category_id, duration, creator_name, upload_date, views, description) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING video_id",
                Long.class, input.title().trim(), input.categoryId(), input.duration(), input.creatorName().trim(),
                input.uploadDate().toString(), input.views(), clean(input.description()));
            return one(id);
        }
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO videos(title, category_id, duration, creator_name, upload_date, views, description) VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            bind(ps, input);
            return ps;
        }, holder);
        Number key = holder.getKey();
        if (key == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Video saved, but its ID could not be retrieved.");
        return one(key.longValue());
    }
    public Video update(long id, VideoInput input) {
        one(id);
        validateCategory(input.categoryId());
        jdbc.update("UPDATE videos SET title=?, category_id=?, duration=?, creator_name=?, upload_date=?, views=?, description=? WHERE video_id=?",
            input.title().trim(), input.categoryId(), input.duration(), input.creatorName().trim(), input.uploadDate().toString(), input.views(), clean(input.description()), id);
        return one(id);
    }
    public void delete(long id) {
        if (jdbc.update("DELETE FROM videos WHERE video_id = ?", id) == 0)
            throw new ApiException(HttpStatus.NOT_FOUND, "Video not found.");
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static void bind(PreparedStatement ps, VideoInput input) throws SQLException {
        ps.setString(1, input.title().trim()); ps.setLong(2, input.categoryId());
        ps.setInt(3, input.duration()); ps.setString(4, input.creatorName().trim());
        ps.setString(5, input.uploadDate().toString()); ps.setLong(6, input.views());
        ps.setString(7, clean(input.description()));
    }
}

