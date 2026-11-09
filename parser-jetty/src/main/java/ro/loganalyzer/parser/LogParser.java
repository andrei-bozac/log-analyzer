package ro.loganalyzer.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogParser {

    private static final int MAX_LINE_LENGTH = 8192;

    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^(\\S{1,45}) \\S+ \\S+ \\[([^\\]]{1,40})\\] "
                    + "\"([A-Z]{1,16}) (\\S{1,2048}) [^\"]{0,32}\" "
                    + "(\\d{3}) (\\d{1,18}|-)(?: .*)?$");

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

    public ParseResult parse(BufferedReader reader) throws IOException {
        List<LogEvent> events = new ArrayList<>();
        int skipped = 0;

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            Optional<LogEvent> event = parseLine(line);
            if (event.isPresent()) {
                events.add(event.get());
            } else {
                skipped++;
            }
        }
        return new ParseResult(events, skipped);
    }

    Optional<LogEvent> parseLine(String line) {
        if (line.length() > MAX_LINE_LENGTH) {
            return Optional.empty();
        }

        Matcher matcher = LINE_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        try {
            Instant timestamp = OffsetDateTime
                    .parse(matcher.group(2), TIMESTAMP_FORMAT)
                    .toInstant();
            int status = Integer.parseInt(matcher.group(5));
            String bytesText = matcher.group(6);
            long bytes = "-".equals(bytesText) ? 0L : Long.parseLong(bytesText);

            return Optional.of(new LogEvent(
                    matcher.group(1),
                    timestamp,
                    matcher.group(3),
                    matcher.group(4),
                    status,
                    bytes));
        } catch (DateTimeException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}