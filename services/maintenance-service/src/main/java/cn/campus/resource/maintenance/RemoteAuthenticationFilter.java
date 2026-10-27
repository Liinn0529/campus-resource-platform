package cn.campus.resource.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

class RemoteAuthenticationFilter extends OncePerRequestFilter {
    private final PlatformClient platform;
    private final ObjectMapper json;

    RemoteAuthenticationFilter(PlatformClient platform, ObjectMapper json) {
        this.platform = platform;
        this.json = json;
    }

    static boolean publicApi(String path) {
        return path.equals("/api/v1/maintenance-tickets") || path.startsWith("/api/v1/maintenance-tickets/");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !publicApi(request.getRequestURI()) || request.getMethod().equals("OPTIONS");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || header.length() > 8192 || !header.regionMatches(true, 0, "Bearer ", 0, 7)
                || header.substring(7).isBlank()) {
            ApiErrors.write(json, response, 401, "UNAUTHORIZED", "请先登录");
            return;
        }
        try {
            Actor actor = platform.identity(header.substring(7));
            MDC.put("userId", Long.toString(actor.id()));
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    actor, null, actor.permissions().stream().map(SimpleGrantedAuthority::new).toList()));
        } catch (ApiFailure failure) {
            ApiErrors.write(json, response, failure.status().value(), failure.code(), failure.getMessage());
            return;
        }
        chain.doFilter(request, response);
    }
}
