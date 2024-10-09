package org.trustify.operator.cdrs.v2alpha1.db1;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.utils.CRDUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@KubernetesDependent(labelSelector = DB1Deployment.LABEL_SELECTOR, resourceDiscriminator = DB1DeploymentDiscriminator.class)
@ApplicationScoped
public class DB1Deployment extends CRUDKubernetesDependentResource<Deployment, Trustify>
        implements Matcher<Deployment, Trustify>, Condition<Deployment, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=db";

    public DB1Deployment() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(Trustify cr, Context<Trustify> context) {
        return newDeployment(cr, context);
    }

    @Override
    public Result<Deployment> match(Deployment actual, Trustify cr, Context<Trustify> context) {
        final var container = actual.getSpec()
                .getTemplate().getSpec().getContainers()
                .stream()
                .findFirst();

        return Result.nonComputed(container
                .map(c -> c.getImage() != null)
                .orElse(false)
        );
    }

    @Override
    public boolean isMet(DependentResource<Deployment, Trustify> dependentResource, Trustify primary, Context<Trustify> context) {
        return context.getSecondaryResource(Deployment.class, new DB1DeploymentDiscriminator())
                .map(deployment -> {
                    final var status = deployment.getStatus();
                    if (status != null) {
                        final var readyReplicas = status.getReadyReplicas();
                        return readyReplicas != null && readyReplicas >= 1;
                    }
                    return false;
                })
                .orElse(false);
    }

    @SuppressWarnings("unchecked")
    private Deployment newDeployment(Trustify cr, Context<Trustify> context) {
        final var contextLabels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        return new DeploymentBuilder()
                .withNewMetadata()
                .withName(getDeploymentName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(contextLabels)
                .addToLabels("component", "db")
                .addToLabels(Map.of(
                        "app.openshift.io/runtime", "postgresql"
                ))
                .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withSpec(getDeploymentSpec(cr, context))
                .build();
    }

    @SuppressWarnings("unchecked")
    private DeploymentSpec getDeploymentSpec(Trustify cr, Context<Trustify> context) {
        final var contextLabels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        Map<String, String> selectorLabels = Constants.DB_SELECTOR_LABELS;
        String image = "quay.io/sclorg/postgresql-15-c9s:latest";
        String imagePullPolicy = "IfNotPresent";

        return new DeploymentSpecBuilder()
                .withStrategy(new DeploymentStrategyBuilder()
                        .withType("Recreate")
                        .build()
                )
                .withReplicas(1)
                .withSelector(new LabelSelectorBuilder()
                        .withMatchLabels(selectorLabels)
                        .build()
                )
                .withTemplate(new PodTemplateSpecBuilder()
                        .withNewMetadata()
                        .withLabels(Stream
                                .concat(contextLabels.entrySet().stream(), selectorLabels.entrySet().stream())
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                        )
                        .endMetadata()
                        .withSpec(new PodSpecBuilder()
                                .withRestartPolicy("Always")
                                .withTerminationGracePeriodSeconds(60L)
                                .withContainers(new ContainerBuilder()
                                        .withName(Constants.TRUSTI_DB_NAME)
                                        .withImage(image)
                                        .withImagePullPolicy(imagePullPolicy)
                                        .withEnv(getEnvVars(cr))
                                        .withPorts(new ContainerPortBuilder()
                                                .withName("tcp")
                                                .withProtocol(Constants.SERVICE_PROTOCOL)
                                                .withContainerPort(getDatabasePort(cr))
                                                .build()
                                        )
                                        .withLivenessProbe(new ProbeBuilder()
                                                .withExec(new ExecActionBuilder()
                                                        .withCommand("/bin/sh", "-c", "psql -U $POSTGRESQL_USER -d $POSTGRESQL_DATABASE -c 'SELECT 1'")
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(10)
                                                .withTimeoutSeconds(10)
                                                .withPeriodSeconds(10)
                                                .withSuccessThreshold(1)
                                                .withFailureThreshold(3)
                                                .build()
                                        )
                                        .withReadinessProbe(new ProbeBuilder()
                                                .withExec(new ExecActionBuilder()
                                                        .withCommand("/bin/sh", "-c", "psql -U $POSTGRESQL_USER -d $POSTGRESQL_DATABASE -c 'SELECT 1'")
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(5)
                                                .withTimeoutSeconds(1)
                                                .withPeriodSeconds(10)
                                                .withSuccessThreshold(1)
                                                .withFailureThreshold(3)
                                                .build()
                                        )
                                        .withVolumeMounts(new VolumeMountBuilder()
                                                .withName("db-pvol")
                                                .withMountPath("/var/lib/pgsql/data")
                                                .build()
                                        )
                                        .build()
                                )
                                .withVolumes(new VolumeBuilder()
                                        .withName("db-pvol")
                                        .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                                                .withClaimName(DB1PersistentVolumeClaim.getPersistentVolumeClaimName(cr))
                                                .build()
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build();
    }

    private List<EnvVar> getEnvVars(Trustify cr) {
        return Arrays.asList(
                new EnvVarBuilder()
                        .withName("POSTGRESQL_USER")
                        .withValue("username")
                        .build(),
                new EnvVarBuilder()
                        .withName("POSTGRESQL_PASSWORD")
                        .withValue("password")
                        .build(),
                new EnvVarBuilder()
                        .withName("POSTGRESQL_DATABASE")
                        .withValue("database")
                        .build()
        );
    }

    public static String getDeploymentName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.DB_DEPLOYMENT_SUFFIX;
    }

    public static Integer getDatabasePort(Trustify cr) {
        return Constants.DB_PORT;
    }
}
