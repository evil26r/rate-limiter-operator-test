package com.evil.k8s.operator.test;

import com.evil.k8s.operator.test.models.RateLimiter;
import com.evil.k8s.operator.test.models.RateLimiterConfig;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.snowdrop.istio.api.networking.v1alpha3.EnvoyFilter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.evil.k8s.operator.test.CustomResourcesConstants.*;
import static com.evil.k8s.operator.test.utils.Utils.YAML_MAPPER;

@Slf4j
@RequiredArgsConstructor
public class K8sRequester {

    private final KubernetesClient client;
    private final String namespace;

    @SneakyThrows
    public RateLimiter getRateLimiter(String name) {
        Map<String, Object> stringObjectMap = client
                .customResource(rateLimitCrdContext)
                .get(namespace, name);
        return YAML_MAPPER.convertValue(stringObjectMap, RateLimiter.class);
    }

    @SneakyThrows
    public RateLimiterConfig getRateLimiterConfig(String name) {
        Map<String, Object> stringObjectMap = client
                .customResource(rateLimitConfigCrdContext)
                .get(namespace, name);
        return YAML_MAPPER.convertValue(stringObjectMap, RateLimiterConfig.class);
    }

    @SneakyThrows
    public K8sRequester createRateLimiter(RateLimiter rateLimiter) {
        client.customResource(rateLimitCrdContext)
                .create(rateLimiter.getMetadata().getNamespace(), YAML_MAPPER.writeValueAsString(rateLimiter));
        TimeUnit.MILLISECONDS.sleep(2_000);
        return this;
    }

    @SneakyThrows
    public K8sRequester createRateLimiterConfig(RateLimiterConfig rateLimiterConfig) {
        client.customResource(rateLimitConfigCrdContext)
                .create(rateLimiterConfig.getMetadata().getNamespace(), YAML_MAPPER.writeValueAsString(rateLimiterConfig));
        TimeUnit.MILLISECONDS.sleep(2_000);
        return this;
    }

    @SneakyThrows
    public Deployment getDeployment(String name) {
        return client.apps().deployments().list().getItems()
                .stream()
                .filter(deployment -> deployment.getMetadata().getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Non deployment: " + name));
    }

    @SneakyThrows
    public void editRateLimiter(RateLimiter rateLimiter) {
        client.customResource(rateLimitCrdContext)
                .edit(namespace, rateLimiter.getMetadata().getName(),
                        YAML_MAPPER.writeValueAsString(rateLimiter));
        TimeUnit.MILLISECONDS.sleep(2_000);
    }

    public Resource<ConfigMap, DoneableConfigMap> getConfigMap(String name) {
        return client.configMaps()
                .inNamespace(namespace)
                .withName(name);
    }

    public List<Service> getServices() {
        return client.services().list().getItems();

    }

    public void deleteRateLimiter(String name) {
        try {
            client.customResource(rateLimitCrdContext).delete(namespace, name);
            TimeUnit.MILLISECONDS.sleep(2_000);
            log.warn("Rate limiter: [{}] deleted", name);
        } catch (Exception e) {
            log.warn("Rate limiter: [{}] hasn't been deleted", name);
        }
    }

    public void deleteRateLimiterConfig(String name) {
        try {
            client.customResource(rateLimitConfigCrdContext).delete(namespace, name);
            TimeUnit.MILLISECONDS.sleep(2_000);
            log.warn("Rate limiter: [{}] deleted", name);
        } catch (Exception e) {
            log.warn("Rate limiter: [{}] hasn't been deleted", name);
        }
    }

    public Map<String, Object> getEnvoyFilter(String name) {
        return client.customResource(envoyFilterContext)
                .get(namespace, name);
    }


    public void deleteDeployment(String name) {
        try {
            Deployment deployment = getDeployment(name);
            client.apps().deployments().delete(deployment);
            log.warn("Rate limiter Deployment: [{}] deleted", name);
        } catch (Exception e) {
            log.warn("Rate limiter: [{}] hasn't been deleted", name);
        }
    }

    public void deleteEnvoyFilter(String name) {
        try {
            client.customResource(envoyFilterContext).delete(namespace, name);
            log.warn("EnvoyFilter: [{}] deleted", name);
        } catch (Exception e) {
            log.warn("EnvoyFilter: [{}] hasn't been deleted", name);
        }
    }

    public void deleteService(String serviceName) {
        try {
            List<Service> serviceList = getServices().stream()
                    .filter(service -> service.getMetadata().getName().equals(serviceName))
                    .collect(Collectors.toList());
            client.services().delete(serviceList);
            log.warn("Service: [{}] deleted", serviceName);
        } catch (Exception e) {
            log.warn("Service: [{}] hasn't been deleted", serviceName);
        }
    }

    public void deleteConfigMap(String name) {
        try {
            client.configMaps().delete(getConfigMap(name).get());
            log.warn("ConfigMap: [{}] deleted", name);
        } catch (Exception e) {
            log.warn("ConfigMap: [{}] hasn't been deleted", name);
        }
    }

    @SneakyThrows
    public void editRateLimiterConfig(RateLimiterConfig currentRateLimiterConfig) {
        client.customResource(rateLimitConfigCrdContext)
                .edit(currentRateLimiterConfig.getMetadata().getNamespace(), currentRateLimiterConfig.getMetadata().getName(),
                        YAML_MAPPER.writeValueAsString(currentRateLimiterConfig));
        TimeUnit.MILLISECONDS.sleep(2_000);
    }

    @SneakyThrows
    public void editService(Service service) {
        client.services().createOrReplace(service);
        TimeUnit.MILLISECONDS.sleep(2_000);
    }

    @SneakyThrows
    public void editEnvoyFilter(EnvoyFilter envoyFilter) {
        client.customResource(envoyFilterContext).edit(envoyFilter.getMetadata().getNamespace(),
                envoyFilter.getMetadata().getName(), YAML_MAPPER.writeValueAsString(envoyFilter));
        TimeUnit.MILLISECONDS.sleep(2_000);
    }
}
