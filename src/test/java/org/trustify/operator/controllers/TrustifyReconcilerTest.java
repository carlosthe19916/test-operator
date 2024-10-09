package org.trustify.operator.controllers;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.db1.DB1Deployment;
import org.trustify.operator.controllers.setup.K3sResource;

import java.util.concurrent.TimeUnit;

@QuarkusTestResource(K3sResource.class)
@QuarkusTest
public class TrustifyReconcilerTest {

    public static final String TEST_APP = "myapp";

    @Inject
    KubernetesClient client;

    @Inject
    Operator operator;

    @BeforeEach
    public void startOperator() {
        operator.start();
    }

    @AfterEach
    public void stopOperator() {
        operator.stop();
    }

    @Test
    @Order(1)
    public void reconcileShouldWork() throws InterruptedException {
        // Requirements
        client.resource(new NamespaceBuilder()
                        .withNewMetadata()
                        .withName(client.getNamespace())
                        .endMetadata()
                        .build()
                )
                .create();

        client.resource(new ServiceAccountBuilder()
                        .withNewMetadata()
                        .withName(Constants.TRUSTI_NAME)
                        .endMetadata()
                        .build()
                )
                .inNamespace(client.getNamespace())
                .create();

        //
        final var app = new Trustify();
        final var metadata = new ObjectMetaBuilder()
                .withName(TEST_APP)
                .withNamespace(client.getNamespace())
                .build();
        app.setMetadata(metadata);

        // Delete prev instance if exists already
        if (client.resource(app).inNamespace(metadata.getNamespace()).get() != null) {
            client.resource(app).inNamespace(metadata.getNamespace()).delete();
            Thread.sleep(10_000);
        }

        // Instantiate Trusti
        client.resource(app).inNamespace(metadata.getNamespace()).serverSideApply();

        // Verify resources
        Awaitility.await()
                .ignoreException(NullPointerException.class)
                .atMost(2, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    // Database
                    final var dbDeployment = client.apps()
                            .deployments()
                            .inNamespace(metadata.getNamespace())
                            .withName(DB1Deployment.getDeploymentName(app))
                            .get();
                    final var dbContainer = dbDeployment.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .stream()
                            .findFirst();
                    MatcherAssert.assertThat("DB container not found", dbContainer.isPresent(), Matchers.is(true));
                    MatcherAssert.assertThat("DB container image not valid", dbContainer.get().getImage(), Matchers.is("quay.io/sclorg/postgresql-15-c9s:latest"));

                    Assertions.assertEquals(1, dbDeployment.getStatus().getReadyReplicas(), "Expected DB deployment number of replicas doesn't match");
                });
    }
}