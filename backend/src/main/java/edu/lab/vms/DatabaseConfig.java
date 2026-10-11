package edu.lab.vms;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {
    @Value("${app.database.mode}") private String mode;
    @Value("${app.database.url}") private String url;
    @Value("${app.database.driver}") private String driver;
    @Value("${app.database.username}") private String username;
    @Value("${app.database.password}") private String password;
    @Value("${app.database.dsn}") private String dsn;

    public String mode() { return mode; }
    public String dsn() { return dsn; }
    public String label() {
        if ("ORACLE".equalsIgnoreCase(mode)) return "Oracle JDBC";
        if ("POSTGRES".equalsIgnoreCase(mode)) return "PostgreSQL JDBC";
        return "ODBC".equalsIgnoreCase(mode) ? "ODBC Bridge" : "JDBC Fallback";
    }

    @Bean
    public DataSource dataSource() {
        if (!"ODBC".equalsIgnoreCase(mode) && !"JDBC_FALLBACK".equalsIgnoreCase(mode) && !"ORACLE".equalsIgnoreCase(mode) && !"POSTGRES".equalsIgnoreCase(mode))
            throw new IllegalArgumentException("DB_MODE must be JDBC_FALLBACK, ODBC, ORACLE, or POSTGRES");
        if ("ODBC".equalsIgnoreCase(mode) && (url.isBlank() || driver.isBlank() || (url.contains("{dsn}") && dsn.isBlank())))
            throw new IllegalArgumentException("ODBC mode requires DB_URL, DB_DRIVER, and DB_DSN when the URL uses {dsn}");
        if ("ODBC".equalsIgnoreCase(mode) && url.startsWith("jdbc:sqlite:"))
            throw new IllegalArgumentException("A SQLite JDBC URL cannot be labeled as ODBC mode");
        String resolvedUrl = url.replace("{dsn}", dsn);
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driver);
        config.setJdbcUrl(resolvedUrl);
        if (!username.isBlank()) config.setUsername(username);
        if (!password.isBlank()) config.setPassword(password);
        config.setMaximumPoolSize(resolvedUrl.startsWith("jdbc:sqlite:") || "ODBC".equalsIgnoreCase(mode) ? 1 : 5);
        config.setPoolName("vms-database");
        config.setInitializationFailTimeout(-1);
        if (resolvedUrl.startsWith("jdbc:sqlite:")) config.setConnectionInitSql("PRAGMA foreign_keys=ON");
        return new HikariDataSource(config);
    }
}

