package com.evil.k8s.operator.test;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.SneakyThrows;
import me.snowdrop.istio.api.networking.v1alpha3.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.TimeUnit;


import static org.junit.jupiter.api.Assertions.*;


public class RateLimitTests extends K8sRateLimitAbstractTest {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final String namespace = "test-project";
    private final String rateLimiterName = "rate-limiter";
    private final String rateLimiterConfigName = "rate-limiter-config-test";
    private final String rateLimiterConfigMapName = "rate-limiter";

    @Test
    @Order(1)
    @SneakyThrows
    public void createRateLimiter() {
        client.customResource(rateLimitCrdContext)
                .create(namespace, yamlMapper.writeValueAsString(preparedRateLimiter()));
        TimeUnit.SECONDS.sleep(10);
        Map<String, Object> stringObjectMap =
                client.customResource(rateLimitCrdContext).get(namespace, rateLimiterName);
        assertNotNull(stringObjectMap);
    }

    @Test
    @Order(2)
    @SneakyThrows
    public void createRateLimiterConfig() {
        client.customResource(rateLimitConfigCrdContext)
                .create(namespace, yamlMapper.writeValueAsString(preparedRateLimiterConfig()));
        Map<String, Object> stringObjectMap =
                client.customResource(rateLimitConfigCrdContext).get(namespace, rateLimiterConfigName);
        assertNotNull(stringObjectMap);
    }

    @Test
    @SneakyThrows
    @Disabled("delete test")
    public void deleteRateLimiterConfig() {
        client.customResource(rateLimitConfigCrdContext).delete(namespace, rateLimiterConfigName);
    }

    @Test
    @Order(3)
    @SneakyThrows
    public void rateLimiterConfig() {
        Map<String, Object> stringObjectMap = client.customResource(rateLimitConfigCrdContext)
                .get(namespace, rateLimiterConfigName);
        ObjectMapper objectMapper = Serialization.jsonMapper();
        RateLimiterConfig rateLimiterConfig = objectMapper.convertValue(stringObjectMap, RateLimiterConfig.class);
        assertNotNull(rateLimiterConfig.getSpec().getHost());
    }

    @Test
    @Order(4)
    @SneakyThrows
    public void validateConfigMap() {
        Resource<ConfigMap, DoneableConfigMap> configMapResource = client.configMaps()
                .inNamespace(namespace).withName(rateLimiterConfigMapName);
        ConfigMap configMap = configMapResource.get();
        Map<String, String> configMapData = configMap.getData();
        // List<String> ↓↓↓↓ ?
        String configMapDescriptors = configMapData.get(rateLimiterConfigName + ".yaml");
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        RateLimiterConfig.RateLimitProperty configMapRateLimitProperty =
                yamlMapper.readValue(configMapDescriptors, RateLimiterConfig.RateLimitProperty.class);
        RateLimiterConfig rateLimiterConfig = preparedRateLimiterConfig();
        assertEquals(configMapRateLimitProperty, rateLimiterConfig.getSpec().getRateLimitProperty());
    }

