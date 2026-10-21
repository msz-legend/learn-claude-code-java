package harness;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Small blocking JSON-over-HTTP helper shared by both protocol clients. */
public final class Http {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private Http() {
    }

    public static String postJson(String url, Map<String, String> headers, String body, int timeoutSeconds)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(builder::header);

        HttpResponse<String> response = CLIENT.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new HttpException(response.statusCode(), response.body());
        }
        return response.body();
    }

    public static final class HttpException extends IOException {
        private final int status;
        private final String body;

        public HttpException(int status, String body) {
            super("HTTP " + status + ": " + shorten(body));
            this.status = status;
            this.body = body;
        }

        public int status() {
            return status;
        }

        public String body() {
            return body;
        }

        private static String shorten(String text) {
            if (text == null) {
                return "";
            }
            String oneLine = text.replace('\n', ' ').trim();
            return oneLine.length() > 800 ? oneLine.substring(0, 800) + "..." : oneLine;
        }
    }
}
