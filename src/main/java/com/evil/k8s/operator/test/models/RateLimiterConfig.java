package com.evil.k8s.operator.test.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.snowdrop.istio.api.networking.v1alpha3.WorkloadSelector;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonDeserialize
public class RateLimiterConfig implements HasMetadata, Namespaced {

    @Builder.Default
    private String kind = "RateLimiterConfig";

    @Builder.Default
    private String apiVersion = "operators.example.com/v1";

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private ObjectMeta metadata;

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private RateLimiterConfigSpec spec;

    public RateLimiterConfig updateSpec(Consumer<RateLimiterConfigSpec> consumerSpec) {
        if (spec == null) {
            spec = new RateLimiterConfigSpec();
        }
        consumerSpec.accept(spec);
        return this;
    }

    @Data
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimiterConfigSpec {
        private Context applyTo;
        private String host;
        private int port;
        private String rateLimiter;
        private boolean failureModeDeny;
        private RateLimitProperty rateLimitProperty;
        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        private WorkloadSelector workloadSelector;
    }

    @Data
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitProperty {
        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        private List<RateLimiterConfigDescriptors> descriptors;
        private String domain;
    }

    @Data
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimiterConfigDescriptors {
        private String key;
        private String value;
        @JsonProperty("rate_limit")
        private RateLimit rateLimit;
        private RateLimiterConfigDescriptors rateLimiterConfigDescriptors;
    }

    @Data
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimit {
        @JsonProperty("requests_per_unit")
        private int requestsPerUnit;
        private String unit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RateLimiterConfig that = (RateLimiterConfig) o;
        return Objects.equals(kind, that.kind) &&
                Objects.equals(apiVersion, that.apiVersion) &&
                Objects.equals(metadata.getName(), that.metadata.getName()) &&
                Objects.equals(metadata.getNamespace(), that.metadata.getNamespace()) &&
                Objects.equals(spec, that.spec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, apiVersion, metadata, spec);
    }

    public enum Context {
        GATEWAY,
        SIDECAR_INBOUND,
        SIDECAR_OUTBOUND,
        ANY
    }
}
