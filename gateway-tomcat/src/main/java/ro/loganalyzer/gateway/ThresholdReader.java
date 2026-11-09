package ro.loganalyzer.gateway;

import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.List;

final class ThresholdsReader {

    private record Field(String name, int defaultValue, int min, int max) {}

    private static final List<Field> FIELDS = List.of(
            new Field("bruteForceThreshold", 10, 1, 100_000),
            new Field("bruteForceWindowSeconds", 60, 1, 86_400),
            new Field("scanThreshold", 20, 1, 100_000),
            new Field("scanWindowSeconds", 60, 1, 86_400),
            new Field("highVolumeMinRequests", 100, 1, 100_000),
            new Field("highVolumeFactor", 5, 1, 100));

    private static final int MAX_VALUE_LENGTH = 9;

    private ThresholdsReader() {
    }

    static JsonObject read(HttpServletRequest request) throws IOException, ServletException {
        JsonObject thresholds = new JsonObject();
        for (Field field : FIELDS) {
            thresholds.addProperty(field.name(), readField(request, field));
        }
        return thresholds;
    }

    private static int readField(HttpServletRequest request, Field field)
            throws IOException, ServletException {
        String raw = request.getParameter(field.name());
        if (raw == null || raw.isBlank()) {
            return field.defaultValue();
        }
        String text = raw.strip();
        if (text.length() > MAX_VALUE_LENGTH) {
            throw invalid(field);
        }
        try {
            int value = Integer.parseInt(text);
            if (value < field.min() || value > field.max()) {
                throw invalid(field);
            }
            return value;
        } catch (NumberFormatException e) {
            throw invalid(field);
        }
    }

    private static IllegalArgumentException invalid(Field field) {
        return new IllegalArgumentException("Valoare invalida pentru " + field.name()
                + ": trebuie sa fie un numar intre " + field.min() + " si " + field.max());
    }
}