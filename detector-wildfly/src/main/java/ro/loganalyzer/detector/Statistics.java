package ro.loganalyzer.detector;

import java.util.List;
import java.util.Map;

public record Statistics(
        int totalRequests,
        int uniqueIps,
        List<IpCount> topIps,
        Map<String, Integer> requestsByHour,
        Map<Integer, Integer> statusCounts) {

    public Statistics {
        topIps = List.copyOf(topIps);
        requestsByHour = Map.copyOf(requestsByHour);
        statusCounts = Map.copyOf(statusCounts);
    }

    public record IpCount(String ip, int requests) {
    }
}
