package com.curiodesk.curiogo.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReservedAliasesTest {

    @ParameterizedTest
    @DisplayName("every reserved word is rejected")
    @ValueSource(strings = {
            "api", "admin", "login", "logout", "health", "actuator",
            "swagger-ui", "v3", "static", "assets", "favicon.ico"
    })
    void rejectsReservedWords(String alias) {
        assertThat(ReservedAliases.isReserved(alias)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("reserved check is case-insensitive")
    @ValueSource(strings = {"API", "Admin", "HEALTH", "Actuator", "Swagger-UI", "V3", "Favicon.ICO"})
    void isCaseInsensitive(String alias) {
        assertThat(ReservedAliases.isReserved(alias)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("ordinary aliases are allowed")
    @ValueSource(strings = {"promo", "spring", "my-link", "abc123", "apiv2", "healthy", "adminx"})
    void allowsOrdinaryAliases(String alias) {
        assertThat(ReservedAliases.isReserved(alias)).isFalse();
    }

    @Test
    @DisplayName("match is a whole-word lookup, not a substring — \"apiv2\" contains \"api\" but is allowed")
    void matchesWholeWordNotSubstring() {
        assertThat(ReservedAliases.isReserved("apiv2")).isFalse();
        assertThat(ReservedAliases.isReserved("healthy")).isFalse();
    }
}
