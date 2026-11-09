package ro.loganalyzer.detector;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class AttackPatternRule implements DetectionRule {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("\\.\\./"),
            Pattern.compile("\\.\\.\\\\"),
            Pattern.compile("<script"),
            Pattern.compile("union\\s+select"),
            Pattern.compile("'\\s*or\\s+'?1'?\\s*=\\s*'?1"),
            Pattern.compile("etc/passwd"));

    private static final String DESCRIPTION =
            "Cereri cu tipare tipice de atac (path traversal, SQL injection sau XSS)";

    @Override
    public List<Alert> evaluate(List<LogEvent> events) {
        Map<String, List<LogEvent>> suspiciousByIp = new HashMap<>();
        for (LogEvent event : events) {
            if (isSuspicious(event.path())) {
                suspiciousByIp
                        .computeIfAbsent(event.ip(), ip -> new ArrayList<>())
                        .add(event);
            }
        }

        List<Alert> alerts = new ArrayList<>();
        for (List<LogEvent> ipEvents : suspiciousByIp.values()) {
            alerts.add(toAlert(ipEvents));
        }
        alerts.sort(Comparator.comparingInt(Alert::eventCount).reversed());
        return alerts;
    }

    private static boolean isSuspicious(String path) {
        String normalized = decode(path).toLowerCase(Locale.ROOT);
        for (Pattern pattern : PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    private static String decode(String path) {
        try {
            return URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return path;
        }
    }

    private static Alert toAlert(List<LogEvent> ipEvents) {
        Instant first = ipEvents.get(0).timestamp();
        Instant last = first;
        for (LogEvent event : ipEvents) {
            if (event.timestamp().isBefore(first)) {
                first = event.timestamp();
            }
            if (event.timestamp().isAfter(last)) {
                last = event.timestamp();
            }
        }
        int count = ipEvents.size();
        Severity severity = count >= 10 ? Severity.HIGH
                : count >= 3 ? Severity.MEDIUM : Severity.LOW;
        return new Alert(AlertType.ATTACK_PATTERN, severity,
                ipEvents.get(0).ip(), count, first, last, DESCRIPTION);
    }
}