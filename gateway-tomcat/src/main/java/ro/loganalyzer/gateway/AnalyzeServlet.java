package ro.loganalyzer.gateway;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@WebServlet(name = "AnalyzeServlet", urlPatterns = "/api/analyze")
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = AnalyzeServlet.MAX_FILE_BYTES,
        maxRequestSize = AnalyzeServlet.MAX_REQUEST_BYTES)
public class AnalyzeServlet extends HttpServlet {

    static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    static final int MAX_REQUEST_BYTES = MAX_FILE_BYTES + 1024 * 1024;

    private static final int MAX_EVENTS = 150_000;
    private static final int PARSER_RESPONSE_LIMIT = 32 * 1024 * 1024;
    private static final int DETECTOR_RESPONSE_LIMIT = 8 * 1024 * 1024;

    private static final String FILE_FIELD = "file";
    private static final String TEXT_CONTENT_TYPE = "text/plain; charset=UTF-8";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";

    private static final Gson GSON = new Gson();

    private final ServiceConfig config = ServiceConfig.fromEnvironment();
    private final ServiceClient client = new ServiceClient();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (!isMultipart(request)) {
            JsonResponses.writeError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Cererea trebuie sa fie multipart/form-data");
            return;
        }

        byte[] fileBytes;
        JsonObject thresholds;
        try {
            thresholds = ThresholdsReader.read(request);
            Part filePart = request.getPart(FILE_FIELD);
            if (filePart == null || !hasAllowedExtension(filePart.getSubmittedFileName())) {
                JsonResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "Fisier lipsa sau extensie nepermisa (se accepta .log si .txt)");
                return;
            }
            try {
                fileBytes = filePart.getInputStream().readNBytes(MAX_FILE_BYTES + 1);
            } finally {
                filePart.delete();
            }
        } catch (IllegalStateException e) {
            JsonResponses.writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Fisierul depaseste limita permisa");
            return;
        } catch (IllegalArgumentException e) {
            JsonResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage());
            return;
        } catch (ServletException e) {
            JsonResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Cerere invalida");
            return;
        }

        if (fileBytes.length > MAX_FILE_BYTES) {
            JsonResponses.writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Fisierul depaseste limita permisa");
            return;
        }
        if (fileBytes.length == 0) {
            JsonResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Fisierul este gol");
            return;
        }

        try {
            JsonObject parsed = parseObject("parser", client.post("parser",
                    config.parseEndpoint(), TEXT_CONTENT_TYPE, fileBytes, PARSER_RESPONSE_LIMIT));
            JsonArray events = requireArray(parsed, "events", "parser");
            int skippedLines = requireInt(parsed, "skippedLines", "parser");

            if (events.size() > MAX_EVENTS) {
                JsonResponses.writeError(response,
                        HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "Fisierul contine prea multe evenimente");
                return;
            }

            JsonObject detectRequest = new JsonObject();
            detectRequest.add("events", events);
            detectRequest.add("thresholds", thresholds);
            byte[] detectBody = GSON.toJson(detectRequest).getBytes(StandardCharsets.UTF_8);

            JsonObject detected = parseObject("detector", client.post("detector",
                    config.detectEndpoint(), JSON_CONTENT_TYPE, detectBody,
                    DETECTOR_RESPONSE_LIMIT));

            JsonObject result = new JsonObject();
            result.addProperty("parsedLines", events.size());
            result.addProperty("skippedLines", skippedLines);
            result.add("alerts", requireArray(detected, "alerts", "detector"));
            result.add("statistics", requireMember(detected, "statistics", "detector"));
            JsonResponses.write(response, HttpServletResponse.SC_OK, result);
        } catch (ServiceException e) {
            JsonResponses.writeError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "Eroare la serviciul " + e.serviceName() + ": " + e.getMessage());
        }
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data");
    }

    private static boolean hasAllowedExtension(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".log") || lower.endsWith(".txt");
    }

    private static JsonObject parseObject(String serviceName, String json)
            throws ServiceException {
        try {
            JsonElement element = JsonParser.parseString(json);
            if (element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (JsonParseException e) {
            throw invalidResponse(serviceName);
        }
        throw invalidResponse(serviceName);
    }

    private static JsonElement requireMember(JsonObject object, String name, String serviceName)
            throws ServiceException {
        JsonElement member = object.get(name);
        if (member == null || member.isJsonNull()) {
            throw invalidResponse(serviceName);
        }
        return member;
    }

    private static JsonArray requireArray(JsonObject object, String name, String serviceName)
            throws ServiceException {
        JsonElement member = requireMember(object, name, serviceName);
        if (!member.isJsonArray()) {
            throw invalidResponse(serviceName);
        }
        return member.getAsJsonArray();
    }

    private static int requireInt(JsonObject object, String name, String serviceName)
            throws ServiceException {
        JsonElement member = requireMember(object, name, serviceName);
        if (!member.isJsonPrimitive() || !member.getAsJsonPrimitive().isNumber()) {
            throw invalidResponse(serviceName);
        }
        return member.getAsInt();
    }

    private static ServiceException invalidResponse(String serviceName) {
        return new ServiceException(serviceName, "Raspuns invalid de la serviciu", null);
    }
}