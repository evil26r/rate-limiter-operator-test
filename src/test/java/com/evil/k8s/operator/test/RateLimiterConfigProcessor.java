package com.evil.k8s.operator.test;

import com.evil.k8s.operator.test.models.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.snowdrop.istio.api.networking.v1alpha3.*;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.evil.k8s.operator.test.utils.Utils.YAML_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@RequiredArgsConstructor
public class RateLimiterConfigProcessor implements AutoCloseable {

    private final K8sRequester requester;

    private final List<RateLimiterConfig> rateLimiterConfigs = new LinkedList<>();

    private RateLimiterConfig currentRateLimiterConfig;

    private Status currentStatus = Status.UNDEFINED;

    public RateLimiterConfigProcessor create(RateLimiterConfig rateLimiterConfig) {
        currentStatus = Status.CREATED;
        currentRateLimiterConfig = rateLimiterConfig;
        rateLimiterConfigs.add(rateLimiterConfig);
        requester.createRateLimiterConfig(rateLimiterConfig);
        return this;
    }

    public RateLimiterConfigProcessor edit(Consumer<RateLimiterConfig> function) {
        function.accept(currentRateLimiterConfig);
        requester.editRateLimiterConfig(currentRateLimiterConfig);
        return this;
    }

    public RateLimiterConfigProcessor validateRatelimiterConfig() {
        currentStatus.getInstance().validateRatelimiterConfig(currentRateLimiterConfig, requester);
        return this;
    }

    public RateLimiterConfigProcessor validateConfigMap() {
        currentStatus.getInstance().validateConfigMap(currentRateLimiterConfig, requester);
        return this;
    }

    @SneakyThrows
    public RateLimiterConfigProcessor editConfigMap(Consumer<List<RateLimiterConfig.RateLimiterConfigDescriptors>> rateLimitDescriptorsConsumer) {
        ConfigMap configMap = requester.getConfigMap(currentRateLimiterConfig.getSpec().getRateLimiter()).get();
        Map<String, String> configData = configMap.getData();
        String yamlFileName = currentRateLimiterConfig.getMetadata().getName() + ".yaml";
        String configMapDescriptors = configData.get(yamlFileName);

        ConfigMapRateLimitProperty configMapRateLimitProperty =
                YAML_MAPPER.readValue(configMapDescriptors, ConfigMapRateLimitProperty.class);

        rateLimitDescriptorsConsumer.accept(configMapRateLimitProperty.getDescriptors());

        configData.replace(yamlFileName, YAML_MAPPER.writeValueAsString(configMapRateLimitProperty));

        configMap.setData(configData);
        requester.editConfigMap(configMap);
        return this;
    }

    public RateLimiterConfigProcessor validateEnvoyFilter() {
        currentStatus.getInstance().validateEnvoyFilter(currentRateLimiterConfig, requester);
        return this;
    }

    public RateLimiterConfigProcessor editEnvoyFilter(Consumer<EnvoyFilter> consumer) {
        Map<String, Object> stringObjectMap = requester.getEnvoyFilter(currentRateLimiterConfig.getMetadata().getName());
        EnvoyFilter envoyFilter = YAML_MAPPER.convertValue(stringObjectMap, EnvoyFilter.class);
        consumer.accept(envoyFilter);
        requester.editEnvoyFilter(envoyFilter);
        return this;
    }

    @Override
    public void close() {
        rateLimiterConfigs.stream().forEach(this::delete);
        rateLimiterConfigs.clear();
    }

    public RateLimiterConfigProcessor delete() {
        rateLimiterConfigs.remove(currentRateLimiterConfig);
        return delete(currentRateLimiterConfig);
    }

    public RateLimiterConfigProcessor delete(RateLimiterConfig rateLimiterConfig) {
        return delete(rateLimiterConfig.getMetadata().getNamespace(), rateLimiterConfig.getMetadata().getName());
    }

    private RateLimiterConfigProcessor delete(String namespace, String name) {
        requester.deleteRateLimiterConfig(name);
        currentStatus = Status.REMOVED;
        log.warn("Rate limiter config: [{}] was deleted", name);
        return this;
    }

    public RateLimiterConfigProcessor deleteEnvoyFilter() {
        requester.deleteEnvoyFilter(currentRateLimiterConfig.getMetadata().getName());
        return this;
    }

    private enum Status {
        UNDEFINED,
        CREATED,
        REMOVED;

        private static final RateLimiterConfigValidator created = new RateLimiterConfigCreatedValidator();
        private static final RateLimiterConfigValidator removed = new RateLimiterConfigRemovedValidator();
        private static final RateLimiterConfigValidator undefined = new RateLimiterConfigValidator() {

            @Override
            public void validateRatelimiterConfig(RateLimiterConfig rateLimiterConfig, K8sRequester requester) {
                throw new UnsupportedOperationException("Illegal operation with status UNDEFINED");
            }

            @Override
            public void validateConfigMap(RateLimiterConfig rateLimiterConfig, K8sRequester requester) {
                throw new UnsupportedOperationException("Illegal operation with status UNDEFINED");
            }

            @Override
            public void validateEnvoyFilter(RateLimiterConfig rateLimiterConfig, K8sRequester requester) {
                throw new UnsupportedOperationException("Illegal operation with status UNDEFINED");
            }
        };

