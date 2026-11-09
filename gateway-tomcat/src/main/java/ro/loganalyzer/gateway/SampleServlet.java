package ro.loganalyzer.gateway;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@WebServlet(name = "SampleServlet", urlPatterns = "/api/sample")
public class SampleServlet extends HttpServlet {

    private static final String RESOURCE = "/sample-access.log";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try (InputStream in = SampleServlet.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                JsonResponses.writeError(response, HttpServletResponse.SC_NOT_FOUND,
                        "Fisierul de proba lipseste");
                return;
            }
            byte[] content = in.readAllBytes();
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/plain");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setContentLength(content.length);
            response.getOutputStream().write(content);
        }
    }
}