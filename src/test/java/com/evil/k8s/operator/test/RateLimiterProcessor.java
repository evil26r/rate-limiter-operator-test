package com.evil.k8s.operator.test;

import com.evil.k8s.operator.test.models.RateLimiter;
import com.evil.k8s.operator.test.models.RateLimiterConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import lombok.RequiredArgsConstructor;
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

    private List<RateLimiter> rateLimiters = new LinkedList<>();

    private final K8sRequester requester;

    private RateLimiter currentRateLimiter;


    public RateLimiterProcessor create(RateLimiter rateLimiter) {
        currentRateLimiter = rateLimiter;
        rateLimiters.add(rateLimiter);
        requester.createRateLimiter(rateLimiter);
        return this;
    }

    public RateLimiterProcessor validateRateLimiter() {
        RateLimiter rateLimiter = requester.getRateLimiter(currentRateLimiter.getMetadata().getName());
        assertEquals(currentRateLimiter, rateLimiter);
        return this;
    }

    public RateLimiterProcessor validateRateLimiterDeployment() {
        return validateRateLimiterDeployment(currentRateLimiter);
    }

    public RateLimiterProcessor validateRateLimiterDeployment(RateLimiter rateLimiter) {
        String name = rateLimiter.getMetadata().getName();
        Deployment deployment = requester.getDeployment(name);
        assertEquals(currentRateLimiter.getSpec().getSize(), deployment.getSpec().getReplicas());

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
                .anyMatch(containerPort -> Objects.equals(rateLimiter.getSpec().getPort(), containerPort.getContainerPort())));
        return this;
    }

    public RateLimiterProcessor validateRedisDeployment() {
        return validateRedisDeployment(currentRateLimiter);
    }

    public RateLimiterProcessor validateRedisDeployment(RateLimiter rateLimiter) {
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
        return this;
    }

    public RateLimiterProcessor edit(Consumer<RateLimiter> function) {
        function.accept(currentRateLimiter);
        requester.editRateLimiter(currentRateLimiter);
        return this;
    }

    public RateLimiterProcessor validateConfigMap() {
        return validateConfigMap(currentRateLimiter);
    }

    public RateLimiterProcessor validateConfigMap(RateLimiter rateLimiter) {
        String name = rateLimiter.getMetadata().getName();
        Map<String, String> data = requester.getConfigMap(name).get().getData();
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
        List<Service> serviceList = requester.getServices().stream()
                .filter(service -> service.getMetadata().getName().equals(name)
                        || service.getMetadata().getName().equals(generateRedisName(name)))
                .collect(Collectors.toList());

        assertEquals(2, serviceList.size());

        Service rateLimiterService = serviceList.stream()
                .filter(service -> service.getMetadata().getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Not exist ratelimiter service"));

        ServiceSpec rateLimiterServiceSpec = rateLimiterService.getSpec();

        //Check port block
        rateLimiterServiceSpec.getPorts()
                .forEach(servicePort -> {
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
                .filter(service -> service.getMetadata().getName().equals(generateRedisName(name)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Not exist ratelimiter service"));

        ServiceSpec redisRateLimiterServiceSpec = redisRateLimiterService.getSpec();

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
        return this;
    }

    public void deleteAdjacentFiles(){
        requester.deleteConfigMap(currentRateLimiter.getMetadata().getName());
        requester.deleteRateLimiterDeployment(currentRateLimiter.getMetadata().getName());
        requester.deleteRedisDeployment(generateRedisName(currentRateLimiter.getMetadata().getName()));
        requester.deleteRateLimiterService(currentRateLimiter.getMetadata().getName());
        requester.deleteRedisService(generateRedisName(currentRateLimiter.getMetadata().getName()));
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
        return this;
    }
}
