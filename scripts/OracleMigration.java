import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;

/** One-time, non-destructive migration from the local SQLite file to Oracle XE. */
class OracleMigration {
    private static final String APP_USER = "VMS_APP";

    public static void main(String[] args) throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();
        Path sqliteFile = root.resolve("database/videos.db");
        if (!Files.isRegularFile(sqliteFile)) throw new IllegalStateException("SQLite database is missing");
        String adminPassword = System.getenv("VMS_ORACLE_ADMIN_PASSWORD");
        if (adminPassword == null || adminPassword.isBlank()) throw new IllegalStateException("Oracle admin password is missing");
        String adminUser = System.getenv().getOrDefault("VMS_ORACLE_ADMIN_USER", "SYSTEM");
        String url = "jdbc:oracle:thin:@//127.0.0.1:1522/XE";
        String appPassword = "Vms_" + HexFormat.of().formatHex(new SecureRandom().generateSeed(12));

        try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile);
             Connection admin = DriverManager.getConnection(url, adminUser, adminPassword)) {
            long sourceVideos = count(sqlite, "videos");
            if (sourceVideos == 0) throw new IllegalStateException("The source database has no videos");
            try (PreparedStatement ps = admin.prepareStatement("SELECT COUNT(*) FROM all_users WHERE username=?")) {
                ps.setString(1, APP_USER);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getLong(1) != 0) throw new IllegalStateException("VMS_APP already exists; existing Oracle data was left untouched");
                }
            }
            try (Statement s = admin.createStatement()) {
                s.execute("CREATE USER " + APP_USER + " IDENTIFIED BY \"" + appPassword + "\" DEFAULT TABLESPACE USERS TEMPORARY TABLESPACE TEMP");
                s.execute("ALTER USER " + APP_USER + " QUOTA 50M ON USERS");
                s.execute("GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO " + APP_USER);
            }
            try (Connection oracle = DriverManager.getConnection(url, APP_USER, appPassword)) {
                createTables(oracle, root.resolve("database/schema-oracle.sql"));
                oracle.setAutoCommit(false);
                try {
                    copy(sqlite, oracle, "users", "user_id, username, password_hash", 3);
                    copy(sqlite, oracle, "categories", "category_id, category_name", 2);
                    copy(sqlite, oracle, "videos", "video_id, title, category_id, duration, creator_name, upload_date, views, description", 8);
                    oracle.commit();
                } catch (Exception ex) {
                    oracle.rollback();
                    throw ex;
                }
                createSequences(oracle);
                for (String table : List.of("users", "categories", "videos")) {
                    if (count(sqlite, table) != count(oracle, table))
                        throw new IllegalStateException("Row count mismatch for " + table);
                }
                if (scalar(sqlite, "SELECT COALESCE(SUM(views),0) FROM videos") !=
                    scalar(oracle, "SELECT NVL(SUM(views),0) FROM videos"))
                    throw new IllegalStateException("Video views mismatch");
                saveConfig(root.resolve(".env"), url, appPassword);
                System.out.println("Oracle migration complete: " + count(oracle, "users") + " users, " +
                    count(oracle, "categories") + " categories, " + count(oracle, "videos") + " videos.");
            }
        }
    }

    private static void createTables(Connection oracle, Path schemaFile) throws Exception {
        StringBuilder sql = new StringBuilder();
        try (Statement statement = oracle.createStatement()) {
            for (String line : Files.readAllLines(schemaFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
                sql.append(line).append('\n');
                if (trimmed.endsWith(";")) {
                    String ddl = sql.toString().trim();
                    sql.setLength(0);
                    if (!ddl.startsWith("CREATE SEQUENCE")) statement.execute(ddl.substring(0, ddl.length() - 1));
                }
            }
        }
    }

    private static void copy(Connection source, Connection target, String table, String columns, int count) throws Exception {
        String placeholders = String.join(",", Collections.nCopies(count, "?"));
        try (Statement query = source.createStatement();
             ResultSet rows = query.executeQuery("SELECT " + columns + " FROM " + table);
             PreparedStatement insert = target.prepareStatement("INSERT INTO " + table + "(" + columns + ") VALUES (" + placeholders + ")")) {
            while (rows.next()) {
                for (int i = 1; i <= count; i++) insert.setObject(i, rows.getObject(i));
                insert.executeUpdate();
            }
        }
    }

    private static void createSequences(Connection oracle) throws Exception {
        try (Statement statement = oracle.createStatement()) {
            for (String table : List.of("users", "categories", "videos")) {
                String id = table.equals("categories") ? "category_id" : table.equals("users") ? "user_id" : "video_id";
                long next = scalar(oracle, "SELECT NVL(MAX(" + id + "),0)+1 FROM " + table);
                statement.execute("CREATE SEQUENCE " + table + "_seq START WITH " + next);
            }
        }
    }

    private static long count(Connection c, String table) throws Exception {
        return scalar(c, "SELECT COUNT(*) FROM " + table);
    }

    private static long scalar(Connection c, String sql) throws Exception {
        try (Statement s = c.createStatement(); ResultSet rows = s.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static void saveConfig(Path path, String url, String password) throws Exception {
        List<String> lines = Files.exists(path) ? Files.readAllLines(path) : new ArrayList<>();
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("DB_MODE", "ORACLE");
        replacements.put("DB_URL", url);
        replacements.put("DB_DRIVER", "oracle.jdbc.OracleDriver");
        replacements.put("DB_USER", APP_USER);
        replacements.put("DB_PASSWORD", password);
        replacements.put("DB_DSN", "");
        List<String> updated = new ArrayList<>();
        for (String line : lines) {
            int equals = line.indexOf('=');
            String key = equals > 0 ? line.substring(0, equals).trim() : "";
            if (!replacements.containsKey(key)) updated.add(line);
        }
        for (var entry : replacements.entrySet()) updated.add(entry.getKey() + "=" + entry.getValue());
        Path temp = path.resolveSibling(".env.oracle.tmp");
        Files.write(temp, updated);
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
    }
}
