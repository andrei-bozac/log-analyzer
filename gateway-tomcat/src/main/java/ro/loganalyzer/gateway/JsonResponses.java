package ro.loganalyzer.gateway;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class JsonResponses {

    private static final Gson GSON = new Gson();

    private JsonResponses() {
    }

    static void write(HttpServletResponse response, int status, Object body)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getWriter().write(GSON.toJson(body));
    }

    static void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        write(response, status, Map.of("error", message));
    }
}