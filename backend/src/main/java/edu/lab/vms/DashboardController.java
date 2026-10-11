package edu.lab.vms;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DashboardController {
    private final JdbcTemplate jdbc;
    private final VideoRepository videos;
    private final DataSource dataSource;
    private final DatabaseConfig config;
    public DashboardController(JdbcTemplate jdbc, VideoRepository videos, DataSource dataSource, DatabaseConfig config) {
        this.jdbc = jdbc; this.videos = videos; this.dataSource = dataSource; this.config = config;
    }
    @GetMapping("/dashboard/stats")
    public Map<String,Object> stats() {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM videos", Long.class);
        Long categories = jdbc.queryForObject("SELECT COUNT(*) FROM categories", Long.class);
        Long views = jdbc.queryForObject("SELECT SUM(views) FROM videos", Long.class);
        List<Video> all = videos.all();
        return Map.of("totalVideos", total, "totalCategories", categories, "totalViews", views == null ? 0 : views,
            "recentVideos", all.stream().limit(5).toList());
    }
    @GetMapping("/database/status")
    public Map<String,Object> status() {
        try (Connection connection = dataSource.getConnection()) {
            boolean reachable = connection.isValid(3);
            return Map.of("reachable", reachable, "mode", config.label(), "message", reachable ? "Database connected" : "Database unreachable");
        } catch (Exception ex) {
            return Map.of("reachable", false, "mode", config.label(), "message", "Database unreachable. Check configuration and driver.");
        }
    }
    @GetMapping("/reports")
    public Map<String,Object> reports(@RequestParam(required=false) String from, @RequestParam(required=false) String to) {
        String start = from == null || from.isBlank() ? "0001-01-01" : from;
        String end = to == null || to.isBlank() ? "9999-12-31" : to;
        try { java.time.LocalDate.parse(start); java.time.LocalDate.parse(end); }
        catch (Exception ex) { throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Use valid YYYY-MM-DD dates."); }
        if (start.compareTo(end) > 0) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Start date must be before end date.");
        return Map.of(
            "allVideos", videos.all(),
            "totalVideos", jdbc.queryForObject("SELECT COUNT(*) FROM videos", Long.class),
            "byCategory", jdbc.queryForList("SELECT c.category_name AS \"categoryName\", COUNT(v.video_id) AS \"videoCount\", SUM(v.views) AS \"totalViews\" FROM categories c LEFT JOIN videos v ON v.category_id=c.category_id GROUP BY c.category_id, c.category_name ORDER BY COUNT(v.video_id) DESC, c.category_name"),
            "groupedVideos", jdbc.queryForList("SELECT c.category_name AS \"categoryName\", v.video_id AS \"videoId\", v.title, v.creator_name AS \"creatorName\", v.views FROM videos v INNER JOIN categories c ON c.category_id=v.category_id ORDER BY c.category_name, v.title"),
            "mostViewed", jdbc.query("SELECT v.video_id, v.title, c.category_name, v.views FROM videos v INNER JOIN categories c ON c.category_id=v.category_id ORDER BY v.views DESC, v.video_id DESC", (rs, n) -> Map.of("videoId", rs.getLong(1), "title", rs.getString(2), "categoryName", rs.getString(3), "views", rs.getLong(4))).stream().limit(5).toList(),
            "dateRange", jdbc.queryForList("SELECT v.video_id AS \"videoId\", v.title, c.category_name AS \"categoryName\", v.upload_date AS \"uploadDate\", v.views FROM videos v INNER JOIN categories c ON c.category_id=v.category_id WHERE v.upload_date BETWEEN ? AND ? ORDER BY v.upload_date DESC", start, end)
        );
    }
}
