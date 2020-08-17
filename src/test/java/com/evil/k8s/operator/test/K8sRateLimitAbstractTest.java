package com.evil.k8s.operator.test;

import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class K8sRateLimitAbstractTest extends K8sTest {


    protected static CustomResourceDefinitionContext rateLimitCrdContext;
    protected static CustomResourceDefinitionContext rateLimitConfigCrdContext;
    protected static CustomResourceDefinitionContext envoyFilterContext;

    @BeforeAll
    @SneakyThrows
    public static void createClient() {
        rateLimitCrdContext = new CustomResourceDefinitionContext.Builder()
                .withGroup("operators.example.com")
                .withScope("Namespaced")
                .withVersion("v1")
                .withPlural("ratelimiters")
                .build();

        rateLimitConfigCrdContext = new CustomResourceDefinitionContext.Builder()
                .withGroup("operators.example.com")
                .withScope("Namespaced")
                .withVersion("v1")
                .withPlural("ratelimiterconfigs")
                .build();

        envoyFilterContext = new CustomResourceDefinitionContext.Builder()
                .withGroup("networking.istio.io")
                .withScope("Namespaced")
                .withVersion("v1alpha3")
                .withPlural("envoyfilters")
                .build();


//        client.customResource(rateLimitCrdContext).create("test-project", TestApplicationTests.class.getResourceAsStream("/rate-limit.yaml"));
    }

    @AfterAll
    static void afterAll() {
        client.close();
    }
}
