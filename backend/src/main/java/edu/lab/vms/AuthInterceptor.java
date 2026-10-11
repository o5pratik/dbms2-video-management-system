package edu.lab.vms;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) return true;
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null)
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please log in to continue.");
        String method = request.getMethod();
        if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS")) {
            String supplied = request.getHeader("X-CSRF-Token");
            if (supplied == null || !supplied.equals(session.getAttribute("csrfToken")))
                throw new ApiException(HttpStatus.FORBIDDEN, "Session token missing or invalid. Refresh and log in again.");
        }
        return true;
    }
}

