package edu.lab.vms;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VideoManagementIntegrationTest {
    private static final String DB = "jdbc:sqlite:./target/test-" + UUID.randomUUID() + ".db";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.database.url", () -> DB);
        registry.add("server.address", () -> "127.0.0.1");
    }
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;
    @BeforeEach void configureClient() { rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory()); }
    String url(String path) { return "http://127.0.0.1:" + port + path; }
    HttpHeaders headers(String cookie, String csrf) {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        if (cookie != null) h.add(HttpHeaders.COOKIE, cookie);
        if (csrf != null) h.add("X-CSRF-Token", csrf);
        return h;
    }
    @SuppressWarnings("unchecked")
    @Test void fullWorkflow() {
        assertThat(rest.getForEntity(url("/api/database/status"), Map.class).getBody()).containsEntry("mode", "JDBC Fallback");
        assertThat(rest.getForEntity(url("/api/videos"), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var invalid = rest.postForEntity(url("/api/auth/login"), Map.of("username","admin","password","wrong"), Map.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var login = rest.postForEntity(url("/api/auth/login"), Map.of("username","admin","password","admin123"), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String cookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";",2)[0];
        String csrf = (String) login.getBody().get("csrfToken");
        assertThat(csrf).isNotBlank();
        var noCsrf = rest.exchange(url("/api/videos"), HttpMethod.POST,
            new HttpEntity<>(Map.of("title","Unsafe"), headers(cookie,null)), String.class);
        assertThat(noCsrf.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var stats = rest.exchange(url("/api/dashboard/stats"), HttpMethod.GET, new HttpEntity<>(headers(cookie,csrf)), Map.class);
        assertThat(stats.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number)stats.getBody().get("totalVideos")).intValue()).isEqualTo(10);
        var input = Map.of("title","Integration Test Video","categoryId",1,"duration",14,
            "creatorName","Lab Student","uploadDate","2026-10-10","views",25,"description","Database test record");
        var created = rest.exchange(url("/api/videos"), HttpMethod.POST, new HttpEntity<>(input,headers(cookie,csrf)), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        int id = ((Number)created.getBody().get("videoId")).intValue();
        var one = rest.exchange(url("/api/videos/"+id), HttpMethod.GET,new HttpEntity<>(headers(cookie,csrf)), Map.class);
        assertThat(one.getBody()).containsEntry("title","Integration Test Video");
        var search = rest.exchange(url("/api/videos/search?field=title&q=Integration"),HttpMethod.GET,new HttpEntity<>(headers(cookie,csrf)),List.class);
        assertThat(search.getBody()).hasSize(1);
        var updated = rest.exchange(url("/api/videos/"+id),HttpMethod.PUT,
            new HttpEntity<>(Map.of("title","Updated Test Video","categoryId",1,"duration",20,"creatorName","Lab Student","uploadDate","2026-10-10","views",40,"description","Updated"),headers(cookie,csrf)),Map.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).containsEntry("title","Updated Test Video");
        var reports = rest.exchange(url("/api/reports?from=2026-10-01&to=2026-10-31"),HttpMethod.GET,new HttpEntity<>(headers(cookie,csrf)),Map.class);
        assertThat(reports.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((List<?>)reports.getBody().get("dateRange"))).hasSize(1);
        var deleted = rest.exchange(url("/api/videos/"+id),HttpMethod.DELETE,new HttpEntity<>(headers(cookie,csrf)),Map.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
        var missing = rest.exchange(url("/api/videos/"+id),HttpMethod.GET,new HttpEntity<>(headers(cookie,csrf)),String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var logout = rest.exchange(url("/api/auth/logout"),HttpMethod.POST,new HttpEntity<>(headers(cookie,csrf)),Map.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);
        var afterLogout = rest.exchange(url("/api/videos"),HttpMethod.GET,new HttpEntity<>(headers(cookie,csrf)),String.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @SuppressWarnings("unchecked")
    @Test void registrationCreatesAnIndependentLogin() {
        String username = "student_" + UUID.randomUUID().toString().substring(0, 8);
        var invalid = rest.postForEntity(url("/api/auth/register"),
            Map.of("username", username, "password", "short"), Map.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        var registered = rest.postForEntity(url("/api/auth/register"),
            Map.of("username", username.toUpperCase(), "password", "a secure test password"), Map.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registered.getBody()).containsEntry("username", username);
        String cookie = registered.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
        String csrf = (String) registered.getBody().get("csrfToken");
        assertThat(rest.exchange(url("/api/videos"), HttpMethod.GET,
            new HttpEntity<>(headers(cookie, csrf)), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, username);
        assertThat(hash).startsWith("$2").doesNotContain("a secure test password");
        var duplicate = rest.postForEntity(url("/api/auth/register"),
            Map.of("username", username, "password", "another secure password"), Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        var login = rest.postForEntity(url("/api/auth/login"),
            Map.of("username", username, "password", "a secure test password"), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).containsEntry("username", username);
    }
}
