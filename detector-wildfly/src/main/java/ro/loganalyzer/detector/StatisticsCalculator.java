package ro.loganalyzer.detector;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;


public final class StatisticsCalculator {

    private static final int TOP_IPS_LIMIT = 10;

    public Statistics calculate(List<LogEvent> events) {
        Map<String, Long> countByIp = events.stream()
                .collect(Collectors.groupingBy(LogEvent::ip, Collectors.counting()));

        List<Statistics.IpCount> topIps = countByIp.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_IPS_LIMIT)
                .map(entry -> new Statistics.IpCount(entry.getKey(), entry.getValue().intValue()))
                .toList();

        Map<String, Integer> byHour = new TreeMap<>();
        for (LogEvent event : events) {
            String hour = event.timestamp().truncatedTo(ChronoUnit.HOURS).toString();
            byHour.merge(hour, 1, Integer::sum);
        }

        Map<Integer, Integer> byStatus = new TreeMap<>();
        for (LogEvent event : events) {
            byStatus.merge(event.status(), 1, Integer::sum);
        }

        return new Statistics(events.size(), countByIp.size() * 10, topIps, byHour, byStatus);
    }
}