        RateLimiterConfigValidator getInstance() {
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

    interface RateLimiterConfigValidator {
        void validateRatelimiterConfig(RateLimiterConfig rateLimiterConfig, K8sRequester requester);

        void validateConfigMap(RateLimiterConfig rateLimiterConfig, K8sRequester requester);

        void validateEnvoyFilter(RateLimiterConfig rateLimiterConfig, K8sRequester requester);

    }

    public static class RateLimiterConfigCreatedValidator implements RateLimiterConfigValidator {

        @Override
        public void validateRatelimiterConfig(RateLimiterConfig rateLimiterConfig, K8sRequester requester) {
            assertEquals(rateLimiterConfig, requester.getRateLimiterConfig(rateLimiterConfig.getMetadata().getName()));
        }

        @SneakyThrows
        @Override
        public void validateConfigMap(RateLimiterConfig rateLimiterConfig, K8sRequester requester) {
            Map<String, String> configData = requester.getConfigMap(rateLimiterConfig.getSpec().getRateLimiter()).get().getData();

            String configMapDescriptors = configData
                    .get(rateLimiterConfig.getMetadata().getName() + ".yaml");
            assertNotNull(configMapDescriptors, "Config map data is Null for file:" + rateLimiterConfig.getMetadata().getName());

            ConfigMapRateLimitProperty configMapRateLimitProperty =
                    YAML_MAPPER.readValue(configMapDescriptors, ConfigMapRateLimitProperty.class);
            assertEquals(configMapRateLimitProperty.getDescriptors(), rateLimiterConfig.getSpec().getDescriptors());

            List<String> domains = configData.values().stream()
                    .map(descriptors -> {
                        try {
                            return YAML_MAPPER.readValue(descriptors, ConfigMapRateLimitProperty.class);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(ConfigMapRateLimitProperty::getDomain)
                    .collect(Collectors.toList());
            assertEquals(domains.size(), new HashSet<>(domains).size(), "Exists not unique domain!!!");
        }

        @Override
        public void validateEnvoyFilter(RateLimiterConfig rateLimiterConfig, K8sRequester requester) {
            String namespace = rateLimiterConfig.getMetadata().getNamespace();
            String rateLimiterConfigName = rateLimiterConfig.getMetadata().getName();
            String rateLimiterName = rateLimiterConfig.getSpec().getRateLimiter();

            Map<String, Object> stringObjectMap = requester.getEnvoyFilter(rateLimiterConfigName);
            EnvoyFilter envoyFilter = YAML_MAPPER.convertValue(stringObjectMap, EnvoyFilter.class);

            //applyTo: HTTP_FILTER
            EnvoyConfigObjectPatch envoyFilterConfigPatchesHttpFilter = envoyFilter.getSpec().getConfigPatches().stream()
                    .filter(i -> i.getApplyTo().name().equals("HTTP_FILTER"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Dont find HTTP_FILTER block from envoy filter"));

            // check→ context: GATEWAY
            assertEquals(rateLimiterConfig.getSpec().getApplyTo().toString(),
                    envoyFilterConfigPatchesHttpFilter.getMatch().getContext().name());

            // match-> listener -> filterChain->filter-> {name, subFilter->name}
            ListenerObjectTypes listenerMatch = (ListenerObjectTypes) envoyFilterConfigPatchesHttpFilter
                    .getMatch().getObjectTypes();
            assertEquals("envoy.http_connection_manager", listenerMatch.getListener()
                    .getFilterChain().getFilter().getName());
            assertEquals("envoy.router", listenerMatch.getListener().getFilterChain()
                    .getFilter().getSubFilter().getName());

            // check patch:
            //        operation: INSERT_BEFORE
            assertEquals("INSERT_BEFORE", envoyFilterConfigPatchesHttpFilter.getPatch().getOperation().name());

            // patch -> value -> config -> {domain , ...->cluster_name}
            EnvoyHttpFilterPatch envoyRateLimit = YAML_MAPPER.convertValue(envoyFilterConfigPatchesHttpFilter.getPatch().getValue(), EnvoyHttpFilterPatch.class);
            assertEquals("envoy.rate_limit", envoyRateLimit.getName());
            assertEquals(rateLimiterConfig.getMetadata().getName(), envoyRateLimit.getConfig().getDomain());
            assertEquals("patched." + rateLimiterName + "." + namespace + ".svc.cluster.local",
                    envoyRateLimit.getConfig().getRateLimitService().getGrpcService().getEnvoyGrpc().getCluster_name());
            assertEquals(rateLimiterConfig.getSpec().getRateLimitRequestTimeout(),
                    envoyRateLimit.getConfig().getRateLimitService().getGrpcService().getTimeout());


            //applyTo: CLUSTER
            EnvoyConfigObjectPatch envoyFilterConfigPatchesCluster = envoyFilter.getSpec().getConfigPatches().stream()
                    .filter(i -> i.getApplyTo().name().equals("CLUSTER"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Dont find CLUSTER block from envoy filter"));
            // match -> cluster -> service
            ClusterObjectTypes clusterMatch = (ClusterObjectTypes) envoyFilterConfigPatchesCluster.getMatch().getObjectTypes();
            assertEquals(rateLimiterName + "." + namespace + ".svc.cluster.local",
                    clusterMatch.getCluster().getService()); // check service

            // check patch:
            //        operation: MERGE
            assertEquals("MERGE", envoyFilterConfigPatchesCluster.getPatch().getOperation().name());

            // check patch->value->name
            EnvoyClusterPatch envoyCluster = YAML_MAPPER.convertValue(envoyFilterConfigPatchesCluster.getPatch().getValue(), EnvoyClusterPatch.class);
            assertEquals("patched." + rateLimiterName + "." + namespace + ".svc.cluster.local", envoyCluster.getName());

            //VIRTUAL_HOST
            EnvoyConfigObjectPatch envoyFilterConfigPatchesVirtualHost = envoyFilter.getSpec().getConfigPatches().stream()
                    .filter(i -> i.getApplyTo().name().equals("VIRTUAL_HOST")).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Dont find VIRTUAL_HOST block from envoy filter"));
            assertEquals(rateLimiterConfig.getSpec().getApplyTo().toString(), envoyFilterConfigPatchesVirtualHost
                    .getMatch().getContext().name()); // check context: GATEWAY

            // check routeConfiguration->vhost->name
            RouteConfigurationObjectTypes routeConfigurationObjectTypes =
                    (RouteConfigurationObjectTypes) envoyFilterConfigPatchesVirtualHost.getMatch().getObjectTypes();
            switch (rateLimiterConfig.getSpec().getApplyTo()) {
                case GATEWAY:
                case ANY:
                case SIDECAR_OUTBOUND:
                    assertEquals(rateLimiterConfig.getSpec().getHost() + ":" + rateLimiterConfig.getSpec()
                            .getPort(), routeConfigurationObjectTypes.getRouteConfiguration().getVhost().getName());
                    break;
                case SIDECAR_INBOUND:
                    assertEquals("inbound|http|" + rateLimiterConfig.getSpec()
                            .getPort(), routeConfigurationObjectTypes.getRouteConfiguration().getVhost().getName());
                    break;
            }

            // check operation: MERGE
            assertEquals("MERGE", envoyFilterConfigPatchesVirtualHost.getPatch().getOperation().name());

            // patch -> value -> rate_limits->actions->request_headers->{descriptor_key, header_name}
            EnvoyGatewayPatch envoyClusterPatch = YAML_MAPPER.convertValue(envoyFilterConfigPatchesVirtualHost.getPatch().getValue(), EnvoyGatewayPatch.class);

            assertEquals(rateLimiterConfig.getSpec().getRateLimits(), envoyClusterPatch.getRateLimits());
            assertEquals(rateLimiterConfig.getSpec().getWorkloadSelector().getLabels(), envoyFilter.getSpec().getWorkloadSelector().getLabels());
        }

    }

    public static class RateLimiterConfigRemovedValidator implements RateLimiterConfigValidator {

        @Override
        public void validateRatelimiterConfig(RateLimiterConfig rateLimiterConfig, K8sRequester requester) {
            try {
                requester.getRateLimiterConfig(rateLimiterConfig.getMetadata().getName());
                throw new IllegalStateException("RatelimiterConfig already exist!");
            } catch (KubernetesClientException exception) {
                log.info("RateLimiterConfig not exist");
            }
        }

        @Override
        public void validateConfigMap(RateLimiterConfig rateLimiterConfig, K8sRequester requester) {
            Resource<ConfigMap, DoneableConfigMap> configMap = requester.getConfigMap(rateLimiterConfig.getSpec().getRateLimiter());
            if (configMap.get() != null) {
                throw new IllegalStateException("Ratelimiter ConfigMap already exist!");
            }
            log.info("RateLimiter ConfigMap not exist");
        }

        @Override
        public void validateEnvoyFilter(RateLimiterConfig rateLimiterConfig, K8sRequester requester) {
            String rateLimiterConfigName = rateLimiterConfig.getMetadata().getName();
            try {
                requester.getEnvoyFilter(rateLimiterConfigName);
                throw new IllegalStateException("EnvoyFilter already exist!");
            } catch (KubernetesClientException ex) {
                log.info("EnvoyFilter not exist");
            }
        }
    }
}
