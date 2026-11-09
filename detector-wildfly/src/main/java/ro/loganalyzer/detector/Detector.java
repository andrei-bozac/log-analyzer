package ro.loganalyzer.detector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class Detector {

    private final List<DetectionRule> rules;

    public Detector(DetectionThresholds thresholds) {
        Objects.requireNonNull(thresholds, "thresholds");
        this.rules = List.of(
                new SlidingWindowRule(
                        AlertType.BRUTE_FORCE,
                        event -> event.status() == 401,
                        thresholds.bruteForceThreshold(),
                        Duration.ofSeconds(thresholds.bruteForceWindowSeconds()),
                        "Multe autentificari esuate de la aceeasi adresa"),
                new SlidingWindowRule(
                        AlertType.SCANNING,
                        event -> event.status() == 404,
                        thresholds.scanThreshold(),
                        Duration.ofSeconds(thresholds.scanWindowSeconds()),
                        "Multe cereri catre pagini inexistente de la aceeasi adresa"),
                new AttackPatternRule(),
                new HighVolumeRule(
                        thresholds.highVolumeMinRequests(),
                        thresholds.highVolumeFactor()));
    }

    public List<Alert> detect(List<LogEvent> events) {
        Objects.requireNonNull(events, "events");

        List<Alert> alerts = new ArrayList<>();
        for (DetectionRule rule : rules) {
            alerts.addAll(rule.evaluate(events));
        }

        alerts.sort(Comparator
                .comparing(Alert::severity, Comparator.reverseOrder())
                .thenComparing(Alert::eventCount, Comparator.reverseOrder()));
        return List.copyOf(alerts);
    }
}