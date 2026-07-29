package com.curiodesk.curiogo.util;

import com.curiodesk.curiogo.exception.UnsafeUrlException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.*;
import java.util.Locale;
import java.util.Set;

@Component
public class UrlSafetyValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final boolean allowPrivateTargets;

    public UrlSafetyValidator(@Value("${app.url-safety.allow-private-targets:false}") boolean allowPrivateTargets) {
        this.allowPrivateTargets = allowPrivateTargets;
    }

    public void validate(String url) {
        URI uri;

        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new UnsafeUrlException("URL is malformed.");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new UnsafeUrlException("URL scheme must be http or https.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UnsafeUrlException("URL host is missing or invalid.");
        }

        if (allowPrivateTargets) {
            return;
        }
        if (isPrivateOrLoopback(host)) {
            throw new UnsafeUrlException("URL targets a private or loopback address.");
        }
    }

    private boolean isPrivateOrLoopback(String host) {
        if (host.equalsIgnoreCase("localhost")) {
            return true;
        }
        String h = host;
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length()-1); // strip IPv6 brackets [::1] -> ::1
        }

        InetAddress addr = parseIpLiteral(h);
        if (addr == null) {
            return false;
        }

        return addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress() || addr.isAnyLocalAddress();
    }

    private InetAddress parseIpLiteral(String h) {
        boolean looksIpv4 = h.matches("\\d{1,3}(\\.\\d{1,3}){3}");
        boolean looksIpv6 = h.indexOf(':') >=0;
        if (!looksIpv4 && !looksIpv6) {
            return null;
        }

        try {
            return InetAddress.getByName(h);
        } catch(UnknownHostException e) {
            return null;
        }
    }


}
