package mera.mera_v2.ltkach;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class LtKhachConfigInterceptor implements HandlerInterceptor {

    public static final String PROTECTED_PASSWORD = "meragroup2026@";
    public static final String SESSION_KEY = "ltkach_config_auth";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        
        if (session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_KEY))) {
            return true;
        }

        // AJAX request - return 401
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"unauthorized\",\"redirect\":\"/lt-khach/config-login\"}");
            return false;
        }

        // Regular request - redirect to login page
        response.sendRedirect("/lt-khach/config-login");
        return false;
    }
}
