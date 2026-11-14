package pl.rynek.search;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL (historia i zapisane wyszukiwania) oraz OpenSearch (indeks) w kontenerach dla testów; te same wersje co w
 * rynek-infra/local. Wymaga działającego Dockera.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }

    @Bean
    GenericContainer<?> opensearch() {
        return new GenericContainer<>("opensearchproject/opensearch:2.19.1")
                .withEnv(Map.of("discovery.type", "single-node", "DISABLE_SECURITY_PLUGIN", "true", "DISABLE_INSTALL_DEMO_CONFIG", "true",
                        "OPENSEARCH_JAVA_OPTS", "-Xms256m -Xmx256m"))
                .withExposedPorts(9200)
                .waitingFor(Wait.forHttp("/_cluster/health").forPort(9200).forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(3));
    }

    @Bean
    DynamicPropertyRegistrar opensearchUrl(GenericContainer<?> opensearch) {
        return registry -> registry.add("rynek.opensearch.url", () -> "http://" + opensearch.getHost() + ":" + opensearch.getMappedPort(9200));
    }
}