    @Test
    @Order(5)
    @SneakyThrows
    public void validateEnvoyFilter() {
        Map<String, Object> stringObjectMap = client.customResource(envoyFilterContext)
                .get(namespace, rateLimiterConfigName);
        ObjectMapper objectMapper = Serialization.jsonMapper();
        EnvoyFilter envoyFilter = objectMapper.convertValue(stringObjectMap, EnvoyFilter.class);

        RateLimiterConfig rateLimiterConfig = preparedRateLimiterConfig();

        //VIRTUAL_HOST
        EnvoyConfigObjectPatch envoyFilterConfigPatchesVirtualHost = envoyFilter.getSpec().getConfigPatches()
                .stream().filter(i -> i.getApplyTo().name().equals("VIRTUAL_HOST")).findFirst().get();
        assertEquals(rateLimiterConfig.getSpec().getApplyTo(), envoyFilterConfigPatchesVirtualHost
                .getMatch().getContext().name()); // check context: GATEWAY

        // check routeConfiguration->vhost->name
        RouteConfigurationObjectTypes routeConfigurationObjectTypes =
                (RouteConfigurationObjectTypes) envoyFilterConfigPatchesVirtualHost.getMatch().getObjectTypes();
        assertEquals(rateLimiterConfig.getSpec().getHost() + ":" + rateLimiterConfig.getSpec()
                .getPort(), routeConfigurationObjectTypes.getRouteConfiguration().getVhost().getName());

        assertEquals("MERGE", envoyFilterConfigPatchesVirtualHost.getPatch()
                .getOperation().name()); // check operation: MERGE

        // patch -> value -> rate_limits->actions->request_headers->{descriptor_key, header_name}
        assertEquals(true, envoyFilterConfigPatchesVirtualHost.getPatch().getValue()
                .toString().contains("descriptor_key=RateLimiterConfigDescriptors")); // check descriptor_key
        assertEquals(true, envoyFilterConfigPatchesVirtualHost.getPatch()
                .getValue().toString().contains("header_name=RateLimiterConfigDescriptors")); // check header_name

        //applyTo: HTTP_FILTER
        EnvoyConfigObjectPatch envoyFilterConfigPatchesHttpFilter = envoyFilter.getSpec().getConfigPatches()
                .stream().filter(i -> i.getApplyTo().name().equals("HTTP_FILTER")).findFirst().get();
        // check context: GATEWAY
        assertEquals(rateLimiterConfig.getSpec().getApplyTo(),
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
        assertEquals(true, envoyFilterConfigPatchesHttpFilter
                .getPatch().getValue().toString().contains("domain=host-info")); // check domain
        assertEquals(true, envoyFilterConfigPatchesHttpFilter
                .getPatch().getValue().toString()
                .contains("cluster_name=patched.rate-limiter.test-project.svc.cluster.local")); // check  cluster_name
        assertEquals(true, envoyFilterConfigPatchesHttpFilter
                .getPatch().getValue().toString().contains("timeout=0.25s")); // check  timeout

        //applyTo: CLUSTER
        EnvoyConfigObjectPatch envoyFilterConfigPatchesCluster = envoyFilter.getSpec().getConfigPatches().stream()
                .filter(i -> i.getApplyTo()
                        .name()
                        .equals("CLUSTER"))
                .findFirst().get();
        // match -> cluster -> service
        ClusterObjectTypes clusterMatch = (ClusterObjectTypes) envoyFilterConfigPatchesCluster
                .getMatch()
                .getObjectTypes();
        assertEquals("rate-limiter.test-project.svc.cluster.local", clusterMatch
                .getCluster()
                .getService()); // check service

        // check patch:
        //        operation: MERGE
        assertEquals("MERGE", envoyFilterConfigPatchesCluster.getPatch().getOperation().name());

        // check patch->value->name
        assertEquals(true, envoyFilterConfigPatchesCluster
                .getPatch().getValue().toString().contains("name=patched.rate-limiter.test-project.svc.cluster.local"));

    }


    @Test
    @SneakyThrows
    private RateLimiter preparedRateLimiter() {
        ObjectMeta objectMeta = new ObjectMeta();
        objectMeta.setName(rateLimiterName);
        objectMeta.setNamespace(namespace);

        WorkloadSelector workloadSelector = new WorkloadSelector(Collections.singletonMap("app", "application-app"));

        RateLimiter.RateLimiterSpec rateLimiterSpec = new RateLimiter.RateLimiterSpec();
        rateLimiterSpec.setPort(1488);
        rateLimiterSpec.setLogLevel("INFO");

        return RateLimiter.builder()
                .metadata(objectMeta)
                .workloadSelector(workloadSelector)
                .spec(rateLimiterSpec)
                .build();
    }

    @Test
    @SneakyThrows
    private RateLimiterConfig preparedRateLimiterConfig() {
        ObjectMeta objectMeta = new ObjectMeta();
        objectMeta.setName(rateLimiterConfigName);
        objectMeta.setNamespace(namespace);

        WorkloadSelector workloadSelector = new WorkloadSelector(Collections.singletonMap("app", "application-app"));

        RateLimiterConfig.RateLimit rateLimit = new RateLimiterConfig.RateLimit();
        rateLimit.setRequestsPerUnit(1);
        rateLimit.setUnit("minute");

        RateLimiterConfig.RateLimiterConfigDescriptors rateLimiterConfigDescriptors =
                new RateLimiterConfig.RateLimiterConfigDescriptors();
        rateLimiterConfigDescriptors.setKey("RateLimiterConfigDescriptors");
        rateLimiterConfigDescriptors.setValue("setting1");
        rateLimiterConfigDescriptors.setRateLimit(rateLimit);

        RateLimiterConfig.RateLimitProperty rateLimitProperty = new RateLimiterConfig.RateLimitProperty();
        rateLimitProperty.setDescriptors(Arrays.asList(rateLimiterConfigDescriptors));
        rateLimitProperty.setDomain("host-info");

        RateLimiterConfig.RateLimiterConfigSpec rateLimiterConfigSpec = new RateLimiterConfig.RateLimiterConfigSpec();
        rateLimiterConfigSpec.setApplyTo("GATEWAY");
        rateLimiterConfigSpec.setHost("host-info-srv.org");
        rateLimiterConfigSpec.setPort(80);
        rateLimiterConfigSpec.setRateLimiter("rate-limiter");
        rateLimiterConfigSpec.setRateLimitProperty(rateLimitProperty);

        return RateLimiterConfig.builder()
                .metadata(objectMeta)
                .workloadSelector(workloadSelector)
                .spec(rateLimiterConfigSpec)
                .build();

    }
}
