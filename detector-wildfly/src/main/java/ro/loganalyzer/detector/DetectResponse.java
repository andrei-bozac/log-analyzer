package ro.loganalyzer.detector;

import java.util.List;

public record DetectResponse(List<Alert> alerts, Statistics statistics) {
}