package com.curiodesk.curiogo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
public class CurioGoApplicationTests {

    @BeforeAll
    static void requireDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available - skipping the Testcontainers-backed context load smoke test");
    }

    @Test
    void contextLoads() {
    }
}
