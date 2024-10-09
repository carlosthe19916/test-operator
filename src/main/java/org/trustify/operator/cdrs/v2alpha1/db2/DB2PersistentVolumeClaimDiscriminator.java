package org.trustify.operator.cdrs.v2alpha1.db2;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.trustify.operator.cdrs.v2alpha1.Trustify;

import java.util.Optional;

public class DB2PersistentVolumeClaimDiscriminator implements ResourceDiscriminator<PersistentVolumeClaim, Trustify> {
    @Override
    public Optional<PersistentVolumeClaim> distinguish(Class<PersistentVolumeClaim> resource, Trustify cr, Context<Trustify> context) {
        String persistentVolumeClaimName = DB2PersistentVolumeClaim.getPersistentVolumeClaimName(cr);
        ResourceID resourceID = new ResourceID(persistentVolumeClaimName, cr.getMetadata().getNamespace());
        var informerEventSource = (InformerEventSource<PersistentVolumeClaim, Trustify>) context.eventSourceRetriever().getResourceEventSourceFor(PersistentVolumeClaim.class);
        return informerEventSource.get(resourceID);
    }
}
