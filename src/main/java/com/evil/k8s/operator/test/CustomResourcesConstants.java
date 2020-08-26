package com.evil.k8s.operator.test;

import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;

public class CustomResourcesConstants {
    public static final CustomResourceDefinitionContext rateLimitCrdContext = new CustomResourceDefinitionContext.Builder()
            .withGroup("operators.example.com")
            .withScope("Namespaced")
            .withVersion("v1")
            .withPlural("ratelimiters")
            .build();

    public static CustomResourceDefinitionContext rateLimitConfigCrdContext = new CustomResourceDefinitionContext.Builder()
            .withGroup("operators.example.com")
            .withScope("Namespaced")
            .withVersion("v1")
            .withPlural("ratelimiterconfigs")
            .build();

    public static CustomResourceDefinitionContext envoyFilterContext = new CustomResourceDefinitionContext.Builder()
            .withGroup("networking.istio.io")
            .withScope("Namespaced")
            .withVersion("v1alpha3")
            .withPlural("envoyfilters")
            .build();
}
