package org.trustify.operator.cdrs.v2alpha1.db1;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.cdrs.v2alpha1.Trustify;

@ApplicationScoped
public class DB1DeploymentActivationCondition implements Condition<Deployment, Trustify> {

    @Override
    public boolean isMet(DependentResource<Deployment, Trustify> resource, Trustify cr, Context<Trustify> context) {
        return true;
    }

}
