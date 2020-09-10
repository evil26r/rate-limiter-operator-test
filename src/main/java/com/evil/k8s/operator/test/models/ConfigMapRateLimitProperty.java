package com.evil.k8s.operator.test.models;

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
public class ConfigMapRateLimitProperty {
    private String domain;
    private List<RateLimiterConfig.RateLimiterConfigDescriptors> descriptors;
}
