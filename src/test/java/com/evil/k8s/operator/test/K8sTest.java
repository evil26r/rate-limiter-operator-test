package com.evil.k8s.operator.test;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

public abstract class K8sTest {
    protected static KubernetesClient client = new DefaultKubernetesClient();
}
