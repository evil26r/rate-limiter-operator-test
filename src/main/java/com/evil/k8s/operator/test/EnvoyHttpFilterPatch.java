package com.evil.k8s.operator.test;



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
    EnvoyRateLimitConfig config;
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    String name;

    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvoyRateLimitConfig{

        String domain;
        int stage;
        String request_type;
        String timeout;// -> google type Duration
        Boolean failure_mode_deny;
        Boolean rate_limited_as_resource_exhausted;
        @JsonProperty("rate_limit_service")
        RateLimitService rateLimitService;
    }



    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitService{
        @JsonProperty("grpc_service")
        GrpcService grpcService;

    }

    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrpcService{
        @JsonProperty("envoy_grpc")
        EnvoyGrpc envoyGrpc;
        // @JsonProperty("google_grpc")
        //  GoogleGrpc googleGrpc;
        String timeout; // -> google type Duration
        @JsonProperty("initial_metadata")
        List<HeaderValue> initialMetadata;
    }

    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvoyGrpc{
        String cluster_name;
    }

    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeaderValue{
        String key;
        String value;
    }
}
