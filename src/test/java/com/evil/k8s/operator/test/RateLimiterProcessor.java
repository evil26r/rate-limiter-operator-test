package com.evil.k8s.operator.test;

import com.evil.k8s.operator.test.models.ConfigMapRateLimitProperty;
import com.evil.k8s.operator.test.models.RateLimiter;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.evil.k8s.operator.test.utils.Utils.*;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@RequiredArgsConstructor
public class RateLimiterProcessor implements AutoCloseable {

    private final List<RateLimiter> rateLimiters = new LinkedList<>();
    private final K8sRequester requester;

    private RateLimiter currentRateLimiter;

    private Status currentStatus = Status.UNDEFINED;

    public RateLimiterProcessor create(RateLimiter rateLimiter) {
        currentRateLimiter = rateLimiter;
        rateLimiters.add(rateLimiter);
        requester.createRateLimiter(rateLimiter);
        currentStatus = Status.CREATED;
        return this;
    }

    public RateLimiterProcessor validateRateLimiter() {
        currentStatus.getInstance().validateRateLimiter(currentRateLimiter, requester);
        return this;
    }

    public RateLimiterProcessor validateRateLimiterDeployment() {
        currentStatus.getInstance().validateRateLimiterDeployment(currentRateLimiter, requester);
        return this;
    }

    public RateLimiterProcessor editRateLimiterDeployment(Consumer<Deployment> deploymentConsumer) {
        Deployment deployment = requester.getDeployment(currentRateLimiter.getMetadata().getName());
        deploymentConsumer.accept(deployment);
        requester.editDeployment(deployment);
        return this;
    }

    public RateLimiterProcessor validateRedisDeployment() {
        currentStatus.getInstance().validateRedisDeployment(currentRateLimiter, requester);
        return this;
    }

    public RateLimiterProcessor deleteRedisDeployment() {
        String name = currentRateLimiter.getMetadata().getName();
        requester.deleteDeployment(generateRedisName(name));
        return this;
    }

    public RateLimiterProcessor editRedisDeployment(Consumer<Deployment> deploymentConsumer) {
        Deployment deployment = requester.getDeployment(generateRedisName(currentRateLimiter.getMetadata().getName()));
        deploymentConsumer.accept(deployment);
        requester.editDeployment(deployment);
        return this;
    }

    public RateLimiterProcessor edit(Consumer<RateLimiter> function) {
        function.accept(currentRateLimiter);
        requester.editRateLimiter(currentRateLimiter);
        return this;
    }

    public RateLimiterProcessor validateConfigMap() {
        currentStatus.getInstance().validateConfigMap(currentRateLimiter, requester);
        return this;
    }

    public RateLimiterProcessor validateServices() {
        currentStatus.getInstance().validateServices(currentRateLimiter, requester);
        return this;
    }

    public RateLimiterProcessor deleteConfigMap() {
        String name = currentRateLimiter.getMetadata().getName();
        requester.deleteConfigMap(name);
        return this;
    }


    public RateLimiterProcessor deleteRateLimitDeployment() {
        String name = currentRateLimiter.getMetadata().getName();
        requester.deleteDeployment(name);
        return this;
    }

    public RateLimiterProcessor deleteRateLimitService() {
        String name = currentRateLimiter.getMetadata().getName();
        requester.deleteService(name);
        return this;
    }

    public RateLimiterProcessor editRateLimiterService(Consumer<Service> сonsumer) {
        editService(сonsumer, currentRateLimiter.getMetadata().getName());
        return this;
    }

    @SneakyThrows
    public RateLimiterProcessor deleteRedisService() {
        String name = currentRateLimiter.getMetadata().getName();
        requester.deleteService(generateRedisName(name));
        return this;
    }

    public RateLimiterProcessor editRedisService(Consumer<Service> сonsumer) {
        editService(сonsumer, generateRedisName(currentRateLimiter.getMetadata().getName()));
        return this;
    }

    private RateLimiterProcessor editService(Consumer<Service> сonsumer, String name) {
        Service service = requester.getServiceByName(name);
        сonsumer.accept(service);
        requester.editService(service);
        return this;
    }

    @Override
    public void close() {
        rateLimiters.forEach(this::delete);
        rateLimiters.clear();
    }

    public RateLimiterProcessor delete() {
        rateLimiters.remove(currentRateLimiter);
        return delete(currentRateLimiter);
    }

    public RateLimiterProcessor delete(RateLimiter rateLimit) {
        return delete(rateLimit.getMetadata().getName());
    }

    private RateLimiterProcessor delete(String name) {
        requester.deleteRateLimiter(name);
        currentStatus = Status.REMOVED;
        return this;
    }

    private enum Status {
        UNDEFINED,
        CREATED,
        REMOVED;

