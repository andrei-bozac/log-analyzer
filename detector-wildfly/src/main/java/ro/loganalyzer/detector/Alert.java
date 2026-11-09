package ro.loganalyzer.detector;

import java.time.Instant;
import java.util.Objects;

public record Alert(
        AlertType type,
        Severity severity,
        String ip,
        int eventCount,
        Instant firstSeen,
        Instant lastSeen,
        String description) {

    private static final int MAX_DESCRIPTION_LENGTH = 200;

    public Alert {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(ip, "ip");
        Objects.requireNonNull(firstSeen, "firstSeen");
        Objects.requireNonNull(lastSeen, "lastSeen");
        Objects.requireNonNull(description, "description");

        if (eventCount < 1) {
            throw new IllegalArgumentException("o alerta are cel putin un eveniment");
        }
        if (firstSeen.isAfter(lastSeen)) {
            throw new IllegalArgumentException("firstSeen este dupa lastSeen");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("descriere prea lunga");
        }
    }
}