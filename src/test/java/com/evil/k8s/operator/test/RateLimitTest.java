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
        try (
                RateLimiterProcessor rateLimiterProcessor = new RateLimiterProcessor(client, namespace);
                RateLimiterConfigProcessor rateLimiterConfigProcessor = new RateLimiterConfigProcessor(client, namespace);
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
                    .delay(1_000)
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
    @SneakyThrows
    private RateLimiter preparedRateLimiter() {
        return new RateLimiter(client)
                .updateMetadata(objectMeta -> {
                    objectMeta.setName(rateLimiterName);
                    objectMeta.setNamespace(namespace);
                })
                .setSpec(new RateLimiter.RateLimiterSpec(8088, "INFO"));
    }

    @SneakyThrows
    private RateLimiterConfig preparedRateLimiterConfig() {
        ObjectMeta objectMeta = new ObjectMeta();
        objectMeta.setName(rateLimiterName);
        objectMeta.setNamespace(namespace);

        WorkloadSelector workloadSelector = new WorkloadSelector(Collections.singletonMap("app", "application-app"));

        RateLimiterConfig.RateLimit rateLimit = new RateLimiterConfig.RateLimit();
        rateLimit.setRequestsPerUnit(1);
        rateLimit.setUnit("minute");

        RateLimiterConfig.RateLimiterConfigDescriptors rateLimiterConfigDescriptors =
                new RateLimiterConfig.RateLimiterConfigDescriptors();
        rateLimiterConfigDescriptors.setKey("header-key");
        rateLimiterConfigDescriptors.setValue("header-val");
        rateLimiterConfigDescriptors.setRateLimit(rateLimit);

        RateLimiterConfig.RateLimitProperty rateLimitProperty = new RateLimiterConfig.RateLimitProperty();
        rateLimitProperty.setDescriptors(Collections.singletonList(rateLimiterConfigDescriptors));
        rateLimitProperty.setDomain("host-info");

        RateLimiterConfig.RateLimiterConfigSpec rateLimiterConfigSpec = new RateLimiterConfig.RateLimiterConfigSpec();
        rateLimiterConfigSpec.setApplyTo(GATEWAY);
        rateLimiterConfigSpec.setHost("host-info-srv.org");
        rateLimiterConfigSpec.setPort(80);
        rateLimiterConfigSpec.setRateLimiter("rate-limiter-test");
        rateLimiterConfigSpec.setRateLimitProperty(rateLimitProperty);
        rateLimiterConfigSpec.setWorkloadSelector(workloadSelector);

        return RateLimiterConfig.builder()
                .metadata(objectMeta)
                .spec(rateLimiterConfigSpec)
                .build();

    }

}
