package com.subtlesight.connectors;

import com.subtlesight.connectors.SourceConnector.ExternalReference;
import com.subtlesight.connectors.SourceConnector.RawPayload;

import java.io.IOException;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SafeHttpClient {
    private final HttpClient primaryClient;
    private final HttpClient fallbackDirectClient;
    private final Duration timeout;
    private final int maxRedirects;
    private final int maxBytes;
    private final java.util.function.Consumer<URI> destinationGuard;
    private final Duration minDelayPerHost;
    private final Map<String,Long> hostLastRequest = new ConcurrentHashMap<>();
    public SafeHttpClient(Duration timeout, int maxRedirects, int maxBytes) {
        this(timeout,maxRedirects,maxBytes,Duration.ZERO);
    }
    public SafeHttpClient(Duration timeout, int maxRedirects, int maxBytes, Duration minDelayPerHost) {
        this(timeout,maxRedirects,maxBytes,minDelayPerHost,SsrfGuard::requirePublicHttp);
    }
    SafeHttpClient(Duration timeout,int maxRedirects,int maxBytes,java.util.function.Consumer<URI> destinationGuard){
        this(timeout,maxRedirects,maxBytes,Duration.ZERO,destinationGuard);
    }
    SafeHttpClient(Duration timeout,int maxRedirects,int maxBytes,Duration minDelayPerHost,java.util.function.Consumer<URI> destinationGuard){
        Optional<ProxySelector> proxySelector = EnvironmentProxySelector.fromEnvironment();
        this.primaryClient = proxySelector.map(selector -> baseBuilder(timeout).proxy(selector).build()).orElseGet(() -> baseBuilder(timeout).build());
        this.fallbackDirectClient = proxySelector.isPresent() ? baseBuilder(timeout).build() : null;
        this.timeout=timeout;this.maxRedirects=maxRedirects;this.maxBytes=maxBytes;this.minDelayPerHost=minDelayPerHost==null?Duration.ZERO:minDelayPerHost;this.destinationGuard=destinationGuard;
    }
    public RawPayload fetch(ExternalReference reference) {
        try {
            URI current = reference.uri();
            List<URI> chain = new ArrayList<>();
            for (int redirects = 0; redirects <= maxRedirects; redirects++) {
                destinationGuard.accept(current);
                waitForHost(current);
                HttpRequest request = HttpRequest.newBuilder(current).timeout(timeout)
                        .header("User-Agent", "SubtleSight/1.0 (+local intelligence reader)")
                        .header("Accept", "text/html,application/json,application/xml,application/rss+xml,application/pdf,text/plain;q=0.9,*/*;q=0.1")
                        .GET().build();
                HttpResponse<byte[]> response = send(request);
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    String location = response.headers().firstValue("location").orElseThrow(() -> new IllegalStateException("redirect without location"));
                    chain.add(current); current = current.resolve(location); continue;
                }
                return new RawPayload(reference, current, response.statusCode(), response.headers().firstValue("content-type").orElse("application/octet-stream"),
                        response.body(), response.headers().firstValue("etag").orElse(null), response.headers().firstValue("last-modified").orElse(null), List.copyOf(chain));
            }
            throw new IllegalStateException("too many redirects");
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("fetch interrupted", ex); }
        catch (Exception ex) { throw new IllegalStateException("fetch failed", ex); }
    }

    private static HttpClient.Builder baseBuilder(Duration timeout) {
        return HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER);
    }

    private HttpResponse<byte[]> send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse.BodyHandler<byte[]> handler = info -> HttpResponse.BodySubscribers.mapping(
                HttpResponse.BodySubscribers.ofByteArray(), bytes -> {
                    if (bytes.length > maxBytes) throw new IllegalArgumentException("response exceeds limit");
                    return bytes;
                });
        try {
            return primaryClient.send(request, handler);
        } catch (IOException ex) {
            if (fallbackDirectClient == null) throw ex;
            try {
                return fallbackDirectClient.send(request, handler);
            } catch (IOException retryEx) {
                retryEx.addSuppressed(ex);
                throw retryEx;
            }
        }
    }

    private void waitForHost(URI uri) throws InterruptedException {
        if (minDelayPerHost.isZero() || minDelayPerHost.isNegative() || uri.getHost() == null) return;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        synchronized (hostLastRequest) {
            long now = System.nanoTime();
            long delayNanos = minDelayPerHost.toNanos();
            Long last = hostLastRequest.get(host);
            if (last != null) {
                long elapsed = now - last;
                long remaining = delayNanos - elapsed;
                if (remaining > 0) {
                    long millis = Math.max(1, Duration.ofNanos(remaining).toMillis());
                    Thread.sleep(millis);
                    now = System.nanoTime();
                }
            }
            hostLastRequest.put(host, now);
        }
    }

    static final class EnvironmentProxySelector extends ProxySelector {
        private final Proxy httpProxy;
        private final Proxy httpsProxy;
        private final Proxy allProxy;
        private final String noProxy;

        private EnvironmentProxySelector(Proxy httpProxy, Proxy httpsProxy, Proxy allProxy, String noProxy) {
            this.httpProxy = httpProxy;
            this.httpsProxy = httpsProxy;
            this.allProxy = allProxy;
            this.noProxy = noProxy == null ? "" : noProxy;
        }

        static Optional<ProxySelector> fromEnvironment() {
            Proxy http = parseProxy(firstEnv("HTTP_PROXY", "http_proxy"));
            Proxy https = parseProxy(firstEnv("HTTPS_PROXY", "https_proxy"));
            Proxy all = parseProxy(firstEnv("ALL_PROXY", "all_proxy"));
            if (http == null && https == null && all == null) return Optional.empty();
            return Optional.of(new EnvironmentProxySelector(http, https, all, firstEnv("NO_PROXY", "no_proxy")));
        }

        @Override
        public List<Proxy> select(URI uri) {
            if (uri == null || shouldBypass(uri)) return List.of(Proxy.NO_PROXY);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            Proxy proxy = switch (scheme) {
                case "https" -> httpsProxy != null ? httpsProxy : allProxy;
                case "http" -> httpProxy != null ? httpProxy : allProxy;
                default -> null;
            };
            return List.of(proxy == null ? Proxy.NO_PROXY : proxy);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, java.io.IOException ioe) {
            // Nothing to update: proxy configuration is environment-provided.
        }

        private boolean shouldBypass(URI uri) {
            String host = uri.getHost();
            if (host == null || host.isBlank()) return true;
            String lower = host.toLowerCase(Locale.ROOT);
            if (lower.equals("localhost") || lower.equals("::1") || lower.endsWith(".local")) return true;
            if (lower.startsWith("127.") || lower.equals("0.0.0.0") || lower.startsWith("10.") || lower.startsWith("192.168.") || lower.startsWith("169.254.")) return true;
            if (isPrivate172(lower)) return true;
            if (noProxy.isBlank()) return false;
            int port = uri.getPort();
            for (String raw : noProxy.split(",")) {
                String rule = raw.trim().toLowerCase(Locale.ROOT);
                if (rule.isBlank()) continue;
                if (rule.equals("*")) return true;
                if (port >= 0 && rule.equals(lower + ":" + port)) return true;
                if (rule.startsWith(".")) {
                    if (lower.endsWith(rule)) return true;
                } else if (lower.equals(rule) || lower.endsWith("." + rule)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isPrivate172(String host) {
            if (!host.startsWith("172.")) return false;
            String[] parts = host.split("\\.");
            if (parts.length < 2) return false;
            try {
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        private static Proxy parseProxy(String value) {
            if (value == null || value.isBlank()) return null;
            try {
                String normalized = value.contains("://") ? value : "http://" + value;
                URI uri = URI.create(normalized);
                String host = uri.getHost();
                if (host == null || host.isBlank()) return null;
                int port = uri.getPort();
                if (port < 0) port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
                return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port));
            } catch (Exception ignored) {
                return null;
            }
        }

        private static String firstEnv(String... keys) {
            Map<String,String> env = System.getenv();
            for (String key : keys) {
                String value = env.get(key);
                if (value != null && !value.isBlank()) return value;
                for (Map.Entry<String,String> entry : env.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(key) && entry.getValue() != null && !entry.getValue().isBlank()) return entry.getValue();
                }
            }
            return null;
        }
    }
}
