package cn.campus.resource.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;

final class ApiErrors {
    private ApiErrors() { }

    static Map<String, String> body(String code, String message) {
        return Map.of("code", code, "message", message, "requestId", RequestIds.current());
    }

    static void write(ObjectMapper json, HttpServletResponse response, int status,
            String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), body(code, message));
    }
}
