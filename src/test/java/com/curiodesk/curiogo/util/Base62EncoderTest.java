package com.curiodesk.curiogo.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link Base62Encoder}. Pure logic, no Spring context needed.
 */
class Base62EncoderTest {

    private final Base62Encoder encoder = new Base62Encoder();

    @Test
    @DisplayName("id 0 encodes to \"0\"")
    void encodesZero() {
        assertThat(encoder.encode(0)).isEqualTo("0");
    }

    @Test
    @DisplayName("known small ids map to expected Base62 digits")
    void encodesKnownValues() {
        assertThat(encoder.encode(1)).isEqualTo("1");
        assertThat(encoder.encode(61)).isEqualTo("Z"); // last symbol in the alphabet
        assertThat(encoder.encode(62)).isEqualTo("10"); // first two-digit value
    }

    @ParameterizedTest
    @DisplayName("encode then decode returns the original id (round-trip)")
    @ValueSource(longs = {0L, 1L, 61L, 62L, 1_000_000L, 9_999_999L, Long.MAX_VALUE})
    void roundTrips(long id) {
        String code = encoder.encode(id);
        assertThat(encoder.decode(code)).isEqualTo(id);
    }

    @Test
    @DisplayName("large id encodes to a compact, non-empty code")
    void encodesLargeId() {
        String code = encoder.encode(Long.MAX_VALUE);
        assertThat(code).isNotBlank();
        // 9,223,372,036,854,775,807 in Base62 is only 11 characters — the whole point.
        assertThat(code.length()).isLessThanOrEqualTo(11);
    }
}
