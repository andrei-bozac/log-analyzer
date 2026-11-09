package ro.loganalyzer.parser;

import java.util.List;

public record ParseResult(List<LogEvent> events, int skippedLines) {

    public ParseResult {
        events = List.copyOf(events);
        if (skippedLines < 0) {
            throw new IllegalArgumentException("numar negativ de randuri ignorate");
        }
    }
}