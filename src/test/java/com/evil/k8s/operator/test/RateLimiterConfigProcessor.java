package com.evil.k8s.operator.test;

import com.evil.k8s.operator.test.models.ConfigMapRateLimitProperty;
import com.evil.k8s.operator.test.models.EnvoyClusterPatch;
import com.evil.k8s.operator.test.models.EnvoyGatewayPatch;
import com.evil.k8s.operator.test.models.EnvoyHttpFilterPatch;
import com.evil.k8s.operator.test.models.RateLimiterConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.ConfigMap;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.snowdrop.istio.api.networking.v1alpha3.ClusterObjectTypes;
import me.snowdrop.istio.api.networking.v1alpha3.EnvoyConfigObjectPatch;
import me.snowdrop.istio.api.networking.v1alpha3.EnvoyFilter;
import me.snowdrop.istio.api.networking.v1alpha3.ListenerObjectTypes;
import me.snowdrop.istio.api.networking.v1alpha3.RouteConfigurationObjectTypes;

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

    private List<RateLimiterConfig> rateLimiterConfigs = new LinkedList<>();

    private RateLimiterConfig currentRateLimiterConfig;


    public RateLimiterConfigProcessor create(RateLimiterConfig rateLimiterConfig) {
        currentRateLimiterConfig = rateLimiterConfig;
        rateLimiterConfigs.add(rateLimiterConfig);
        requester.createRateLimiterConfig(rateLimiterConfig);
        return this;
    }

    public RateLimiterConfigProcessor validateRatelimiterConfig() {
        assertEquals(currentRateLimiterConfig, requester.getRateLimiterConfig(currentRateLimiterConfig.getMetadata().getName()));
        return this;
    }

    @SneakyThrows
    public RateLimiterConfigProcessor validateConfigMap() {
        Map<String, String> configData = requester.getConfigMap(currentRateLimiterConfig.getSpec().getRateLimiter()).get().getData();

        String configMapDescriptors = configData
                .get(currentRateLimiterConfig.getMetadata().getName() + ".yaml");
        assertNotNull(configMapDescriptors, "Config map data is Null for file:" + currentRateLimiterConfig.getMetadata().getName());

        ConfigMapRateLimitProperty configMapRateLimitProperty =
                YAML_MAPPER.readValue(configMapDescriptors, ConfigMapRateLimitProperty.class);
        assertEquals(configMapRateLimitProperty.getDescriptors(), currentRateLimiterConfig.getSpec().getDescriptors());

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
        return this;
    }

    public RateLimiterConfigProcessor validateEnvoyFilter() {
        String namespace = currentRateLimiterConfig.getMetadata().getNamespace();
        String rateLimiterConfigName = currentRateLimiterConfig.getMetadata().getName();
        String rateLimiterName = currentRateLimiterConfig.getSpec().getRateLimiter();

        Map<String, Object> stringObjectMap = requester.getEnvoyFilter(rateLimiterConfigName);
        EnvoyFilter envoyFilter = YAML_MAPPER.convertValue(stringObjectMap, EnvoyFilter.class);

        //applyTo: HTTP_FILTER
        EnvoyConfigObjectPatch envoyFilterConfigPatchesHttpFilter = envoyFilter.getSpec().getConfigPatches().stream()
                .filter(i -> i.getApplyTo().name().equals("HTTP_FILTER"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Dont find HTTP_FILTER block from envoy filter"));

        // check→ context: GATEWAY
        assertEquals(currentRateLimiterConfig.getSpec().getApplyTo().toString(),
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
        assertEquals(currentRateLimiterConfig.getMetadata().getName(), envoyRateLimit.getConfig().getDomain());
        assertEquals("patched." + rateLimiterName + "." + namespace + ".svc.cluster.local",
                envoyRateLimit.getConfig().getRateLimitService().getGrpcService().getEnvoyGrpc().getCluster_name());
        assertEquals(currentRateLimiterConfig.getSpec().getRateLimitRequestTimeout(),
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
        assertEquals(currentRateLimiterConfig.getSpec().getApplyTo().toString(), envoyFilterConfigPatchesVirtualHost
                .getMatch().getContext().name()); // check context: GATEWAY

        // check routeConfiguration->vhost->name
        RouteConfigurationObjectTypes routeConfigurationObjectTypes =
                (RouteConfigurationObjectTypes) envoyFilterConfigPatchesVirtualHost.getMatch().getObjectTypes();
        switch (currentRateLimiterConfig.getSpec().getApplyTo()) {
            case GATEWAY:
            case ANY:
            case SIDECAR_OUTBOUND:
                assertEquals(currentRateLimiterConfig.getSpec().getHost() + ":" + currentRateLimiterConfig.getSpec()
                        .getPort(), routeConfigurationObjectTypes.getRouteConfiguration().getVhost().getName());
                break;
            case SIDECAR_INBOUND:
                assertEquals("inbound|http|" + currentRateLimiterConfig.getSpec()
                        .getPort(), routeConfigurationObjectTypes.getRouteConfiguration().getVhost().getName());
                break;
        }

        // check operation: MERGE
        assertEquals("MERGE", envoyFilterConfigPatchesVirtualHost.getPatch().getOperation().name());

        // patch -> value -> rate_limits->actions->request_headers->{descriptor_key, header_name}
        EnvoyGatewayPatch envoyClusterPatch = YAML_MAPPER.convertValue(envoyFilterConfigPatchesVirtualHost.getPatch().getValue(), EnvoyGatewayPatch.class);

        assertEquals(currentRateLimiterConfig.getSpec().getRateLimits(), envoyClusterPatch.getRateLimits());
        assertEquals(currentRateLimiterConfig.getSpec().getWorkloadSelector().getLabels(), envoyFilter.getSpec().getWorkloadSelector().getLabels());

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
        log.warn("Rate limiter config: [{}] was deleted", name);
        return this;
    }

    @SneakyThrows
    public RateLimiterConfigProcessor deleteEnvoyFilter() {
        requester.deleteEnvoyFilter(currentRateLimiterConfig.getMetadata().getName());
        return this;
    }

    public RateLimiterConfigProcessor editEnvoyFilter(Consumer<EnvoyFilter> consumer) {
        Map<String, Object> stringObjectMap = requester.getEnvoyFilter(currentRateLimiterConfig.getMetadata().getName());
        EnvoyFilter envoyFilter = YAML_MAPPER.convertValue(stringObjectMap, EnvoyFilter.class);
        consumer.accept(envoyFilter);
        requester.editEnvoyFilter(envoyFilter);
        return this;
    }

    @SneakyThrows
    public RateLimiterConfigProcessor edit(Consumer<RateLimiterConfig> function) {
        function.accept(currentRateLimiterConfig);
        requester.editRateLimiterConfig(currentRateLimiterConfig);
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
}
