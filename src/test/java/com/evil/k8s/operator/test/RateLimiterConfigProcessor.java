package com.evil.k8s.operator.test;

import com.evil.k8s.operator.test.models.EnvoyClusterPatch;
import com.evil.k8s.operator.test.models.EnvoyGatewayPatch;
import com.evil.k8s.operator.test.models.EnvoyHttpFilterPatch;
import com.evil.k8s.operator.test.models.RateLimiterConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.snowdrop.istio.api.networking.v1alpha3.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.evil.k8s.operator.test.CustomResourcesConstants.envoyFilterContext;
import static com.evil.k8s.operator.test.CustomResourcesConstants.rateLimitConfigCrdContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@RequiredArgsConstructor
public class RateLimiterConfigProcessor implements AutoCloseable {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private final KubernetesClient client;
    private final String namespace;

    private List<RateLimiterConfig> rateLimiterConfigs = new LinkedList<>();

    private RateLimiterConfig currentRateLimiterConfig;


    @SneakyThrows
    public RateLimiterConfigProcessor create(RateLimiterConfig rateLimiterConfig) {
        currentRateLimiterConfig = rateLimiterConfig;
        rateLimiterConfigs.add(rateLimiterConfig);
        client.customResource(rateLimitConfigCrdContext)
                .create(rateLimiterConfig.getMetadata().getNamespace(), YAML_MAPPER.writeValueAsString(rateLimiterConfig));
        return this;
    }

    //ToDo: Раскомментировать
    public RateLimiterConfigProcessor validateRatelimiterConfig() {
        Map<String, Object> stringObjectMap = client
                .customResource(rateLimitConfigCrdContext)
                .get(currentRateLimiterConfig.getMetadata().getNamespace(), currentRateLimiterConfig.getMetadata().getName());
        RateLimiterConfig rateLimiterConfig = YAML_MAPPER.convertValue(stringObjectMap, RateLimiterConfig.class);
        assertEquals(currentRateLimiterConfig, rateLimiterConfig);
        return this;
    }

    @SneakyThrows
    public RateLimiterConfigProcessor validateConfigMap() {
        Resource<ConfigMap, DoneableConfigMap> configMapResource = client.configMaps()
                .inNamespace(currentRateLimiterConfig.getMetadata().getClusterName())
                .withName(currentRateLimiterConfig.getSpec().getRateLimiter());

        String configMapDescriptors = configMapResource.get().getData()
                .get(currentRateLimiterConfig.getMetadata().getName() + ".yaml");
        assertNotNull(configMapDescriptors, "Config map data is Null!");
        RateLimiterConfig.RateLimitProperty configMapRateLimitProperty =
                YAML_MAPPER.readValue(configMapDescriptors, RateLimiterConfig.RateLimitProperty.class);
        assertEquals(configMapRateLimitProperty, currentRateLimiterConfig.getSpec().getRateLimitProperty());

        List<String> domains = configMapResource.get().getData().values().stream()
                .map(s -> {
                    try {
                        return YAML_MAPPER.readValue(configMapDescriptors, RateLimiterConfig.RateLimitProperty.class);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(RateLimiterConfig.RateLimitProperty::getDomain)
                .collect(Collectors.toList());
        assertEquals(domains.size(), new HashSet<>(domains).size(), "Exists not unique domain!!!");
        return this;
    }

    public RateLimiterConfigProcessor validateEnvoyFilter() {

        String namespace = currentRateLimiterConfig.getMetadata().getNamespace();
        String rateLimiterConfigName = currentRateLimiterConfig.getMetadata().getName();
        String rateLimiterName = currentRateLimiterConfig.getSpec().getRateLimiter();

        Map<String, Object> stringObjectMap = client
                .customResource(envoyFilterContext)
                .get(namespace, rateLimiterConfigName);
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
        assertEquals("host-info", envoyRateLimit.getConfig().getDomain());
        assertEquals("patched." + rateLimiterName + "." + namespace + ".svc.cluster.local",
                envoyRateLimit.getConfig().getRateLimitService().getGrpcService().getEnvoyGrpc().getCluster_name());
        assertEquals("0.25s", envoyRateLimit.getConfig().getRateLimitService().getGrpcService().getTimeout());


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
            case GATEWAY, ANY, SIDECAR_OUTBOUND -> assertEquals(currentRateLimiterConfig.getSpec().getHost() + ":" + currentRateLimiterConfig.getSpec()
                    .getPort(), routeConfigurationObjectTypes.getRouteConfiguration().getVhost().getName());
            case SIDECAR_INBOUND -> assertEquals("inbound|http|" + currentRateLimiterConfig.getSpec()
                    .getPort(), routeConfigurationObjectTypes.getRouteConfiguration().getVhost().getName());
        }

        // check operation: MERGE
        assertEquals("MERGE", envoyFilterConfigPatchesVirtualHost.getPatch().getOperation().name());

        // patch -> value -> rate_limits->actions->request_headers->{descriptor_key, header_name}
        EnvoyGatewayPatch envoyClusterPatch = YAML_MAPPER.convertValue(envoyFilterConfigPatchesVirtualHost.getPatch().getValue(), EnvoyGatewayPatch.class);
        assertEquals(currentRateLimiterConfig.getSpec().getRateLimitProperty().getDescriptors().get(0).getKey(),
                envoyClusterPatch.getRateLimits().get(0).getActions().get(0).getRequestHeaders().getDescriptionKey());
        assertEquals(currentRateLimiterConfig.getSpec().getRateLimitProperty().getDescriptors().get(0).getKey(),
                envoyClusterPatch.getRateLimits().get(0).getActions().get(0).getRequestHeaders().getHeaderName());

        return this;
    }


    @Override
    public void close() {
        rateLimiterConfigs.forEach(this::delete);
        rateLimiterConfigs.clear();
    }

    @SneakyThrows
    public RateLimiterConfigProcessor delay(int delayMs) {
        TimeUnit.MILLISECONDS.sleep(delayMs);
        return this;
    }

    public RateLimiterConfigProcessor delete() {
        rateLimiterConfigs.remove(currentRateLimiterConfig);
        return delete(currentRateLimiterConfig);
    }

    public RateLimiterConfigProcessor delete(RateLimiterConfig rateLimiterConfig) {
        return delete(rateLimiterConfig.getMetadata().getNamespace(), rateLimiterConfig.getMetadata().getName());
    }

    private RateLimiterConfigProcessor delete(String namespace, String name) {
        try {
            rateLimiterConfigs.removeIf(rateLimiterConfig -> rateLimiterConfig.getMetadata().getName().equals(name)
                    && rateLimiterConfig.getMetadata().getNamespace().equals(namespace));
            client.customResource(rateLimitConfigCrdContext)
                    .delete(namespace, name);
            log.warn("Rate limiter config: [{}] has deleted", name);
        } catch (IOException e) {
            log.warn("Rate limiter config: [{}] hasn't been deleted", name);
        }
        return this;
    }

    @SneakyThrows
    public RateLimiterConfigProcessor edit(Consumer<RateLimiterConfig> function) {
        function.accept(currentRateLimiterConfig);
        client.customResource(rateLimitConfigCrdContext)
                .edit(currentRateLimiterConfig.getMetadata().getNamespace(), currentRateLimiterConfig.getMetadata().getName(),
                        YAML_MAPPER.writeValueAsString(currentRateLimiterConfig));
        try {
            TimeUnit.MILLISECONDS.sleep(1_000);
        } catch (Exception ex) {
            log.error("", ex);
        }
        return this;
    }
}