        private static final RateLimiterValidator created = new RateLimiterCreatedValidator();
        private static final RateLimiterValidator removed = new RateLimiterRemovedvalidator();
        private static final RateLimiterValidator undefined = new RateLimiterValidator() {
            @Override
            public void validateRateLimiter(RateLimiter rateLimiter, K8sRequester requester) {
                throw new UnsupportedOperationException("Illegal operation with status UNDEFINED");
            }

            @Override
            public void validateRateLimiterDeployment(RateLimiter rateLimiter, K8sRequester requester) {
                throw new UnsupportedOperationException("Illegal operation with status UNDEFINED");
            }

            @Override
            public void validateRedisDeployment(RateLimiter rateLimiter, K8sRequester requester) {
                throw new UnsupportedOperationException("Illegal operation with status UNDEFINED");
            }

            @Override
            public void validateConfigMap(RateLimiter rateLimiter, K8sRequester requester) {
                throw new UnsupportedOperationException("Illegal operation with status UNDEFINED");
            }

            @Override
            public void validateServices(RateLimiter rateLimiter, K8sRequester requester) {
                throw new UnsupportedOperationException("Illegal operation with status UNDEFINED");
            }
        };

        RateLimiterValidator getInstance() {
            switch (this) {
                case CREATED:
                    return created;
                case REMOVED:
                    return removed;
                case UNDEFINED:
                default:
                    return undefined;
            }
        }
    }

    interface RateLimiterValidator {
        void validateRateLimiter(RateLimiter rateLimiter, K8sRequester requester);

        void validateRateLimiterDeployment(RateLimiter rateLimiter, K8sRequester requester);

        void validateRedisDeployment(RateLimiter rateLimiter, K8sRequester requester);

        void validateConfigMap(RateLimiter rateLimiter, K8sRequester requester);

        void validateServices(RateLimiter rateLimiter, K8sRequester requester);
    }

    public static class RateLimiterCreatedValidator implements RateLimiterValidator {

        @Override
        public void validateRateLimiter(RateLimiter rateLimiter, K8sRequester requester) {
            RateLimiter rateLimiterFromK8s = requester.getRateLimiter(rateLimiter.getMetadata().getName());
            assertEquals(rateLimiter, rateLimiterFromK8s);
        }

        @Override
        public void validateRateLimiterDeployment(RateLimiter rateLimiter, K8sRequester requester) {
            String name = rateLimiter.getMetadata().getName();
            Deployment deployment = requester.getDeployment(name);
            assertEquals(rateLimiter.getSpec().getSize(), deployment.getSpec().getReplicas());

            // Check environment
            List<Container> containers = deployment.getSpec().getTemplate().getSpec().getContainers();
            Map<String, String> collect = containers.stream()
                    .map(Container::getEnv)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toMap(EnvVar::getName, EnvVar::getValue));

            log.info("Check RateLimiter deployment environment level");
            assertEquals(rateLimiter.getSpec().getLogLevel(), collect.get("LOG_LEVEL"));
            assertEquals(TCP, collect.get("REDIS_SOCKET_TYPE"));
            assertEquals(generateRedisName(name) + ":6379", collect.get("REDIS_URL"));
            assertEquals(TRUE.toString(), collect.get("RUNTIME_IGNOREDOTFILES"));
            assertEquals(FALSE.toString(), collect.get("RUNTIME_WATCH_ROOT"));

