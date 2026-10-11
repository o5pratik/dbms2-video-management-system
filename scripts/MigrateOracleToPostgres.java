import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/** One-time copy of categories and videos; website accounts are deliberately not copied. */
class MigrateOracleToPostgres {
    private static Map<String, String> load(Path path) throws Exception {
        if (!Files.isRegularFile(path)) throw new IllegalStateException("Missing " + path);
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int equals = line.indexOf('=');
            if (equals > 0) values.put(line.substring(0, equals).trim(), line.substring(equals + 1).trim());
        }
        return values;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + key);
        return value;
    }

    private static long count(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();
        Map<String, String> oracleSettings = load(root.resolve(".env"));
        Map<String, String> neonSettings = load(root.resolve(".env.neon.local"));
        if (!"ORACLE".equalsIgnoreCase(required(oracleSettings, "DB_MODE")))
            throw new IllegalStateException("The local .env must point to Oracle");
        String targetUrl = required(neonSettings, "NEON_DB_URL");
        if (!targetUrl.startsWith("jdbc:postgresql:"))
            throw new IllegalStateException("NEON_DB_URL must be a PostgreSQL JDBC URL");

        Class.forName("oracle.jdbc.OracleDriver");
        Class.forName("org.postgresql.Driver");
        try (Connection source = DriverManager.getConnection(
                 required(oracleSettings, "DB_URL"), required(oracleSettings, "DB_USER"), required(oracleSettings, "DB_PASSWORD"));
             Connection target = DriverManager.getConnection(targetUrl,
                 required(neonSettings, "NEON_DB_USER"), required(neonSettings, "NEON_DB_PASSWORD"))) {
            target.setAutoCommit(false);
            try {
                String schema = Files.readString(root.resolve("backend/src/main/resources/database/schema-postgres.sql"));
                for (String command : schema.split(";")) {
                    if (!command.isBlank()) {
                        try (Statement statement = target.createStatement()) { statement.execute(command); }
                    }
                }
                if (count(target, "users") != 0 || count(target, "categories") != 0 || count(target, "videos") != 0)
                    throw new IllegalStateException("Neon tables must be empty before the one-time copy");

                try (Statement query = source.createStatement();
                     ResultSet rows = query.executeQuery("SELECT category_id, category_name FROM categories ORDER BY category_id");
                     PreparedStatement insert = target.prepareStatement(
                         "INSERT INTO categories(category_id, category_name) VALUES (?, ?)")) {
                    while (rows.next()) {
                        insert.setLong(1, rows.getLong(1));
                        insert.setString(2, rows.getString(2));
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                try (Statement query = source.createStatement();
                     ResultSet rows = query.executeQuery("SELECT video_id, title, category_id, duration, creator_name, upload_date, views, description FROM videos ORDER BY video_id");
                     PreparedStatement insert = target.prepareStatement(
                         "INSERT INTO videos(video_id, title, category_id, duration, creator_name, upload_date, views, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    while (rows.next()) {
                        insert.setLong(1, rows.getLong(1));
                        insert.setString(2, rows.getString(2));
                        insert.setLong(3, rows.getLong(3));
                        insert.setInt(4, rows.getInt(4));
                        insert.setString(5, rows.getString(5));
                        insert.setString(6, rows.getString(6));
                        insert.setLong(7, rows.getLong(7));
                        insert.setString(8, rows.getString(8) == null ? "" : rows.getString(8));
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                for (String table : new String[]{"categories", "videos"}) {
                    String id = table.equals("categories") ? "category_id" : "video_id";
                    try (Statement statement = target.createStatement()) {
                        statement.execute("SELECT setval(pg_get_serial_sequence('" + table + "', '" + id + "'), (SELECT MAX(" + id + ") FROM " + table + "), true)");
                    }
                    if (count(source, table) != count(target, table))
                        throw new IllegalStateException("Row count mismatch for " + table);
                }
                target.commit();
                System.out.println("Copied " + count(target, "categories") + " categories and " + count(target, "videos") + " videos to Neon.");
                System.out.println("Website accounts were not copied. Create your cloud account through Sign up.");
            } catch (Exception ex) {
                target.rollback();
                throw ex;
            }
        }
    }
}
