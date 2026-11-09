package ro.loganalyzer.detector;

import java.util.List;

public record DetectRequest(List<LogEvent> events, DetectionThresholds thresholds) {
}