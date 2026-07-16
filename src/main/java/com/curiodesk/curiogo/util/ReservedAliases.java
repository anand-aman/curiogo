package com.curiodesk.curiogo.util;

import java.util.Set;

/**
 * Blocklist of custom aliases that would otherwise shadow real application routes.
 *
 * <p>A custom alias like {@code api}, {@code health}, or {@code actuator} would
 * collide with a live endpoint, so the service rejects any reserved alias with
 * HTTP 422. The check is case-insensitive.
 *
 * <p>Final class with a private constructor: this is a pure static utility and must
 * never be instantiated.
 */
public final class ReservedAliases {

    private static final Set<String> RESERVED = Set.of(
            "api", "admin", "login", "logout", "health", "actuator",
            "swagger-ui", "v3", "static", "assets", "favicon.ico"
    );

    private ReservedAliases() {
        // no instances
    }

    /**
     * @param alias the candidate custom alias
     * @return {@code true} if the alias is reserved and must be rejected
     */
    public static boolean isReserved(String alias) {
        return RESERVED.contains(alias.toLowerCase());
    }
}
