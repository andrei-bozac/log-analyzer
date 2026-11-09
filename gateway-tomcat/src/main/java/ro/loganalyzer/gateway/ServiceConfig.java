package ro.loganalyzer.gateway;

import java.net.URI;
import java.util.Objects;

public record ServiceConfig(URI parserUrl, URI detectorUrl) {

    private static final String PARSER_ENV = "PARSER_URL";
    private static final String DETECTOR_ENV = "DETECTOR_URL";
    private static final String DEFAULT_PARSER = "http://localhost:8081/parser";
    private static final String DEFAULT_DETECTOR = "http://localhost:8082/detector";

    public ServiceConfig {
        parserUrl = validate(parserUrl, "parserUrl");
        detectorUrl = validate(detectorUrl, "detectorUrl");
    }

    public static ServiceConfig fromEnvironment() {
        return new ServiceConfig(
                URI.create(readEnv(PARSER_ENV, DEFAULT_PARSER)),
                URI.create(readEnv(DETECTOR_ENV, DEFAULT_DETECTOR)));
    }

    public URI parseEndpoint() {
        return URI.create(parserUrl + "/parse");
    }

    public URI parserHealthEndpoint() {
        return URI.create(parserUrl + "/health");
    }

    public URI detectEndpoint() {
        return URI.create(detectorUrl + "/detect");
    }

    public URI detectorHealthEndpoint() {
        return URI.create(detectorUrl + "/health");
    }

    private static String readEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static URI validate(URI uri, String name) {
        Objects.requireNonNull(uri, name);
        String scheme = uri.getScheme();
        boolean httpScheme = "http".equals(scheme) || "https".equals(scheme);
        if (!httpScheme
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(name + " invalid");
        }
        String text = uri.toString();
        return text.endsWith("/") ? URI.create(text.substring(0, text.length() - 1)) : uri;
    }
}