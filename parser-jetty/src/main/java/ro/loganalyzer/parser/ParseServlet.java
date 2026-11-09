package ro.loganalyzer.parser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@WebServlet(name = "ParseServlet", urlPatterns = "/parse")
public class ParseServlet extends HttpServlet {

    private static final int MAX_BODY_BYTES = 10 * 1024 * 1024;
    private static final String EXPECTED_CONTENT_TYPE = "text/plain";

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .create();

    private final LogParser parser = new LogParser();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (!hasExpectedContentType(request)) {
            writeError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Content-Type trebuie sa fie text/plain");
            return;
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Fisierul depaseste limita permisa");
            return;
        }

        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Fisierul depaseste limita permisa");
            return;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8))) {
            ParseResult result = parser.parse(reader);
            writeJson(response, HttpServletResponse.SC_OK, GSON.toJson(result));
        }
    }

    private static boolean hasExpectedContentType(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith(EXPECTED_CONTENT_TYPE);
    }

    private static void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        writeJson(response, status, GSON.toJson(Map.of("error", message)));
    }

    private static void writeJson(HttpServletResponse response, int status, String json)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getWriter().write(json);
    }
}