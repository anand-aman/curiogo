package com.curiodesk.curiogo.util;

import com.curiodesk.curiogo.exception.UnsafeUrlException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UrlSafetyValidatorTest {

    /** Default production posture: private/loopback targets are blocked. */
    private final UrlSafetyValidator strict = new UrlSafetyValidator(false);

    /** Relaxed posture (e.g. local testing): scheme check only. */
    private final UrlSafetyValidator lenient = new UrlSafetyValidator(true);

    @Nested
    @DisplayName("public URLs")
    class PublicUrls {

        @ParameterizedTest
        @DisplayName("normal http/https public targets pass")
        @ValueSource(
                strings = {
                        "https://example.com",
                        "http://example.com",
                        "https://example.com/path?q=1#frag",
                        "https://sub.domain.example.co.uk:8443/a/b",
                        "HTTPS://Example.Com", // scheme match is case-insensitive
                })
        void acceptsPublicUrls(String url) {
            assertThatCode(() -> strict.validate(url)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a public IPv4 literal passes")
        void acceptsPublicIpv4() {
            assertThatCode(() -> strict.validate("https://93.184.216.34/"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("malformed / bad-scheme / hostless URLs")
    class Rejections {

        @Test
        @DisplayName("a malformed URL is rejected")
        void rejectsMalformed() {
            assertThatThrownBy(() -> strict.validate("h ttp://exa mple.com"))
                    .isInstanceOf(UnsafeUrlException.class);
        }

        @ParameterizedTest
        @DisplayName("non-http(s) schemes are rejected (javascript/data/file/ftp)")
        @ValueSource(
                strings = {
                        "javascript:alert(1)",
                        "data:text/html;base64,PHNjcmlwdD4=",
                        "file:///etc/passwd",
                        "ftp://example.com/resource",
                })
        void rejectsDisallowedSchemes(String url) {
            assertThatThrownBy(() -> strict.validate(url))
                    .isInstanceOf(UnsafeUrlException.class);
        }

        @ParameterizedTest
        @DisplayName("a URL with no host is rejected")
        @ValueSource(strings = {"https:///path-only", "http://"})
        void rejectsMissingHost(String url) {
            assertThatThrownBy(() -> strict.validate(url))
                    .isInstanceOf(UnsafeUrlException.class);
        }
    }

    @Nested
    @DisplayName("private / loopback targets (SSRF guard)")
    class PrivateTargets {

        @ParameterizedTest
        @DisplayName("private, loopback and link-local hosts are rejected by default")
        @ValueSource(
                strings = {
                        "http://localhost:8080/actuator",
                        "http://127.0.0.1:8080/actuator",
                        "http://10.0.0.5/",
                        "http://172.16.4.9/",
                        "http://192.168.1.1/",
                        "http://169.254.169.254/latest/meta-data", // cloud metadata pivot
                        "http://[::1]:9000/",
                })
        void rejectsPrivateTargets(String url) {
            assertThatThrownBy(() -> strict.validate(url))
                    .isInstanceOf(UnsafeUrlException.class);
        }

        @ParameterizedTest
        @DisplayName("with allow-private-targets=true the same hosts pass")
        @ValueSource(
                strings = {
                        "http://localhost:8080/actuator",
                        "http://127.0.0.1:8080/actuator",
                        "http://192.168.1.1/",
                        "http://[::1]:9000/",
                })
        void allowsPrivateTargetsWhenConfigured(String url) {
            assertThatCode(() -> lenient.validate(url)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("scheme check still applies even when private targets are allowed")
        void lenientStillRejectsBadScheme() {
            assertThatThrownBy(() -> lenient.validate("javascript:alert(1)"))
                    .isInstanceOf(UnsafeUrlException.class);
        }
    }
}
