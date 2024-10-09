package org.trustify.operator.controllers.setup;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.jboss.logging.Logger;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

public class K3sResource implements QuarkusTestResourceLifecycleManager {
    private static final Logger logger = Logger.getLogger(K3sConfigProducer.class);

    static K3sContainer k3sContainer;

    @Override
    public Map<String, String> start() {
        Map<String, String> result = new HashMap<>();
        result.put("quarkus.kubernetes.namespace", "trustify-operator");

        k3sContainer = new K3sContainer(DockerImageName.parse("rancher/k3s:latest"));
        k3sContainer.start();

        String kubeConfigYaml = k3sContainer.getKubeConfigYaml();

        result.put("kubeConfigYaml", kubeConfigYaml);
        return result;
    }

    @Override
    public void stop() {
        if (k3sContainer != null) {
            k3sContainer.stop();
        }
    }
}
