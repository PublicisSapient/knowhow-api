package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy;

import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.mttm.MeanTimeToMergeNonTrendKpiServiceImpl;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.mttm.MeanTimeToMergeTrendKpiServiceImpl;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.pickup.time.PickupTimeNonTrendKpiServiceImpl;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.pickup.time.PickupTimeTrendKpiServiceImpl;
import com.publicissapient.kpidashboard.apis.enums.KPICode;

public class KpiStrategyFactory {
    
    public static KpiCalculationStrategy getStrategy(KPICode kpiCode, String chartType) {
        return switch (kpiCode) {
            case REPO_TOOL_MEAN_TIME_TO_MERGE -> getMeanTimeToMergeStrategy(chartType);
            case PICKUP_TIME -> getPickupTimeStrategy(chartType);
            default -> throw new IllegalArgumentException("No strategy found for KPI: " + kpiCode);
        };
    }
    
    private static KpiCalculationStrategy getMeanTimeToMergeStrategy(String chartType) {
        return "line".equalsIgnoreCase(chartType) 
            ? new MeanTimeToMergeTrendKpiServiceImpl() 
            : new MeanTimeToMergeNonTrendKpiServiceImpl();
    }

    private static KpiCalculationStrategy getPickupTimeStrategy(String chartType) {
        return "line".equalsIgnoreCase(chartType)
                ? new PickupTimeTrendKpiServiceImpl()
                : new PickupTimeNonTrendKpiServiceImpl();
    }
}
