package com.evil.k8s.operator.test.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Objects;
import java.util.function.Consumer;

import static com.evil.k8s.operator.test.utils.Utils.generateRedisName;
import static com.fasterxml.jackson.annotation.JsonInclude.Include;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonDeserialize
public class RateLimiter {

    private String kind = "RateLimiter";

    private String apiVersion = "operators.example.com/v1";

    @JsonInclude(Include.NON_ABSENT)
    private RateLimiterSpec spec;

    @JsonInclude(Include.NON_ABSENT)
    private ObjectMeta metadata;

    @JsonIgnore
    private KubernetesClient client;

    public RateLimiter(KubernetesClient client) {
        this.client = client;
    }

    public RateLimiter updateSpec(Consumer<RateLimiterSpec> consumerSpec) {
        if (spec == null) {
            spec = new RateLimiterSpec();
        }
        consumerSpec.accept(spec);
        return this;
    }

    public RateLimiter updateMetadata(Consumer<ObjectMeta> consumerMeta) {
        if (metadata == null) {
            metadata = new ObjectMeta();
        }
        consumerMeta.accept(metadata);
        return this;
    }

    public Deployment getDeployment(String namespace) {
        return client.apps().deployments().list().getItems()
                .stream()
                .filter(deployment -> deployment.getMetadata().getName().equals(getMetadata().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Non deployment"));
    }

    public Deployment getRedisDeployment(String namespace) {
        return client.apps().deployments().list().getItems()
                .stream()
                .filter(deployment -> deployment.getMetadata().getName().equals(generateRedisName(getMetadata().getName())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Non deployment"));
    }

    @Data
    @Builder
    @Accessors(chain = true)
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimiterSpec {
        private int size;
        private String logLevel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RateLimiter that = (RateLimiter) o;
        return Objects.equals(kind, that.kind) &&
                Objects.equals(apiVersion, that.apiVersion) &&
                Objects.equals(spec, that.spec) &&
                Objects.equals(metadata.getName(), that.metadata.getName()) &&
                Objects.equals(metadata.getNamespace(), that.metadata.getNamespace());
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, apiVersion, spec, metadata.getName(), metadata.getNamespace());
    }
}
