package com.evil.k8s.operator.test;

import com.evil.k8s.operator.test.models.RateLimiter;
import com.evil.k8s.operator.test.models.RateLimiterConfig;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.snowdrop.istio.api.networking.v1alpha3.WorkloadSelector;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.evil.k8s.operator.test.models.RateLimiterConfig.Context.*;


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
                log.info("", ex);
            }
            rateLimiterProcessor
                    .create(preparedRateLimiter())
                    .validateRateLimiterDeployment()
                    .validateRedisDeployment()
                    .validateConfigMap()
                    .validateService();

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
                    .validateService();

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
                    .validateService();

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
     * Тест редактирует RateLimiter конфиг и проверяет,
     * что изменения откатываются к необходимым.
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
     * Удаление файлов, которые создаются автоматически,
     * и последующая проверка на то, что они верно пересоздались.
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
                    .deleteControlledResources();

            rateLimiterConfigProcessor
                    .create(preparedRateLimiterConfig())
                    .validateRatelimiterConfig()
                    .deleteEnvoyFilter();

            rateLimiterConfigProcessor
                    .validateConfigMap()
                    .validateEnvoyFilter();

            rateLimiterProcessor
                    .validateConfigMap()
                    .validateRateLimiterDeployment()
                    .validateRedisDeployment()
                    .validateService();
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
                    .editEnvoyFilter()
                    .validateEnvoyFilter();

        }
    }

    /**
     * Тест редактирует RateLimiter Service и проверяет, что
     * изменения откатываются к необходимым.
     */
    @Test
    @SneakyThrows
    public void editRateLimiterService(){
        RateLimiter rateLimiter = preparedRateLimiter();
        try (
                RateLimiterProcessor rateLimiterProcessor = new RateLimiterProcessor(requester);
        ) {
            rateLimiterProcessor
                    .create(rateLimiter);

            rateLimiterProcessor.editRateLimiterService().validateService();
        }
    }

    /**
     * Тест редактирует Redis Service и проверяет, что
     * изменения откатываются к необходимым.
     */
    @Test
    @SneakyThrows
    public void editRedisService(){
        RateLimiter rateLimiter = preparedRateLimiter();
        try (
                RateLimiterProcessor rateLimiterProcessor = new RateLimiterProcessor(requester);
        ) {
            rateLimiterProcessor
                    .create(rateLimiter);

            rateLimiterProcessor.editRedisService().validateService();
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
