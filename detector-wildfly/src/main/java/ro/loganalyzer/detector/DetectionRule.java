package ro.loganalyzer.detector;

import java.util.List;

public interface DetectionRule {

    List<Alert> evaluate(List<LogEvent> events);
}