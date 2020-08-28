package com.evil.k8s.operator.test;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class K8sRateLimitAbstractTest extends K8sTest {

    @BeforeAll
    @SneakyThrows
    public static void createClient() {
        client = new DefaultKubernetesClient();
    }

    @AfterAll
    static void afterAll() {
        client.close();
    }
}
