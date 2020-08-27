package com.evil.k8s.operator.test;

import com.evil.k8s.operator.test.models.RateLimiter;
import com.evil.k8s.operator.test.models.RateLimiterConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.IllegalInstantException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.evil.k8s.operator.test.CustomResourcesConstants.rateLimitCrdContext;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class RateLimiterProcessor implements AutoCloseable {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private final KubernetesClient client;
    private final String namespace;
    private Map<String, Semaphore> locks = new HashMap();
    private List<RateLimiter> rateLimiters = new LinkedList<>();

    private RateLimiter currentRateLimiter;

    @SneakyThrows
    public RateLimiterProcessor(KubernetesClient client, String namespace) {
        this.client = client;
        this.namespace = namespace;

        client.customResource(rateLimitCrdContext)
                .watch(namespace, new Watcher<>() {
                    @SneakyThrows
                    @Override
                    public void eventReceived(Action action, String resource) {
                        log.info("Action: [{}]; Resource: [{}]", action, resource);
                        RateLimiter rateLimiters = YAML_MAPPER.readValue(resource, RateLimiter.class);
                        locks.computeIfPresent(generateLockKey(rateLimiters), (s, semaphore) -> {
                            log.info("Unlock: [{}]", s);
                            semaphore.release();
                            return semaphore;
                        });
                    }

                    @Override
                    public void onClose(KubernetesClientException cause) {
                        log.info("Watcher Exception", cause);
                    }
                });

        client.apps().deployments()
                .watch(new Watcher<>() {
                    @Override
                    public void eventReceived(Action action, Deployment deployment) {
                        log.info("Action: [{}]; Resource: [{}]", action, deployment);
                        locks.computeIfPresent(generateLockKey(Deployment.class, deployment.getMetadata().getName()), (s, semaphore) -> {
                            try {
                                TimeUnit.SECONDS.sleep(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            log.info("Unlock: [{}]", s);
                            semaphore.release();
                            return semaphore;
                        });
                    }

                    @Override
                    public void onClose(KubernetesClientException cause) {
                        log.info("Watcher Exception", cause);
                    }
                });
    }

    @SneakyThrows
    public RateLimiterProcessor create(RateLimiter rateLimiter) {
        currentRateLimiter = rateLimiter;
        rateLimiters.add(rateLimiter);
        locks.put(generateLockKey(rateLimiter), new Semaphore(0));
        locks.put(generateLockKey(Deployment.class, rateLimiter.getMetadata().getName()), new Semaphore(0));
        locks.put(generateLockKey(Deployment.class, rateLimiter.getMetadata().getName() + "-redis"), new Semaphore(0));
        client.customResource(rateLimitCrdContext)
                .create(rateLimiter.getMetadata().getNamespace(), YAML_MAPPER.writeValueAsString(rateLimiter));
        return this;
    }

    public RateLimiterProcessor validateRateLimiterDeployment() {
        return validateRateLimiterDeployment(currentRateLimiter);
    }

    @SneakyThrows
    public RateLimiterProcessor validateRateLimiterDeployment(RateLimiter rateLimiter) {
        locks.get(generateLockKey(Deployment.class, rateLimiter.getMetadata().getName())).acquire(1);
        String name = rateLimiter.getMetadata().getName();
        Deployment deployment = getDeployment(rateLimiter.getMetadata().getName());
        assertEquals(1, deployment.getSpec().getReplicas());

        // Check environment
        List<Container> containers = deployment.getSpec().getTemplate().getSpec().getContainers();
        Map<String, String> collect = containers.stream()
                .map(Container::getEnv)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(EnvVar::getName, EnvVar::getValue));

        log.info("Check log level");
        assertEquals(rateLimiter.getSpec().getLogLevel(), collect.get("LOG_LEVEL"));
        assertEquals("TCP", collect.get("REDIS_SOCKET_TYPE"));
        assertEquals(name + "-redis" + ":6379", collect.get("REDIS_URL"));
        assertEquals("true", collect.get("RUNTIME_IGNOREDOTFILES"));
        assertEquals("false", collect.get("RUNTIME_WATCH_ROOT"));

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

        assertEquals(runtimeRoot + "/" + runtimeSubdirectory + "/config", mountPath);

        //Check port
        assertTrue(containers.stream()
                .map(Container::getPorts)
                .flatMap(Collection::stream)
                .anyMatch(containerPort -> rateLimiter.getSpec().getPort() == containerPort.getContainerPort()));
        return this;
    }

    public RateLimiterProcessor validateRedisDeployment() {
        return validateRedisDeployment(currentRateLimiter);
    }

    public RateLimiterProcessor validateRedisDeployment(RateLimiter rateLimiter) {
        String name = rateLimiter.getMetadata().getName();
        Deployment redisDeployment = client.apps().deployments().list()
                .getItems().stream()
                .filter(deployment -> deployment.getMetadata().getName().equals(name + "-redis"))
                .findFirst().orElseThrow(() -> new IllegalInstantException("Not exist redis deployment"));

        assertEquals(1, redisDeployment.getSpec().getReplicas());

        PodTemplateSpec template = redisDeployment.getSpec().getTemplate();
        template.getMetadata().getLabels().entrySet()
                .stream()
                .filter(stringStringEntry -> stringStringEntry.getKey().equals("app") && stringStringEntry.getValue().equals(name + "-redis"))
                .findAny().orElseThrow(() -> new IllegalStateException("Not exist label selector"));

        template.getSpec().getContainers()
                .stream()
                .map(Container::getPorts)
                .flatMap(Collection::stream)
                .filter(containerPort -> containerPort.getContainerPort().equals(6379))
                .findAny().orElseThrow(() -> new IllegalStateException("Illegal port from redis deployment"));
        return this;
    }

    @SneakyThrows
    public RateLimiterProcessor edit(Consumer<RateLimiter> function) {
        function.accept(currentRateLimiter);
        client.customResource(rateLimitCrdContext)
                .edit(currentRateLimiter.getMetadata().getNamespace(), currentRateLimiter.getMetadata().getName(),
                        YAML_MAPPER.writeValueAsString(currentRateLimiter));
        locks.computeIfPresent(generateLockKey(currentRateLimiter), (s, semaphore) -> {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return semaphore;
        });
        return this;
    }

    public RateLimiterProcessor validateConfigMap() {
        return validateConfigMap(currentRateLimiter);
    }

    public RateLimiterProcessor validateConfigMap(RateLimiter rateLimiter) {
        String name = rateLimiter.getMetadata().getName();
        Map<String, String> data = client.configMaps().list()
                .getItems()
                .stream()
                .filter($configMap -> $configMap.getMetadata().getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Non config map!"))
                .getData();
        if (data != null) {
            List<RateLimiterConfig.RateLimitProperty> rateLimitersConfig = data.values()
                    .stream()
                    .map(s -> {
                        try {
                            return YAML_MAPPER.readValue(s, RateLimiterConfig.RateLimitProperty.class);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());

            Collection<String> allDomains = rateLimitersConfig.stream()
                    .map(RateLimiterConfig.RateLimitProperty::getDomain)
                    .collect(Collectors.toList());

            Collection<String> distinctDomain = rateLimitersConfig.stream()
                    .map(RateLimiterConfig.RateLimitProperty::getDomain)
                    .collect(Collectors.toSet());

            assertEquals(distinctDomain.size(), allDomains.size(), "Exist not unique domains");
        }

        return this;
    }

    public RateLimiterProcessor validateService() {
        return validateService(currentRateLimiter);
    }

    public RateLimiterProcessor validateService(RateLimiter rateLimiter) {
        String name = rateLimiter.getMetadata().getName();
        int port = rateLimiter.getSpec().getPort();
        List<Service> serviceList = client.services().list().getItems().stream()
                .filter(service -> service.getMetadata().getName().equals(name)
                        || service.getMetadata().getName().equals(name + "-redis"))
                .collect(Collectors.toList());
        assertEquals(2, serviceList.size());

        Service rateLimiterService = serviceList.stream()
                .filter(service -> service.getMetadata().getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Not exist ratelimiter service"));


        ServiceSpec rateLimiterServiceSpec = rateLimiterService.getSpec();

        //Check port block
        rateLimiterServiceSpec.getPorts().forEach(servicePort -> {
            assertEquals("grpc-" + name, servicePort.getName());
            assertEquals("TCP", servicePort.getProtocol());
            assertEquals(port, servicePort.getPort());
            assertEquals(port, servicePort.getTargetPort().getIntVal());
        });
        //Check selector block
        rateLimiterService.getSpec().getSelector().entrySet().stream()
                .filter(stringStringEntry -> stringStringEntry.getKey().equals("app") && stringStringEntry.getValue().equals(name))
                .findAny().orElseThrow(() -> new IllegalStateException("Not exist lable from ratelimiter service selector"));

        Service redisRateLimiterService = serviceList.stream()
                .filter(service -> service.getMetadata().getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Not exist ratelimiter service"));

        ServiceSpec redisRateLimiterServiceSpec = redisRateLimiterService.getSpec();

        //Check port block
        redisRateLimiterServiceSpec.getPorts().forEach(servicePort -> {
            assertEquals("grpc-" + name, servicePort.getName());
            assertEquals("TCP", servicePort.getProtocol());
            assertEquals(port, servicePort.getPort());
            assertEquals(port, servicePort.getTargetPort().getIntVal());
        });
        //Check selector block
        redisRateLimiterServiceSpec.getSelector().entrySet().stream()
                .filter(stringStringEntry -> stringStringEntry.getKey().equals("app") && stringStringEntry.getValue().equals(name))
                .findAny().orElseThrow(() -> new IllegalStateException("Not exist lable from ratelimiter service selector"));
        return this;
    }


    private Deployment getDeployment(String name) {
        return client.apps().deployments().list().getItems()
                .stream()
                .filter(deployment -> deployment.getMetadata().getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Non deployment: " + name));
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
        try {
            client.customResource(rateLimitCrdContext)
                    .delete(namespace, name);
            log.warn("Rate limiter: [{}] deleted", name);
        } catch (IOException e) {
            log.warn("Rate limiter: [{}] hasn't been deleted", name);
        }
        return this;
    }

    private static String generateLockKey(RateLimiter rateLimiters) {
        return generateLockKey(rateLimiters.getClass(), rateLimiters.getMetadata().getName());
    }

    private static String generateLockKey(Class clazz, String name) {
        return clazz + ":" + name;
    }
}