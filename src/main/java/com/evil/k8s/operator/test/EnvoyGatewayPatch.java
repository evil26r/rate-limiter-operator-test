package com.evil.k8s.operator.test;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@JsonDeserialize
@NoArgsConstructor
@AllArgsConstructor
public class EnvoyGatewayPatch {
    @JsonProperty("rate_limits")
    private List<GatewayRateLimit> rateLimits;

    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GatewayRateLimit{
        List<RateLimitAction> actions;

    }

    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitAction{
        @JsonProperty("request_headers")
        ActionRequestHeader requestHeaders;
    }

    @Data
    @Builder
    @JsonDeserialize
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionRequestHeader{
        @JsonProperty("descriptor_key")
        String descriptionKey;
        @JsonProperty("header_name")
        String headerName;
    }
}
