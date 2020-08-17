package com.evil.k8s.operator.test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.snowdrop.istio.api.networking.v1alpha3.WorkloadSelector;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonDeserialize
public class RateLimiter implements HasMetadata, Namespaced {

    @Builder.Default
    private String kind = "RateLimiter";

    @Builder.Default
    private String apiVersion = "operators.example.com/v1";

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private RateLimiterSpec spec;

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private WorkloadSelector workloadSelector;

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private ObjectMeta metadata;

    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimiterSpec {
        int port;
        String logLevel;
    }
}
