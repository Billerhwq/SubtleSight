package com.subtlesight.connectors;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

public final class SsrfGuard {
    private static final List<String> METADATA_HOSTS = List.of("169.254.169.254", "metadata.google.internal", "metadata.azure.internal");
    private SsrfGuard() {}
    public static void requirePublicHttp(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) throw new SecurityException("absolute URL required");
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw new SecurityException("only http(s) is permitted");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || METADATA_HOSTS.contains(host)) throw new SecurityException("private destination blocked");
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) throw new SecurityException("host did not resolve");
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress() || isCarrierGradeNat(address.getAddress()))
                    throw new SecurityException("private destination blocked");
            }
        } catch (UnknownHostException ex) { throw new SecurityException("host cannot be resolved", ex); }
    }
    private static boolean isCarrierGradeNat(byte[] bytes) {
        return bytes.length == 4 && (bytes[0] & 0xff) == 100 && ((bytes[1] & 0xff) >= 64 && (bytes[1] & 0xff) <= 127);
    }
}

