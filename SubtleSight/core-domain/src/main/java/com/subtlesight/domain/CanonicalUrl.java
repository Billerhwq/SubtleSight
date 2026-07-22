package com.subtlesight.domain;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Deterministic URL canonicalization used by discovery, fetch and dedup. */
public final class CanonicalUrl {
    private static final Set<String> TRACKING = Set.of("fbclid", "gclid", "dclid", "mc_cid", "mc_eid", "ref", "ref_src");
    private CanonicalUrl() {}

    public static String normalize(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("url must not be blank");
        try {
            String candidate = input.trim();
            if (!candidate.matches("(?i)^[a-z][a-z0-9+.-]*://.*$")) candidate = "https://" + candidate;
            URI raw = new URI(candidate);
            String scheme = raw.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) throw new IllegalArgumentException("unsupported url scheme");
            String host = raw.getHost();
            if (host == null && raw.getRawAuthority() != null) {
                String authority = raw.getRawAuthority();
                int user = authority.lastIndexOf('@'); if (user >= 0) authority = authority.substring(user + 1);
                int colon = authority.lastIndexOf(':'); if (colon > 0 && authority.indexOf(':') == colon) authority = authority.substring(0, colon);
                host = authority;
            }
            if (host == null) throw new IllegalArgumentException("url has no host");
            host = IDN.toASCII(host.toLowerCase(Locale.ROOT));
            int port = raw.getPort();
            if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) port = -1;
            String path = raw.normalize().getRawPath();
            if (path == null || path.isBlank()) path = "/";
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            String query = normalizeQuery(raw.getRawQuery());
            StringBuilder normalized = new StringBuilder().append(scheme).append("://");
            if (raw.getRawUserInfo() != null && !raw.getRawUserInfo().isBlank()) normalized.append(raw.getRawUserInfo()).append('@');
            normalized.append(host);
            if (port >= 0) normalized.append(':').append(port);
            normalized.append(path);
            if (query != null) normalized.append('?').append(query);
            return URI.create(normalized.toString()).toASCIIString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("invalid url", ex);
        }
    }

    private static String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return null;
        List<String> parts = new ArrayList<>(Arrays.asList(rawQuery.split("&")));
        parts.removeIf(part -> {
            String key = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
            return key.startsWith("utm_") || TRACKING.contains(key);
        });
        parts.sort(Comparator.naturalOrder());
        return parts.isEmpty() ? null : String.join("&", parts);
    }
}
