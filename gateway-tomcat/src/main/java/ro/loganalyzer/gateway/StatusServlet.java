package ro.loganalyzer.gateway;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@WebServlet(name = "StatusServlet", urlPatterns = "/api/status")
public class StatusServlet extends HttpServlet {

    private final ServiceConfig config = ServiceConfig.fromEnvironment();
    private final ServiceClient client = new ServiceClient();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("parser", label(client.isUp(config.parserHealthEndpoint())));
        status.put("detector", label(client.isUp(config.detectorHealthEndpoint())));
        JsonResponses.write(response, HttpServletResponse.SC_OK, status);
    }

    private static String label(boolean up) {
        return up ? "UP" : "DOWN";
    }
}