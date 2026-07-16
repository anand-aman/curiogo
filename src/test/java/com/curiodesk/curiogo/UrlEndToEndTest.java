package com.curiodesk.curiogo;

import com.curiodesk.curiogo.domain.CreateUrlRequest;
import com.curiodesk.curiogo.domain.CreateUrlResponse;
import com.curiodesk.curiogo.domain.Url;
import com.curiodesk.curiogo.repository.UrlRepository;
import com.curiodesk.curiogo.service.ClickCounter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.testcontainers.DockerClientFactory;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UrlEndToEndTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private UrlRepository repository;
    @Autowired private ClickCounter clickCounter;

    @BeforeAll
    static void requireDocker() {
        assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available - skipping the Testcontainers end-to-end test");
    }

    @BeforeEach
    void doNotFollowRedirects() {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory(client));
    }

    @Test
    @DisplayName("create -> persisted in real Postgres -> redirect 302 -> click counted -> unknown 404")
    void fullLifecycle() {
        // 1. CREATE: POST a long URL, expect 201 with a short code.
        CreateUrlRequest request =
                new CreateUrlRequest("https://example.com/very/long/path", null, null, null);

        ResponseEntity<CreateUrlResponse> createResponse =
                rest.postForEntity("/api/v1/urls", request, CreateUrlResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CreateUrlResponse body = createResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isNotBlank();
        assertThat(body.shortUrl()).endsWith("/" + body.code());

        // 2. The entity landed in Postgres, with the click count starting at zero.
        Url persisted = repository.findByShortCode(body.code()).orElseThrow();
        assertThat(persisted.getOriginalUrl()).isEqualTo("https://example.com/very/long/path");
        assertThat(persisted.getClickCount()).isZero();

        // 3. REDIRECT: GET /{code} returns 302 with the original URL in Location.
        //    TestRestTemplate does not follow redirects, so we can inspect the 302 itself.
        ResponseEntity<Void> redirect =
                rest.exchange("/" + body.code(), HttpMethod.GET, null, Void.class);

        assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirect.getHeaders().getLocation())
                .hasToString("https://example.com/very/long/path");

        // 4. The click was buffered async; force the flush and confirm the UPDATE hit Postgres.
        clickCounter.flush();
        Url afterClick = repository.findByShortCode(body.code()).orElseThrow();
        assertThat(afterClick.getClickCount()).isEqualTo(1);

        // 5. Unknown code -> 404 from the GlobalExceptionHandler.
        ResponseEntity<String> unknown =
                rest.exchange("/does-not-exist", HttpMethod.GET, null, String.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
