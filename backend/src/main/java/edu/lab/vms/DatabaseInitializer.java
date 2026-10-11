package edu.lab.vms;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;
import java.sql.Connection;

@Component
public class DatabaseInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final DataSource dataSource;
    private final DatabaseConfig config;
    private final JdbcTemplate jdbc;
    @Value("${app.demo.admin.enabled:true}") private boolean demoAdminEnabled;
    public DatabaseInitializer(DataSource dataSource, DatabaseConfig config, JdbcTemplate jdbc) {
        this.dataSource = dataSource; this.config = config; this.jdbc = jdbc;
    }
    @Override public void run(ApplicationArguments args) {
        try {
            if ("JDBC_FALLBACK".equalsIgnoreCase(config.mode()) || "POSTGRES".equalsIgnoreCase(config.mode())) {
                try (Connection connection = dataSource.getConnection()) {
                    ScriptUtils.executeSqlScript(connection, new ClassPathResource("database/schema-" + ("POSTGRES".equalsIgnoreCase(config.mode()) ? "postgres" : "sqlite") + ".sql"));
                }
            }
            Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            if (demoAdminEnabled && users != null && users == 0) {
                String sql = "ORACLE".equalsIgnoreCase(config.mode())
                    ? "INSERT INTO users(user_id, username, password_hash) VALUES (users_seq.NEXTVAL, ?, ?)"
                    : "INSERT INTO users(username, password_hash) VALUES (?, ?)";
                jdbc.update(sql, "admin", new BCryptPasswordEncoder().encode("admin123"));
            }
            Integer categories = jdbc.queryForObject("SELECT COUNT(*) FROM categories", Integer.class);
            if (categories != null && categories == 0) {
                if ("JDBC_FALLBACK".equalsIgnoreCase(config.mode()) || "POSTGRES".equalsIgnoreCase(config.mode())) {
                    try (Connection connection = dataSource.getConnection()) {
                        ScriptUtils.executeSqlScript(connection, new ClassPathResource("database/seed-sqlite.sql"));
                    }
                } else {
                    seedOdbc();
                }
            }
        } catch (Exception ex) {
            if ("POSTGRES".equalsIgnoreCase(config.mode()))
                throw new IllegalStateException("PostgreSQL initialization failed", ex);
            log.warn("Database initialization could not complete. Check /api/database/status and configuration: {}", ex.getMessage());
        }
    }
    private void seedOdbc() {
        boolean oracle = "ORACLE".equalsIgnoreCase(config.mode());
        for (String category : new String[]{"Education", "Entertainment", "Music", "Technology", "Sports"})
            jdbc.update(oracle
                ? "INSERT INTO categories(category_id, category_name) VALUES (categories_seq.NEXTVAL, ?)"
                : "INSERT INTO categories(category_name) VALUES (?)", category);
        Object[][] samples = {
            {"Understanding SQL Joins",1,18,"Aarav Mehta","2026-08-14",8420,"A visual introduction to INNER JOIN."},
            {"Building a Study Routine",1,12,"Priya Nair","2026-07-05",5270,"Practical study habits."},
            {"Campus Short Film",2,24,"Rohan Kapoor","2026-06-19",18400,"A short campus story."},
            {"Behind the Scenes: Student Fest",2,9,"Neha Sharma","2026-09-02",9300,"Annual fest highlights."},
            {"Acoustic Evening Session",3,16,"Isha Rao","2026-04-28",24600,"Live acoustic performance."},
            {"Piano Basics for Beginners",3,21,"Kabir Sethi","2026-03-11",15750,"First piano melodies."},
            {"How JDBC Connects to Databases",4,15,"Ananya Gupta","2026-09-21",11280,"JDBC connection flow."},
            {"Inside a Modern Computer",4,13,"Dev Patel","2026-05-17",20340,"Computer hardware tour."},
            {"Five Great Football Goals",5,8,"Sameer Khan","2026-08-29",31800,"Local league highlights."},
            {"Beginner Yoga Flow",5,19,"Meera Joshi","2026-02-07",13890,"Gentle movement routine."}
        };
        for (Object[] s : samples)
            jdbc.update(oracle
                ? "INSERT INTO videos(video_id,title,category_id,duration,creator_name,upload_date,views,description) VALUES (videos_seq.NEXTVAL,?,?,?,?,?,?,?)"
                : "INSERT INTO videos(title,category_id,duration,creator_name,upload_date,views,description) VALUES (?,?,?,?,?,?,?)",
                s[0],s[1],s[2],s[3],s[4],s[5],s[6]);
    }
}

