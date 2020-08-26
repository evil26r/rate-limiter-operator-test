package com.evil.k8s.operator.test.models;



import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonDeserialize
public class EnvoyHttpFilterPatch {

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private EnvoyRateLimitConfig config;
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private String name;

    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvoyRateLimitConfig{
        private String domain;
        private int stage;
        private String request_type;
        private String timeout;
        private Boolean failure_mode_deny;
        private Boolean rate_limited_as_resource_exhausted;
        @JsonProperty("rate_limit_service")
        private RateLimitService rateLimitService;
    }



    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitService{
        @JsonProperty("grpc_service")
        private GrpcService grpcService;

    }

    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrpcService{
        @JsonProperty("envoy_grpc")
        private EnvoyGrpc envoyGrpc;
        private String timeout;
        @JsonProperty("initial_metadata")
        private List<HeaderValue> initialMetadata;
    }

    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvoyGrpc{
        private String cluster_name;
    }

    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeaderValue{
        private String key;
        private String value;
    }
}