            String runtimeRoot = collect.get("RUNTIME_ROOT");
            String runtimeSubdirectory = collect.get("RUNTIME_SUBDIRECTORY");
            String mountPath = containers.stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("Non config map"))
                    .getVolumeMounts()
                    .stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("Non config map"))
                    .getMountPath();
            assertNotNull(runtimeRoot);
            assertNotNull(runtimeSubdirectory);

            assertEquals(generateMountPath(runtimeRoot, runtimeSubdirectory), mountPath);

            //Check port
            assertTrue(containers.stream()
                    .map(Container::getPorts)
                    .flatMap(Collection::stream)
                    .anyMatch(containerPort -> Objects.equals(8081, containerPort.getContainerPort())));
        }

        @Override
        public void validateRedisDeployment(RateLimiter rateLimiter, K8sRequester requester) {
            String redisName = generateRedisName(rateLimiter.getMetadata().getName());
            Deployment redisDeployment = requester.getDeployment(redisName);

            assertEquals(1, redisDeployment.getSpec().getReplicas());

            PodTemplateSpec template = redisDeployment.getSpec().getTemplate();

            template.getMetadata().getLabels().entrySet()
                    .stream()
                    .filter(stringStringEntry -> stringStringEntry.getKey().equals("app") && stringStringEntry.getValue().equals(redisName))
                    .findAny()
                    .orElseThrow(() -> new IllegalStateException("Not exist label selector"));

            template.getSpec()
                    .getContainers()
                    .stream()
                    .map(Container::getPorts)
                    .flatMap(Collection::stream)
                    .filter(containerPort -> containerPort.getContainerPort().equals(6379))
                    .findAny()
                    .orElseThrow(() -> new IllegalStateException("Illegal port from redis deployment"));
        }

        @Override
        public void validateConfigMap(RateLimiter rateLimiter, K8sRequester requester) {
            String name = rateLimiter.getMetadata().getName();
            Map<String, String> data = requester.getConfigMap(name).get().getData();
            if (data != null) {
                List<ConfigMapRateLimitProperty> rateLimitersConfig = data.values()
                        .stream()
                        .map(s -> {
                            try {
                                return YAML_MAPPER.readValue(s, ConfigMapRateLimitProperty.class);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .collect(Collectors.toList());

                Collection<String> allDomains = rateLimitersConfig.stream()
                        .map(ConfigMapRateLimitProperty::getDomain)
                        .collect(Collectors.toList());

                Collection<String> distinctDomain = rateLimitersConfig.stream()
                        .map(ConfigMapRateLimitProperty::getDomain)
                        .collect(Collectors.toSet());

                assertEquals(distinctDomain.size(), allDomains.size(), "Exist not unique domains");
            }
        }

        @Override
        public void validateServices(RateLimiter rateLimiter, K8sRequester requester) {
            String name = rateLimiter.getMetadata().getName();

            Service service = requester.getServiceByName(name);
            ServiceSpec rateLimiterServiceSpec = service.getSpec();

            //Check port block
            rateLimiterServiceSpec.getPorts()
                    .forEach(servicePort -> {
                        assertEquals("grpc-" + name, servicePort.getName());
                        assertEquals("TCP", servicePort.getProtocol());
                        assertEquals(8081, servicePort.getPort());
                        assertEquals(8081, servicePort.getTargetPort().getIntVal());
                    });
            //Check selector block
            rateLimiterServiceSpec.getSelector().entrySet().stream()
                    .filter(stringStringEntry -> stringStringEntry.getKey().equals("app") && stringStringEntry.getValue().equals(name))
                    .findAny().orElseThrow(() -> new IllegalStateException("Not exist lable from ratelimiter service selector"));

            Service redisService = requester.getServiceByName(generateRedisName(name));
            ServiceSpec redisRateLimiterServiceSpec = redisService.getSpec();

            //Check port block
            redisRateLimiterServiceSpec.getPorts().forEach(servicePort -> {
                assertEquals(generateRedisName(name), servicePort.getName());
                assertEquals(TCP, servicePort.getProtocol());
                assertEquals(redisPort, servicePort.getPort());
                assertEquals(redisPort, servicePort.getTargetPort().getIntVal());
            });
            //Check selector block
            redisRateLimiterServiceSpec.getSelector().entrySet().stream()
                    .filter(stringStringEntry -> stringStringEntry.getKey().equals("app") && stringStringEntry.getValue().equals(generateRedisName(name)))
                    .findAny()
                    .orElseThrow(() -> new IllegalStateException("Not exist lable from ratelimiter service selector"));
        }
    }

    public static class RateLimiterRemovedvalidator implements RateLimiterValidator {

        @Override
        public void validateRateLimiter(RateLimiter rateLimiter, K8sRequester requester) {
            try {
                requester.getRateLimiter(rateLimiter.getMetadata().getName());
                throw new IllegalStateException("Ratelimiter already exist");
            } catch (KubernetesClientException ex) {
                log.info("Ratelimiter not exist.");
            }
        }

        @Override
        public void validateRateLimiterDeployment(RateLimiter rateLimiter, K8sRequester requester) {
            try {
                requester.getDeployment(rateLimiter.getMetadata().getName());
                throw new IllegalStateException("Ratelimiter Deployment already exist");
            } catch (IllegalStateException ex) {
                log.info("Ratelimiter Deployment not exist.");
            }
        }

        @Override
        public void validateRedisDeployment(RateLimiter rateLimiter, K8sRequester requester) {
            try {
                requester.getDeployment(generateRedisName(rateLimiter.getMetadata().getName()));
                throw new IllegalStateException("Ratelimiter Redis Deployment already exist");
            } catch (IllegalStateException ex) {
                log.info("Ratelimiter Redis Deployment not exist.");
            }
        }

        @Override
        public void validateConfigMap(RateLimiter rateLimiter, K8sRequester requester) {
            if (requester.getConfigMap(rateLimiter.getMetadata().getName()).get() == null) {
                log.info("Ratelimiter configMap not exist.");
            } else {
                throw new IllegalStateException("Ratelimiter configMap already exist");
            }
        }

        @Override
        public void validateServices(RateLimiter rateLimiter, K8sRequester requester) {
            try {
                requester.getServiceByName(rateLimiter.getMetadata().getName());
                throw new IllegalStateException("Ratelimiter already exist");
            } catch (IllegalStateException ex) {
                log.info("Ratelimiter service not exist.");
            }
            try {
                requester.getServiceByName(generateRedisName(rateLimiter.getMetadata().getName()));
                throw new IllegalStateException("Ratelimiter already exist");
            } catch (IllegalStateException ex) {
                log.info("RatelimiterRedis not exist.");
            }
        }
    }
}
