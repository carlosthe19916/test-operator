package org.trustify.operator.cdrs.v2alpha1;

import java.util.ArrayList;
import java.util.List;

public class TrustifyStatus {
    private List<TrustifyStatusCondition> conditions;

    public TrustifyStatus() {
        conditions = new ArrayList<>();
    }

    public List<TrustifyStatusCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<TrustifyStatusCondition> conditions) {
        this.conditions = conditions;
    }

}
