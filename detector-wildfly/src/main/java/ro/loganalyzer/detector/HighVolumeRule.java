package ro.loganalyzer.detector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class HighVolumeRule implements DetectionRule {

    private static final int MAX_MIN_REQUESTS = 1_000_000;
    private static final int MAX_FACTOR = 1_000;
    private static final String DESCRIPTION =
            "Volum de cereri mult peste cel obisnuit al celorlalte adrese";

    private final int minRequests;
    private final int factor;

    public HighVolumeRule(int minRequests, int factor) {
        if (minRequests < 1 || minRequests > MAX_MIN_REQUESTS) {
            throw new IllegalArgumentException("minRequests invalid");
        }
        if (factor < 1 || factor > MAX_FACTOR) {
            throw new IllegalArgumentException("factor invalid");
        }
        this.minRequests = minRequests;
        this.factor = factor;
    }

    @Override
    public List<Alert> evaluate(List<LogEvent> events) {
        if (events.isEmpty()) {
            return List.of();
        }

        Map<String, List<LogEvent>> byIp = events.stream()
                .collect(Collectors.groupingBy(LogEvent::ip));

        int[] counts = byIp.values().stream()
                .mapToInt(List::size)
                .sorted()
                .toArray();
        double limit = Math.max(minRequests, factor * median(counts));

        List<Alert> alerts = new ArrayList<>();
        for (List<LogEvent> ipEvents : byIp.values()) {
            if (ipEvents.size() >= limit) {
                alerts.add(toAlert(ipEvents, limit));
            }
        }
        alerts.sort(Comparator.comparingInt(Alert::eventCount).reversed());
        return alerts;
    }

    private static double median(int[] sorted) {
        int mid = sorted.length / 2;
        if (sorted.length % 2 == 1) {
            return sorted[mid];
        }
        return (sorted[mid - 1] + sorted[mid]) / 2.0;
    }

    private static Alert toAlert(List<LogEvent> ipEvents, double limit) {
        Instant first = ipEvents.stream().map(LogEvent::timestamp)
                .min(Comparator.naturalOrder()).orElseThrow();
        Instant last = ipEvents.stream().map(LogEvent::timestamp)
                .max(Comparator.naturalOrder()).orElseThrow();
        int count = ipEvents.size();
        Severity severity = count >= limit * 3 ? Severity.HIGH
                : count >= limit * 1.5 ? Severity.MEDIUM : Severity.LOW;
        return new Alert(AlertType.HIGH_VOLUME, severity,
                ipEvents.get(0).ip(), count, first, last, DESCRIPTION);
    }
}