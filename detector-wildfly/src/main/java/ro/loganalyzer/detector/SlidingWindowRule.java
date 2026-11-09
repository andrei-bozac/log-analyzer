package ro.loganalyzer.detector;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class SlidingWindowRule implements DetectionRule {

    private static final int MAX_THRESHOLD = 100_000;
    private static final Duration MAX_WINDOW = Duration.ofDays(1);

    private final AlertType type;
    private final Predicate<LogEvent> matcher;
    private final int threshold;
    private final Duration window;
    private final String description;

    public SlidingWindowRule(AlertType type, Predicate<LogEvent> matcher,
                             int threshold, Duration window, String description) {
        this.type = Objects.requireNonNull(type, "type");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.window = Objects.requireNonNull(window, "window");
        this.description = Objects.requireNonNull(description, "description");

        if (threshold < 1 || threshold > MAX_THRESHOLD) {
            throw new IllegalArgumentException("prag invalid");
        }
        if (window.isNegative() || window.isZero() || window.compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException("fereastra invalida");
        }
        this.threshold = threshold;
    }

    @Override
    public List<Alert> evaluate(List<LogEvent> events) {
        Map<String, List<LogEvent>> byIp = events.stream()
                .filter(matcher)
                .collect(Collectors.groupingBy(LogEvent::ip));

        List<Alert> alerts = new ArrayList<>();
        for (List<LogEvent> ipEvents : byIp.values()) {
            findBurst(ipEvents).ifPresent(alerts::add);
        }
        alerts.sort(Comparator.comparingInt(Alert::eventCount).reversed());
        return alerts;
    }

    private Optional<Alert> findBurst(List<LogEvent> ipEvents) {
        List<LogEvent> sorted = ipEvents.stream()
                .sorted(Comparator.comparing(LogEvent::timestamp))
                .toList();

        int start = 0;
        int bestStart = 0;
        int bestCount = 0;

        for (int end = 0; end < sorted.size(); end++) {
            while (Duration.between(sorted.get(start).timestamp(),
                    sorted.get(end).timestamp()).compareTo(window) > 0) {
                start++;
            }
            int count = end - start + 1;
            if (count > bestCount) {
                bestCount = count;
                bestStart = start;
            }
        }

        if (bestCount < threshold) {
            return Optional.empty();
        }

        Instant firstSeen = sorted.get(bestStart).timestamp();
        Instant lastSeen = sorted.get(bestStart + bestCount - 1).timestamp();
        return Optional.of(new Alert(type, severityFor(bestCount),
                sorted.get(0).ip(), bestCount, firstSeen, lastSeen, description));
    }

    private Severity severityFor(int count) {
        if (count >= threshold * 5) {
            return Severity.HIGH;
        }
        if (count >= threshold * 2) {
            return Severity.MEDIUM;
        }
        return Severity.LOW;
    }
}