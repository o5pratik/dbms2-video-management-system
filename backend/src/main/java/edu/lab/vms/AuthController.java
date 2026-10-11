package edu.lab.vms;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate jdbc;
    private final DatabaseConfig config;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    public AuthController(JdbcTemplate jdbc, DatabaseConfig config) { this.jdbc = jdbc; this.config = config; }
    public record LoginRequest(@NotBlank(message="Username is required") String username,
                               @NotBlank(message="Password is required") String password) {}
    public record RegisterRequest(
        @NotBlank(message="Username is required")
        @Size(min=3, max=32, message="Username must be 3 to 32 characters")
        @Pattern(regexp="[A-Za-z0-9_.-]+", message="Username can use letters, numbers, dots, underscores, and hyphens")
        String username,
        @NotBlank(message="Password is required")
        @Size(min=8, max=72, message="Password must be 8 to 72 characters")
        String password) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> register(@Valid @RequestBody RegisterRequest input, HttpServletRequest request) {
        String username = input.username().toLowerCase(Locale.ROOT);
        if (usernameExists(username))
            throw new ApiException(HttpStatus.CONFLICT, "That username is already taken.");
        String sql = "INSERT INTO users(username, password_hash) VALUES (?, ?)";
        if ("ORACLE".equalsIgnoreCase(config.mode()))
            sql = "INSERT INTO users(user_id, username, password_hash) VALUES (users_seq.NEXTVAL, ?, ?)";
        try { jdbc.update(sql, username, encoder.encode(input.password())); }
        catch (DataAccessException ex) {
            if (usernameExists(username))
                throw new ApiException(HttpStatus.CONFLICT, "That username is already taken.");
            throw ex;
        }
        Long id = jdbc.queryForObject("SELECT user_id FROM users WHERE username = ?", Long.class, username);
        return startSession(id, username, request);
    }

    private boolean usernameExists(String username) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Long.class, username);
        return count != null && count > 0;
    }

    @PostMapping("/login")
    public Map<String,Object> login(@Valid @RequestBody LoginRequest input, HttpServletRequest request) {
        var users = jdbc.query("SELECT user_id, username, password_hash FROM users WHERE username = ?",
            (rs, row) -> Map.<String,Object>of("id", rs.getLong(1), "username", rs.getString(2), "hash", rs.getString(3)),
            input.username().trim().toLowerCase(Locale.ROOT));
        if (users.isEmpty() || !encoder.matches(input.password(), (String) users.get(0).get("hash")))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        return startSession((Long) users.get(0).get("id"), (String) users.get(0).get("username"), request);
    }

    private Map<String,Object> startSession(Long id, String username, HttpServletRequest request) {
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = request.getSession(true);
        String token = UUID.randomUUID().toString();
        session.setAttribute("userId", id);
        session.setAttribute("username", username);
        session.setAttribute("csrfToken", token);
        session.setMaxInactiveInterval(60 * 60 * 8);
        return Map.of("username", username, "csrfToken", token);
    }
    @GetMapping("/me")
    public Map<String,Object> me(HttpSession session) {
        return Map.of("username", session.getAttribute("username"), "csrfToken", session.getAttribute("csrfToken"));
    }
    @PostMapping("/logout")
    public Map<String,String> logout(HttpServletRequest request) {
        request.getSession(false).invalidate();
        return Map.of("message", "Logged out successfully.");
    }
}
