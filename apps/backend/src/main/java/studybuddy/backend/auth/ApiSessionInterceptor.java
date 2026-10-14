package studybuddy.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import studybuddy.backend.common.DomainException;

@Component
public class ApiSessionInterceptor implements HandlerInterceptor {
    private final SessionService sessions;

    public ApiSessionInterceptor(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        response.setHeader("Cache-Control", "no-store");
        if ("OPTIONS".equals(request.getMethod())) return true;
        if (!"GET".equals(request.getMethod())
                && !"1".equals(request.getHeader("X-StudyBuddy-Request")))
            throw DomainException.forbidden("Missing application request header.");
        if (!request.getRequestURI().startsWith("/api/session"))
            sessions.actor(request.getSession());
        return true;
    }
}
