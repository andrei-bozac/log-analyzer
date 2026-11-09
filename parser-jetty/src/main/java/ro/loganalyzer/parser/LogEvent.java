package ro.loganalyzer.parser;

import java.time.Instant;
import java.util.Objects;


public record LogEvent(
        String ip,
        Instant timestamp,
        String method,
        String path,
        int status,
        long bytes) {

    private static final int MAX_IP_LENGTH = 45;
    private static final int MAX_METHOD_LENGTH = 16;
    private static final int MAX_PATH_LENGTH = 2048;
    private static final int MIN_STATUS = 100;
    private static final int MAX_STATUS = 599;

    public LogEvent {
        Objects.requireNonNull(ip, "ip");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");

        if (ip.length() > MAX_IP_LENGTH) {
            throw new IllegalArgumentException("ip prea lung");
        }
        if (method.length() > MAX_METHOD_LENGTH) {
            throw new IllegalArgumentException("metoda prea lunga");
        }
        if (path.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("calea prea lunga");
        }
        if (status < MIN_STATUS || status > MAX_STATUS) {
            throw new IllegalArgumentException("status in afara intervalului 100-599");
        }
        if (bytes < 0) {
            throw new IllegalArgumentException("dimensiune negativa");
        }
    }
}