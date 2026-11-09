package ro.loganalyzer.gateway;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ServiceClient {

    private static final Logger LOGGER = Logger.getLogger(ServiceClient.class.getName());

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public String post(String serviceName, URI uri, String contentType,
                       byte[] body, int maxResponseBytes) throws ServiceException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", contentType)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        try {
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    LOGGER.warning("Serviciul " + serviceName
                            + " a raspuns cu status " + response.statusCode());
                    throw new ServiceException(serviceName,
                            "Raspuns neasteptat de la serviciu", null);
                }
                byte[] bytes = in.readNBytes(maxResponseBytes + 1);
                if (bytes.length > maxResponseBytes) {
                    LOGGER.warning("Raspunsul serviciului " + serviceName
                            + " depaseste limita permisa");
                    throw new ServiceException(serviceName,
                            "Raspuns prea mare de la serviciu", null);
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Apel esuat catre serviciul " + serviceName, e);
            throw new ServiceException(serviceName, "Serviciul nu este disponibil", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(serviceName, "Apel intrerupt", e);
        }
    }

    public boolean isUp(URI healthUri) {
        HttpRequest request = HttpRequest.newBuilder(healthUri)
                .timeout(HEALTH_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<Void> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}