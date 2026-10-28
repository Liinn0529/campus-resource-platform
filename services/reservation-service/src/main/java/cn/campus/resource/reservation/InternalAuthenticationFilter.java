package cn.campus.resource.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

class InternalAuthenticationFilter extends OncePerRequestFilter {
    private final byte[] classroom;
    private final byte[] maintenance;
    private final ObjectMapper json;

    InternalAuthenticationFilter(String classroom, String maintenance, String platform, ObjectMapper json) {
        if (classroom.isBlank() || maintenance.isBlank()
                || classroom.getBytes(StandardCharsets.UTF_8).length < 32
                || maintenance.getBytes(StandardCharsets.UTF_8).length < 32
                || classroom.equals(maintenance) || classroom.equals(platform) || maintenance.equals(platform)) {
            throw new IllegalStateException("Configure independent private M2 and M3 occupancy credentials");
        }
        this.classroom = classroom.getBytes(StandardCharsets.UTF_8);
        this.maintenance = maintenance.getBytes(StandardCharsets.UTF_8);
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/v1/room-blocks");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String module = null;
        if (header != null && header.length() <= 8192 && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            byte[] token = header.substring(7).getBytes(StandardCharsets.UTF_8);
            boolean m2 = MessageDigest.isEqual(classroom, token);
            boolean m3 = MessageDigest.isEqual(maintenance, token);
            module = m2 ? "M2" : m3 ? "M3" : null;
        }
        if (module == null) {
            ApiErrors.write(json, response, 401, "UNAUTHORIZED", "需要有效的内部服务身份");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                module, null, List.of(new SimpleGrantedAuthority("OCCUPANCY_SERVICE"))));
        chain.doFilter(request, response);
    }
}
