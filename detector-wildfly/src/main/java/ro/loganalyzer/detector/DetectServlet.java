package ro.loganalyzer.detector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;


@WebServlet(name = "DetectServlet", urlPatterns = "/detect")
public class DetectServlet extends HttpServlet {

    private static final int MAX_BODY_BYTES = 32 * 1024 * 1024;
    private static final int MAX_EVENTS = 150_000;
    private static final String EXPECTED_CONTENT_TYPE = "application/json";

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .create();

    private final StatisticsCalculator statisticsCalculator = new StatisticsCalculator();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (!hasExpectedContentType(request)) {
            writeError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Content-Type trebuie sa fie application/json");
            return;
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Cererea depaseste limita permisa");
            return;
        }

        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Cererea depaseste limita permisa");
            return;
        }

        DetectRequest detectRequest = readRequest(body);
        if (detectRequest == null || !hasValidEvents(detectRequest.events())) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Cerere invalida");
            return;
        }

        DetectionThresholds thresholds = detectRequest.thresholds() != null
                ? detectRequest.thresholds()
                : DetectionThresholds.defaults();

        Detector detector;
        try {
            detector = new Detector(thresholds);
        } catch (IllegalArgumentException e) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Praguri invalide");
            return;
        }

        List<LogEvent> events = detectRequest.events();
        DetectResponse result = new DetectResponse(
                detector.detect(events),
                statisticsCalculator.calculate(events));
        writeJson(response, HttpServletResponse.SC_OK, GSON.toJson(result));
    }

    private static DetectRequest readRequest(byte[] body) throws IOException {
        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(body), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, DetectRequest.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean hasValidEvents(List<LogEvent> events) {
        return events != null
                && events.size() <= MAX_EVENTS
                && !events.contains(null);
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