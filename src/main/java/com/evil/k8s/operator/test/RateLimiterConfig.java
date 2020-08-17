package com.evil.k8s.operator.test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import lombok.*;
import me.snowdrop.istio.api.networking.v1alpha3.WorkloadSelector;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonDeserialize
public class RateLimiterConfig implements HasMetadata, Namespaced {

    private String kind;

    private String apiVersion;

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private WorkloadSelector workloadSelector;

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private ObjectMeta metadata;

    private RateLimiterConfigSpec spec;

    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RateLimiterConfigSpec {
        private String applyTo;
        private String host;
        private int port;
        private String rateLimiter;
        private RateLimitProperty rateLimitProperty;
    }

    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RateLimitProperty {
        private List<RateLimiterConfigDescriptors> descriptors;
    }

    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RateLimiterConfigDescriptors {
        private String key;
        private String value;
        @JsonProperty("rate_limit")
        private RateLimit rateLimit;
        private RateLimiterConfigDescriptors rateLimiterConfigDescriptors;
    }

    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RateLimit {
        @JsonProperty("requests_per_unit")
        private int requestsPerUnit;
        private String unit;
    }
}
