package org.trustify.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import org.trustify.operator.Constants;

@Group(Constants.CRDS_GROUP)
@Version(Constants.CRDS_VERSION)
public class Trustify extends CustomResource<TrustifySpec, TrustifyStatus> implements Namespaced {

    @Override
    protected TrustifySpec initSpec() {
        return new TrustifySpec();
    }

    @Override
    protected TrustifyStatus initStatus() {
        return new TrustifyStatus();
    }

}

