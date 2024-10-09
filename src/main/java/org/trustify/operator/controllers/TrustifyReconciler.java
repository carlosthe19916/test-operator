package org.trustify.operator.controllers;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.jboss.logging.Logger;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.db1.DB1Deployment;
import org.trustify.operator.cdrs.v2alpha1.db1.DB1PersistentVolumeClaim;
import org.trustify.operator.cdrs.v2alpha1.db1.DB1PersistentVolumeClaimActivationCondition;
import org.trustify.operator.cdrs.v2alpha1.db2.DB2PersistentVolumeClaim;
import org.trustify.operator.cdrs.v2alpha1.db2.DB2PersistentVolumeClaimActivationCondition;

import java.time.Duration;
import java.util.Map;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@ControllerConfiguration(
        namespaces = WATCH_CURRENT_NAMESPACE,
        name = "trustify",
        dependents = {
                @Dependent(
                        name = "oidc-db-pvc",
                        type = DB2PersistentVolumeClaim.class,
                        activationCondition = DB2PersistentVolumeClaimActivationCondition.class,
                        useEventSourceWithName = TrustifyReconciler.PVC_EVENT_SOURCE
                ),

                @Dependent(
                        name = "db-pvc",
                        type = DB1PersistentVolumeClaim.class,
                        activationCondition = DB1PersistentVolumeClaimActivationCondition.class,
                        useEventSourceWithName = TrustifyReconciler.PVC_EVENT_SOURCE
                ),
                @Dependent(
                        name = "db-deployment",
                        type = DB1Deployment.class,
                        activationCondition = DB1PersistentVolumeClaimActivationCondition.class
                ),
        }
)
public class TrustifyReconciler implements Reconciler<Trustify>, ContextInitializer<Trustify>, EventSourceInitializer<Trustify> {

    private static final Logger logger = Logger.getLogger(TrustifyReconciler.class);

    public static final String PVC_EVENT_SOURCE = "pvcSource";

    @Override
    public void initContext(Trustify cr, Context<Trustify> context) {
        final var labels = Map.of(
                "app.kubernetes.io/managed-by", "trustify-operator",
                "app.kubernetes.io/name", cr.getMetadata().getName(),
                "app.kubernetes.io/part-of", cr.getMetadata().getName(),
                "trustify-operator/cluster", org.trustify.operator.Constants.TRUSTI_NAME
        );
        context.managedDependentResourceContext().put(org.trustify.operator.Constants.CONTEXT_LABELS_KEY, labels);
    }

    @Override
    public UpdateControl<Trustify> reconcile(Trustify cr, Context context) {
        return context.managedDependentResourceContext()
                .getWorkflowReconcileResult()
                .map(wrs -> {
                    if (wrs.allDependentResourcesReady()) {
                        return UpdateControl.<Trustify>noUpdate();
                    } else {
                        final var duration = Duration.ofSeconds(5);
                        return UpdateControl.<Trustify>noUpdate().rescheduleAfter(duration);
                    }
                })
                .orElseThrow();
    }

    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<Trustify> context) {
        var pvcInformerConfiguration = InformerConfiguration.from(PersistentVolumeClaim.class, context).build();

        var pvcInformerEventSource = new InformerEventSource<>(pvcInformerConfiguration, context);

        return Map.of(
                PVC_EVENT_SOURCE, pvcInformerEventSource
        );
    }
}
