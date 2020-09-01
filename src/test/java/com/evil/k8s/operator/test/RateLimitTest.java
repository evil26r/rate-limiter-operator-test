package com.evil.k8s.operator.test;

import com.evil.k8s.operator.test.models.EnvoyHttpFilterPatch;
import com.evil.k8s.operator.test.models.RateLimiter;
import com.evil.k8s.operator.test.models.RateLimiterConfig;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.snowdrop.istio.api.networking.v1alpha3.EnvoyConfigObjectPatch;
import me.snowdrop.istio.api.networking.v1alpha3.WorkloadSelector;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static com.evil.k8s.operator.test.models.RateLimiterConfig.Context.*;
import static com.evil.k8s.operator.test.utils.Utils.YAML_MAPPER;
import static com.evil.k8s.operator.test.utils.Utils.generateRedisName;


//@SpringBootTest
@Slf4j
class RateLimitTest extends K8sRateLimitAbstractTest {

    private final String namespace = "test-project";
    private final String rateLimiterName = "rate-limiter-test";
    private K8sRequester requester = new K8sRequester(client, namespace);

    /**
     * Тест проверяет работу оператора.
     * Создает 2 ресурса: RateLimiter и RateLimiterConfig, валидирует указанные значения и созданные оператором ресурсы
     */
    @Test
    public void createRateLimiter() {
        RateLimiter rateLimiter = preparedRateLimiter();
        RateLimiterConfig rateLimiterConfig = preparedRateLimiterConfig();
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
                    .validateServices()
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

    /**
     * Создание рейтлимитера без ворклоадер селектора, добавление и удаление селектора.
     */
    @Test
    public void createRateLimiterWithOutWorkLoadSelector() {
        RateLimiter rateLimiter = preparedRateLimiter();
        RateLimiterConfig rateLimiterConfig = preparedRateLimiterConfig();
        rateLimiterConfig.setSpec(rateLimiterConfig.getSpec().setWorkloadSelector(null));
        try (
                RateLimiterProcessor rateLimiterProcessor = new RateLimiterProcessor(requester);
                RateLimiterConfigProcessor rateLimiterConfigProcessor = new RateLimiterConfigProcessor(requester);
        ) {
            rateLimiterProcessor
                    .create(rateLimiter)
                    .validateRateLimiterDeployment()
                    .validateRedisDeployment()
                    .validateConfigMap()
                    .validateServices()
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

    /**
     * Создание рейтлимитер конфига без рейтлимитера.
     * Ожидаемое поведение: При создании рейтлимитера - создадутся необходимые для работы ресурсы.
     */
    @Test
    public void createRateLimiterConfigWithOutRateLimiter() {
        RateLimiterConfig rateLimiterConfig = preparedRateLimiterConfig();
        try (
                RateLimiterProcessor rateLimiterProcessor = new RateLimiterProcessor(requester);
                RateLimiterConfigProcessor rateLimiterConfigProcessor = new RateLimiterConfigProcessor(requester);
        ) {
            rateLimiterConfigProcessor
                    .create(rateLimiterConfig)
                    .validateRatelimiterConfig();
            try {
                rateLimiterConfigProcessor.validateEnvoyFilter();
                throw new RuntimeException("RateLimiter doesn't exists, but exist EnvoyFilter");
            } catch (KubernetesClientException ex) {
                if (ex.getStatus().getMessage().equals("envoyfilters.networking.istio.io \"" + rateLimiterConfig.getMetadata().getName() + "\" not found")) {
                    log.info("", ex);
                } else {
                    throw new RuntimeException(ex);
                }
            }
            rateLimiterProcessor
                    .create(preparedRateLimiter())
                    .validateRateLimiterDeployment()
                    .validateRedisDeployment()
                    .validateConfigMap()
                    .validateServices();

            rateLimiterConfigProcessor
                    .validateRatelimiterConfig()
                    .validateEnvoyFilter()
                    .validateConfigMap();
        }
    }

    /**
     * Пересоздание рейтлимитера и проверка, что конфиг мапа создалась заново.
     */
    @Test
    public void recreateRateLimiterAndCheckConfigMap() {
        RateLimiter rateLimiter = preparedRateLimiter();
        RateLimiterConfig rateLimiterConfig = preparedRateLimiterConfig();
        try (
                RateLimiterConfigProcessor rateLimiterConfigProcessor = new RateLimiterConfigProcessor(requester);
                RateLimiterProcessor rateLimiterProcessor = new RateLimiterProcessor(requester);
        ) {
            rateLimiterProcessor
                    .create(rateLimiter)
                    .validateRateLimiterDeployment()
                    .validateRedisDeployment()
                    .validateConfigMap()
                    .validateServices();

            rateLimiterConfigProcessor
                    .create(rateLimiterConfig)
                    .validateRatelimiterConfig()
                    .validateConfigMap()
                    .validateEnvoyFilter()
                    .validateEnvoyFilter()
                    .validateConfigMap()
                    .validateEnvoyFilter();

            rateLimiterProcessor.delete()
                    .create(rateLimiter)
                    .validateRateLimiterDeployment()
                    .validateRedisDeployment()
                    .validateConfigMap()
                    .validateServices();

            rateLimiterConfigProcessor
                    .validateConfigMap();
        }
    }


    /**
     * Тест проверяет, что домены в файлах конфиг мапы уникальны.
     */
    @Test
    @SneakyThrows
    public void sameDomainsRateLimiterConfigs() {
        RateLimiterConfig rateLimiterConfig1 = preparedRateLimiterConfig();

        RateLimiterConfig rateLimiterConfig2 = preparedRateLimiterConfig();
        rateLimiterConfig2.getMetadata().setName("new-rate-limiter-test");
        rateLimiterConfig2.getSpec().getRateLimitProperty().setDomain("another-domain");

        RateLimiterConfig rateLimiterConfig3 = preparedRateLimiterConfig();
        rateLimiterConfig3.getMetadata().setName("another-new-rate-limiter-test");
        rateLimiterConfig3.getSpec().getRateLimitProperty().setDomain("another-domain");
        try (
                RateLimiterProcessor rateLimiterProcessor = new RateLimiterProcessor(requester);
                RateLimiterConfigProcessor rateLimiterConfigProcessor = new RateLimiterConfigProcessor(requester);
        ) {
            rateLimiterProcessor.create(preparedRateLimiter());

            rateLimiterConfigProcessor
                    .create(rateLimiterConfig1)
                    .create(rateLimiterConfig2)
                    .validateConfigMap()
                    .create(rateLimiterConfig3)
                    .validateConfigMap();
        }
    }

    /**
     * Тест редактирует RateLimiter конфиг и проверяет, что изменения откатываются к необходимым.
     */
    @Test
    @SneakyThrows
    public void editAllFieldsRateLimiterConfig() {
        RateLimiterConfig rateLimiterConfig = preparedRateLimiterConfig();
        try (
                RateLimiterProcessor rateLimiterProcessor = new RateLimiterProcessor(requester);
                RateLimiterConfigProcessor rateLimiterConfigProcessor = new RateLimiterConfigProcessor(requester);
        ) {
            rateLimiterProcessor.create(preparedRateLimiter());

            rateLimiterConfigProcessor
                    .create(rateLimiterConfig)
                    .validateRatelimiterConfig()
                    .edit(rlConfig -> rlConfig
                            .updateSpec(rateLimiterConfigSpec -> {
                                rateLimiterConfigSpec.setApplyTo(SIDECAR_INBOUND);
                                rateLimiterConfigSpec.setHost("new-host.org");
                                rateLimiterConfigSpec.setPort(81);
                                WorkloadSelector workloadSelector = new WorkloadSelector();
                                workloadSelector.setLabels(Collections.singletonMap("app", "huapp"));
                                rateLimiterConfigSpec.setWorkloadSelector(workloadSelector);

                                rateLimiterConfigSpec.getRateLimitProperty().setDomain("another-domain");
                                rateLimiterConfigSpec.getRateLimitProperty().getDescriptors()
                                        .forEach(d -> d.setKey("new-header-key").setValue("new-header-val"));
                                rateLimiterConfigSpec.getRateLimitProperty().getDescriptors()
                                        .forEach(d -> d.getRateLimit().setRequestsPerUnit(5));
                            }))
                    .validateRatelimiterConfig()
                    .validateEnvoyFilter()
                    .validateConfigMap()
                    .edit(rlConfig -> rlConfig
                            .updateSpec(rateLimiterConfigSpec -> {
                                rateLimiterConfigSpec.setApplyTo(SIDECAR_OUTBOUND);
                                rateLimiterConfigSpec.setHost("another-host.org");
                                rateLimiterConfigSpec.setPort(81);
                                WorkloadSelector workloadSelector = new WorkloadSelector();
                                workloadSelector.setLabels(Collections.singletonMap("app", "huapp2"));
                                rateLimiterConfigSpec.setWorkloadSelector(workloadSelector);

                                rateLimiterConfigSpec.getRateLimitProperty().setDomain("different-domain");
                                rateLimiterConfigSpec.getRateLimitProperty().getDescriptors()
                                        .forEach(d -> d.setKey("another-header-key").setValue("another-header-val"));
                                rateLimiterConfigSpec.getRateLimitProperty().getDescriptors()
                                        .forEach(d -> d.getRateLimit().setRequestsPerUnit(10));
                            }))
                    .validateRatelimiterConfig()
                    .validateEnvoyFilter()
                    .validateConfigMap();
        }
    }

    /**
     * Удаление кайндов, которые создаются оператором и проверяем, что они верно пересоздались.
     */
    @Test
    @SneakyThrows
    public void deleteResources() {
        try (
                RateLimiterProcessor rateLimiterProcessor = new RateLimiterProcessor(requester);
                RateLimiterConfigProcessor rateLimiterConfigProcessor = new RateLimiterConfigProcessor(requester);
        ) {
            rateLimiterProcessor
                    .create(preparedRateLimiter())
                    .validateRateLimiter()
                    .deleteConfigMap()
                    .deleteRateLimitDeployment()
                    .deleteRedisDeployment()
                    .deleteRedisService()
                    .deleteRateLimitService()
                    .validateConfigMap()
                    .validateRateLimiterDeployment()
                    .validateRedisDeployment()
                    .validateServices();

            rateLimiterConfigProcessor
                    .create(preparedRateLimiterConfig())
                    .validateRatelimiterConfig()
                    .deleteEnvoyFilter()
                    .validateConfigMap()
                    .validateEnvoyFilter();

        }
    }


    /**
     * Редактирование EnvoyFilter и проверка, что изменения откатываются к необходимым.
     */
    @Test
    @SneakyThrows
    public void editEnvoyFilter() {
        try (
                RateLimiterProcessor rateLimiterProcessor = new RateLimiterProcessor(requester);
                RateLimiterConfigProcessor rateLimiterConfigProcessor = new RateLimiterConfigProcessor(requester);
        ) {
            rateLimiterProcessor.create(preparedRateLimiter());
            rateLimiterConfigProcessor
                    .create(preparedRateLimiterConfig())
                    .editEnvoyFilter(envoyFilter -> {
                        EnvoyConfigObjectPatch envoyFilterConfigPatchesHttpFilter = envoyFilter.getSpec().getConfigPatches().stream()
                                .filter(i -> i.getApplyTo().name().equals("HTTP_FILTER"))
                                .findFirst()
                                .orElseThrow(() -> new IllegalStateException("Dont find HTTP_FILTER block from envoy filter"));

                        EnvoyHttpFilterPatch envoyRateLimit = YAML_MAPPER.convertValue(envoyFilterConfigPatchesHttpFilter.getPatch().getValue(), EnvoyHttpFilterPatch.class);
                        envoyRateLimit.getConfig().setDomain("another domain");
                        envoyRateLimit.getConfig().setFailure_mode_deny(false);
                        envoyRateLimit.getConfig().getRateLimitService().getGrpcService().getEnvoyGrpc().setCluster_name("edited cluster name");
                        envoyRateLimit.getConfig().getRateLimitService().getGrpcService().setTimeout("5s");
                        envoyRateLimit.setName("new name");
                        envoyFilterConfigPatchesHttpFilter.getPatch().setValue(YAML_MAPPER.convertValue(envoyRateLimit, Map.class));
                        envoyFilter.getSpec().setConfigPatches(Collections.singletonList(envoyFilterConfigPatchesHttpFilter));
                    })
                    .validateEnvoyFilter();
        }
    }

    /**
     * Тест редактирует RateLimiter Service и проверяет, что
     * изменения откатываются к необходимым.
     */
    @Test
    @SneakyThrows
    public void editService() {
        RateLimiter rateLimiter = preparedRateLimiter();
        try (
                RateLimiterProcessor rateLimiterProcessor = new RateLimiterProcessor(requester);
        ) {
            rateLimiterProcessor
                    .create(rateLimiter)
                    .editRateLimiterService(service -> {
                        ServiceSpec spec = service.getSpec();
                        ServicePort servicePort = new ServicePort();
                        servicePort.setName("http");
                        servicePort.setPort(30000);
                        servicePort.setProtocol("UDP");
                        servicePort.setTargetPort(new IntOrString(50000));
                        spec.setPorts(Collections.singletonList(servicePort));
                        return service;
                    })
                    .validateServices()
                    .delete()
                    .create(rateLimiter)
                    .editRedisService(service -> {
                        ServiceSpec spec = service.getSpec();
                        spec.setSelector(null);
                        return service;
                    })
                    .editRateLimiterService(service -> {
                        ServiceSpec spec = service.getSpec();
                        spec.setSelector(null);
                        return service;
                    })
                    .validateServices();
        }
    }


    /**
     * Тест редактирует Deployment'ы и проверяет, что
     * происходит откат изменений.
     */
    @Test
    public void editDeployment() {
        RateLimiter rateLimiter = preparedRateLimiter();
        try (
                RateLimiterProcessor rateLimiterProcessor = new RateLimiterProcessor(requester);
        ) {
            rateLimiterProcessor
                    .create(rateLimiter)
                    .validateRateLimiterDeployment()
                    .validateRedisDeployment()
                    .editRedisDeployment(deployment -> {
                        deployment.getSpec().setReplicas(5);
                    })
                    .validateRateLimiterDeployment()
                    .editRateLimiterDeployment(deployment -> {
                        deployment.getSpec().setReplicas(4);
                    })
                    .validateRedisDeployment();
        }
    }

    /**
     * Тест редактирует ConfigMap и проверяет, что
     * происходит откат изменений.
     */
    @Test
    @SneakyThrows
    public void editConfigMap() {
        RateLimiter rateLimiter = preparedRateLimiter();
        RateLimiterConfig rateLimiterConfig = preparedRateLimiterConfig();
        try (
                RateLimiterProcessor rateLimiterProcessor = new RateLimiterProcessor(requester);
                RateLimiterConfigProcessor rateLimiterConfigProcessor = new RateLimiterConfigProcessor(requester);
        ) {
            rateLimiterProcessor.create(rateLimiter);
            rateLimiterConfigProcessor
                    .create(rateLimiterConfig)
                    .editConfigMap(rateLimitProperty -> {
                        rateLimitProperty.setDomain("new_Domain");
                        rateLimitProperty.getDescriptors().get(0).setValue("new_value2");
                        rateLimitProperty.getDescriptors().get(0).setKey("new_Key456");
                        rateLimitProperty.getDescriptors().get(0).getRateLimit().setRequestsPerUnit(10);
                    })
                    .validateConfigMap();
        }
    }

    private RateLimiter preparedRateLimiter() {
        return new RateLimiter(client)
                .updateMetadata(objectMeta -> {
                    objectMeta.setName(rateLimiterName);
                    objectMeta.setNamespace(namespace);
                })
                .setSpec(new RateLimiter.RateLimiterSpec(8088, 1, "INFO"));
    }

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
