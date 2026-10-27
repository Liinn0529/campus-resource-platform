package cn.campus.resource.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

class InternalTokenFilter extends OncePerRequestFilter {
    private final ObjectMapper json;
    private final List<String> tokens;
    private static final List<String> CALLERS = List.of("reservation-service", "classroom-service", "maintenance-service");

    InternalTokenFilter(ObjectMapper json, String reservation, String classroom, String maintenance) {
        this.json = json;
        this.tokens = List.of(reservation, classroom, maintenance);
        if (tokens.stream().anyMatch(token -> token.isBlank() || token.getBytes(StandardCharsets.UTF_8).length < 32)
                || tokens.stream().distinct().count() != 3) {
            throw new IllegalStateException("Internal callers require distinct secrets of at least 32 UTF-8 bytes");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        byte[] supplied = header != null && header.startsWith("Bearer ")
                ? header.substring(7).getBytes(StandardCharsets.UTF_8) : new byte[0];
        int matched = -1;
        for (int i = 0; i < tokens.size(); i++) {
            if (MessageDigest.isEqual(supplied, tokens.get(i).getBytes(StandardCharsets.UTF_8))) { matched = i; }
        }
        if (matched < 0) {
            ApiErrors.write(json, response, 401, "UNAUTHORIZED", "内部服务身份无效");
            return;
        }
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("SERVICE_READ"));
        if (matched == 2) { authorities.add(new SimpleGrantedAuthority("SERVICE_MAINTENANCE")); }
        if (matched == 0) { authorities.add(new SimpleGrantedAuthority("SERVICE_RESERVATION")); }
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(CALLERS.get(matched), null, authorities));
        chain.doFilter(request, response);
    }
}
