# Log Analyzer

A distributed web access log analyzer built with Jakarta Servlets and deployed
across three different servlet containers: Apache Tomcat, Eclipse Jetty and WildFly.

Course project for "Java Technologies and Platforms for Distributed Applications".

## Overview

Web servers record every request they receive in an access log. A production log can
contain hundreds of thousands of lines, and signs of malicious activity are hard to spot
by reading it manually. Log Analyzer takes a log file uploaded by the user, extracts the
requests, applies a set of detection rules and presents the findings in a web interface,
together with traffic statistics.

The application is split into three independent services that communicate over HTTP and
exchange JSON, in the manner of microservices. Each service runs in a different container.

## Features

- Upload of access logs in Common Log Format or Combined Log Format (`.log` or `.txt`, up to 10 MB)
- Four detection rules:
    - **Brute force**: many failed authentications (HTTP 401) from one address within a time window
    - **Scanning**: many requests for non-existent pages (HTTP 404) from one address within a time window
    - **Attack patterns**: path traversal, SQL injection and cross-site scripting signatures in request URLs
    - **High volume**: an address that sends far more requests than the typical address (median based)
- Alert severity levels (LOW, MEDIUM, HIGH)
- Configurable detection thresholds from the user interface
- Traffic statistics: requests per hour, status code distribution, most active addresses
- Built-in sample file for a quick demonstration
- Live status indicators for the three services

## Architecture

![Architecture diagram](docs/arhitectura.png)

| Service  | Container            | Port | Context path | Responsibility                                   |
|----------|----------------------|------|--------------|--------------------------------------------------|
| gateway  | Tomcat 11.0.18       | 8080 | `/gateway`   | Web interface, request validation, orchestration |
| parser   | Jetty 12.1.14        | 8081 | `/parser`    | Converts raw log lines into structured events    |
| detector | WildFly 42.0.0.Final | 8082 | `/detector`  | Applies detection rules and computes statistics  |

The gateway is the only entry point. The parser and the detector never call each other.

Request flow:

1. The browser sends the file and the thresholds to the gateway (`multipart/form-data`).
2. The gateway validates the request and sends the file content to the parser (`text/plain`).
3. The parser returns the parsed events and the number of skipped lines (JSON).
4. The gateway sends the events and the thresholds to the detector (JSON).
5. The detector returns the alerts and the statistics (JSON).
6. The gateway combines the results and returns them to the browser.

## Technology

- Java 21, Jakarta Servlet 6.0
- Maven (multi-module build, WAR packaging)
- Gson for JSON processing
- `java.net.http.HttpClient` for communication between services
- Plain HTML, CSS and JavaScript for the interface (no frameworks)
- PowerShell scripts for starting, stopping and redeploying the servers

## Prerequisites

- Windows with PowerShell
- JDK 21 or newer, with `JAVA_HOME` set
- Apache Maven
- Apache Tomcat 11.0.18, Eclipse Jetty 12.1.14 and WildFly 42.0.0.Final, extracted under `C:\servers`
  (the paths and ports can be changed in `tools/config.ps1`)

Server configuration:

- **Tomcat**: default configuration, port 8080.
- **Jetty**: a Jetty base directory (`jetty-base`) with HTTP on port 8081 and the EE10
  deployment and annotation modules enabled (`ee10-deploy`, `ee10-annotations`). The annotations
  module is required for `@WebServlet` discovery.
- **WildFly**: standalone mode, started with `-Djboss.socket.binding.port-offset=2`, which
  moves the HTTP port to 8082.

## Build and run

From the project root:

```powershell
# Build, stop the servers, copy the WAR files, start the servers
powershell -ExecutionPolicy Bypass -File .\tools\redeploy.ps1

# Start the three servers and wait until all health checks pass
powershell -ExecutionPolicy Bypass -File .\tools\start-all.ps1

# Stop the three servers
powershell -ExecutionPolicy Bypass -File .\tools\stop-all.ps1
```

`start-all.ps1` opens each server in its own window and checks the `/health` endpoint of
every service. When it prints `[ OK ]` for all three, open `http://localhost:8080/gateway/`.

To build only, run `mvn clean package`. This produces `gateway.war`, `parser.war` and
`detector.war` in the `target` directory of each module.

## Usage

1. Open `http://localhost:8080/gateway/`.
2. Check that the three service indicators in the header are online.
3. Choose a log file and press **Analyze**, or press **Use sample file**.
4. Optionally expand **Detection thresholds**, change the values and analyze again.
5. Read the summary cards, the alerts table and the charts.

Default thresholds:

| Rule        | Threshold          | Window     |
|-------------|--------------------|------------|
| Brute force | 10 failed requests | 60 seconds |
| Scanning    | 20 not-found requests | 60 seconds |
| High volume | at least 100 requests and 5 times the median | whole file |

Alert severity grows with the ratio between the observed count and the threshold.

## HTTP interface

| Method and path               | Description                                      |
|-------------------------------|--------------------------------------------------|
| `POST /gateway/api/analyze`   | Analyze an uploaded log (multipart: `file` and six optional threshold fields) |
| `GET  /gateway/api/status`    | Availability of the parser and the detector      |
| `GET  /gateway/api/sample`    | The bundled sample log                           |
| `POST /parser/parse`          | Parse a log (`text/plain`), internal             |
| `POST /detector/detect`       | Run the detection rules (JSON), internal         |
| `GET  /{service}/health`      | Health check of each service                     |

Example:

```powershell
curl.exe -X POST -F "file=@access.log" -F "bruteForceThreshold=5" http://localhost:8080/gateway/api/analyze
```

## Configuration

The gateway reads the addresses of the other services from environment variables and falls
back to local defaults:

| Variable       | Default                          |
|----------------|----------------------------------|
| `PARSER_URL`   | `http://localhost:8081/parser`   |
| `DETECTOR_URL` | `http://localhost:8082/detector` |

## Security considerations

- Size limits enforced at every layer (container, gateway, services) and bounded reads of
  uploads and service responses
- Input validated independently by each service
- Fixed error messages that never echo user input or internal details
- Regular expressions with bounded quantifiers, to prevent catastrophic backtracking
- Service addresses come only from configuration, and HTTP redirects are disabled
- Strict Content Security Policy and other security headers on every response
- Log content is rendered as plain text in the interface

## Limitations

- The detection rules are heuristics and can produce false positives or miss distributed attacks
- Logs are analyzed from uploaded files of up to 10 MB, not in real time
- No authentication between services and no TLS (intended for local use)

## Project structure

```
log-analyzer/
  gateway-tomcat/    Tomcat module: web interface and orchestration
  parser-jetty/      Jetty module: log parser
  detector-wildfly/  WildFly module: detection rules and statistics
  tools/             Start, stop and redeploy scripts, sample log generator
  docs/              Documentation and diagrams
  pom.xml            Parent Maven project
```

## Author

Bozac Andrei