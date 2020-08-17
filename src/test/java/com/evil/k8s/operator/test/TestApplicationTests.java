package com.evil.k8s.operator.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.snowdrop.istio.api.networking.v1alpha3.EnvoyFilter;
import me.snowdrop.istio.api.networking.v1alpha3.WorkloadSelector;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;


//@SpringBootTest
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestApplicationTests extends K8sRateLimitAbstractTest {

    private final ObjectMapper yamlMapper;

    private final String namespace = "test-project";

    private final String rateLimiterName = "rate-limiter-test";

    public TestApplicationTests() {
        yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    @Test
    @Order(1)
    @SneakyThrows
    public void createRateLimiter() {
        client.customResource(rateLimitCrdContext)
                .create(namespace, yamlMapper.writeValueAsString(preparedRateLimiter()));
        TimeUnit.SECONDS.sleep(10);
        Map<String, Object> stringObjectMap =
                client.customResource(rateLimitCrdContext).get(namespace, rateLimiterName);
        assertNotNull(stringObjectMap);
    }

    @Test
    @Order(2)
    void rateLimiter() {
        Map<String, Object> stringObjectMap = client.customResource(rateLimitCrdContext).get(namespace, rateLimiterName);
        ObjectMapper objectMapper = Serialization.jsonMapper();
        RateLimiter rateLimiter = objectMapper.convertValue(stringObjectMap, RateLimiter.class);
        System.out.println(rateLimiter.getSpec());
    }

    @Test
    @Order(3)
    void envoyFilter() {
        Map<String, Object> stringObjectMap = client.customResource(envoyFilterContext).get(namespace, "host-info-srv-config");
        EnvoyFilter envoyFilter = yamlMapper.convertValue(stringObjectMap, EnvoyFilter.class);
        System.out.println(envoyFilter);
    }

    @Test
    @Order(4)
    void deploymentRateLimiter() {
        DeploymentList list = client.apps().deployments().list();
        System.out.println(list.getItems().stream().filter(deployment -> deployment.getMetadata().getName().equals(rateLimiterName)).findFirst().get());
    }

    @Test
    @Order(5)
    void deploymentRedis() {
        DeploymentList list = client.apps().deployments().list();
        System.out.println(list.getItems().stream()
                .filter(deployment -> deployment.getMetadata().getName().equals(rateLimiterName +"-redis"))
                .findFirst().get());
    }

    @Test
    @Order(6)
    @SneakyThrows
    void removeRateLimit() {
        client.customResource(rateLimitCrdContext).delete(namespace, rateLimiterName);
        assertNull(client.customResource(rateLimitCrdContext).get(namespace, rateLimiterName));
    }

    @Test
    @SneakyThrows
    private RateLimiter preparedRateLimiter() {
        ObjectMeta objectMeta = new ObjectMeta();
        objectMeta.setName(rateLimiterName);
        objectMeta.setNamespace(namespace);

        WorkloadSelector workloadSelector = new WorkloadSelector(Collections.singletonMap("app", "application-app"));

        RateLimiter.RateLimiterSpec rateLimiterSpec = new RateLimiter.RateLimiterSpec();
        rateLimiterSpec.setPort(1488);
        rateLimiterSpec.setLogLevel("INFO");

        return RateLimiter.builder()
                .metadata(objectMeta)
                .workloadSelector(workloadSelector)
                .spec(rateLimiterSpec)
                .build();
    }

}
