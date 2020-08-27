package com.evil.k8s.operator.test;

import com.evil.k8s.operator.test.models.RateLimiter;
import com.evil.k8s.operator.test.models.RateLimiterConfig;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.snowdrop.istio.api.networking.v1alpha3.WorkloadSelector;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Collections;

import static com.evil.k8s.operator.test.models.RateLimiterConfig.Context.GATEWAY;
import static com.evil.k8s.operator.test.models.RateLimiterConfig.Context.SIDECAR_INBOUND;


//@SpringBootTest
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RateLimitTest extends K8sRateLimitAbstractTest {

    private final String namespace = "test-project";
    private final String rateLimiterName = "rate-limiter-test";

    @Test
    @Order(1)
    public void createRateLimiter() {
        RateLimiter rateLimiter = preparedRateLimiter();
        RateLimiterConfig rateLimiterConfig = preparedRateLimiterConfig();
        K8sRequester requester = new K8sRequester(client, namespace);
        try (
                RateLimiterProcessor rateLimiterProcessor = new RateLimiterProcessor(requester);
                RateLimiterConfigProcessor rateLimiterConfigProcessor = new RateLimiterConfigProcessor(requester);
        ) {
            rateLimiterProcessor
                    .create(rateLimiter)
                    .validateRateLimiter()
                    .validateRateLimiterDeployment()
                    .validateRedisDeployment()
                    .validateConfigMap()
                    .validateService()
                    .edit($rateLimiter -> $rateLimiter
                            .updateSpec(rateLimiterSpec -> rateLimiterSpec.setLogLevel("WARNING")))
                    .validateRateLimiterDeployment();

            rateLimiterConfigProcessor
                    .create(rateLimiterConfig)
                    .validateRatelimiterConfig()
                    .validateConfigMap()
                    .validateEnvoyFilter()
                    .edit(rlConfig -> rlConfig
                            .updateSpec(rateLimiterConfigSpec -> {
                                rateLimiterConfigSpec.setApplyTo(SIDECAR_INBOUND);
                                WorkloadSelector workloadSelector = new WorkloadSelector();
                                workloadSelector.setLabels(Collections.singletonMap("app", "huapp"));
                                rateLimiterConfigSpec.setWorkloadSelector(workloadSelector);
                            }))
                    .validateEnvoyFilter()
                    .validateConfigMap();
        }
    }

    @Test
    @Order(1)
    public void createRateLimiterWithOutWorkLoadSelector() {
        RateLimiter rateLimiter = preparedRateLimiter();
        RateLimiterConfig rateLimiterConfig = preparedRateLimiterConfig();
        rateLimiterConfig.setSpec(rateLimiterConfig.getSpec().setWorkloadSelector(null));
        K8sRequester requester = new K8sRequester(client, namespace);
        try (
                RateLimiterProcessor rateLimiterProcessor = new RateLimiterProcessor(requester);
                RateLimiterConfigProcessor rateLimiterConfigProcessor = new RateLimiterConfigProcessor(requester);
        ) {
            rateLimiterProcessor
                    .create(rateLimiter)
                    .validateRateLimiterDeployment()
                    .validateRedisDeployment()
                    .validateConfigMap()
                    .validateService()
                    .edit($rateLimiter -> $rateLimiter
                            .updateSpec(rateLimiterSpec -> rateLimiterSpec.setLogLevel("WARNING")))
                    .validateRateLimiterDeployment();

            rateLimiterConfigProcessor
                    .create(rateLimiterConfig)
                    .validateRatelimiterConfig()
                    .validateConfigMap()
                    .validateEnvoyFilter()
                    .edit(rlConfig -> rlConfig
                            .updateSpec(rateLimiterConfigSpec -> {
                                WorkloadSelector workloadSelector = new WorkloadSelector();
                                workloadSelector.setLabels(Collections.singletonMap("app", "huapp"));
                                rateLimiterConfigSpec.setWorkloadSelector(workloadSelector);
                            }))
                    .validateEnvoyFilter()
                    .validateConfigMap()
                    .edit(rlConfig -> rlConfig
                            .updateSpec(rateLimiterConfigSpec -> {
                                rateLimiterConfigSpec.setWorkloadSelector(null);
                            }))
                    .validateEnvoyFilter();
        }
    }

    @Test
    @SneakyThrows
    private RateLimiter preparedRateLimiter() {
        return new RateLimiter(client)
                .updateMetadata(objectMeta -> {
                    objectMeta.setName(rateLimiterName);
                    objectMeta.setNamespace(namespace);
                })
                .setSpec(new RateLimiter.RateLimiterSpec(8088, 1, "INFO"));
    }

    @SneakyThrows
    private RateLimiterConfig preparedRateLimiterConfig() {
        ObjectMeta objectMeta = new ObjectMeta();
        objectMeta.setName(rateLimiterName);
        objectMeta.setNamespace(namespace);

        RateLimiterConfig.RateLimit rateLimit = new RateLimiterConfig.RateLimit()
                .setRequestsPerUnit(1)
                .setUnit("minute");

        RateLimiterConfig.RateLimiterConfigDescriptors rateLimiterConfigDescriptors =
                new RateLimiterConfig.RateLimiterConfigDescriptors()
                        .setKey("header-key").setValue("header-val")
                        .setRateLimit(rateLimit);

        RateLimiterConfig.RateLimitProperty rateLimitProperty = new RateLimiterConfig.RateLimitProperty()
                .setDescriptors(Collections.singletonList(rateLimiterConfigDescriptors))
                .setDomain("host-info");

        RateLimiterConfig.RateLimiterConfigSpec rateLimiterConfigSpec =
                new RateLimiterConfig.RateLimiterConfigSpec()
                        .setApplyTo(GATEWAY)
                        .setHost("host-info-srv.org")
                        .setPort(80)
                        .setRateLimiter("rate-limiter-test")
                        .setRateLimitProperty(rateLimitProperty)
                        .setWorkloadSelector(new WorkloadSelector(Collections.singletonMap("app", "application-app")));

        return RateLimiterConfig.builder()
                .metadata(objectMeta)
                .spec(rateLimiterConfigSpec)
                .build();

    }

}
