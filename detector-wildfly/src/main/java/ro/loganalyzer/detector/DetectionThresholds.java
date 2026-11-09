package ro.loganalyzer.detector;


public record DetectionThresholds(
        int bruteForceThreshold,
        int bruteForceWindowSeconds,
        int scanThreshold,
        int scanWindowSeconds,
        int highVolumeMinRequests,
        int highVolumeFactor) {

    public static DetectionThresholds defaults() {
        return new DetectionThresholds(10, 60, 20, 60, 100, 5);
    }
}