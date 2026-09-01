package dev.singlehope.free.shpix.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class JsonHttpClient implements AutoCloseable {

    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    private final HttpClient client;
    private final ExecutorService executor;
    private final Duration timeout;
    private final RateLimiter limiter;
    private final Set<String> allowedHosts;

    public JsonHttpClient(final String threadName, final Duration timeout, final int requestsPerMinute,
                          final Set<String> allowedHosts) {
        this.timeout = timeout;
        this.limiter = new RateLimiter(requestsPerMinute);
        this.allowedHosts = Set.copyOf(allowedHosts);
        this.executor = Executors.newFixedThreadPool(2, namedFactory(threadName));
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .executor(this.executor)
                .build();
    }

    private static ThreadFactory namedFactory(final String threadName) {
        final AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            final Thread thread = new Thread(runnable, threadName + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    public JsonResponse post(final URI uri, final String body, final Map<String, String> headers) throws HttpException {
        return send(builder(uri, headers)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json; charset=utf-8"));
    }

    public JsonResponse get(final URI uri, final Map<String, String> headers) throws HttpException {
        return send(builder(uri, headers).GET());
    }

    private HttpRequest.Builder builder(final URI uri, final Map<String, String> headers) throws HttpException {
        validate(uri);
        final HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(this.timeout)
                .header("Accept", "application/json")
                .header("User-Agent", "ShPIX");
        headers.forEach((key, value) -> {
            if (isSafeHeader(key, value)) {
                builder.header(key, value);
            }
        });
        return builder;
    }

    private void validate(final URI uri) throws HttpException {
        if (uri.getScheme() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new HttpException("Somente URLs HTTPS são permitidas.");
        }
        final String host = uri.getHost();
        if (host == null || !this.allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
            throw new HttpException("Host não permitido para esta integração.");
        }
    }

    private static boolean isSafeHeader(final String key, final String value) {
        if (key == null || value == null || key.isBlank()) {
            return false;
        }
        return key.chars().noneMatch(c -> c == '\r' || c == '\n')
                && value.chars().noneMatch(c -> c == '\r' || c == '\n');
    }

    private JsonResponse send(final HttpRequest.Builder builder) throws HttpException {
        if (!this.limiter.tryAcquire()) {
            throw new HttpException("Limite de requisições atingido; tente novamente em instantes.");
        }
        try {
            final HttpResponse<InputStream> response = this.client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            final String body = readBounded(response.body());
            return new JsonResponse(response.statusCode(), parse(body));
        } catch (InterruptedIOException exception) {
            throw new HttpException("Tempo limite excedido ao contatar o provedor de pagamento.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HttpException("Requisição interrompida.");
        } catch (IOException exception) {
            throw new HttpException("Falha de comunicação com o provedor de pagamento.");
        }
    }

    private static String readBounded(final InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (stream) {
            final byte[] buffer = new byte[8192];
            final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int read;
            while ((read = stream.read(buffer)) != -1) {
                if (out.size() + read > MAX_RESPONSE_BYTES) {
                    out.write(buffer, 0, Math.max(0, MAX_RESPONSE_BYTES - out.size()));
                    break;
                }
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private static JsonObject parse(final String body) {
        if (body == null || body.isBlank()) {
            return new JsonObject();
        }
        try {
            final JsonElement element = JsonParser.parseString(body);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (JsonSyntaxException | IllegalStateException ignored) {
            return new JsonObject();
        }
    }

    @Override
    public void close() {
        this.executor.shutdownNow();
        try {
            this.executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public record JsonResponse(int status, JsonObject body) {

        public boolean isSuccessful() {
            return this.status >= 200 && this.status < 300;
        }
    }
}
