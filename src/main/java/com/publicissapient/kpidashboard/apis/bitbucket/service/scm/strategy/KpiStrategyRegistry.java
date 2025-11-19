package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy;

import com.publicissapient.kpidashboard.apis.enums.KPICode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class KpiStrategyRegistry {
    
    private final Map<String, KpiCalculationStrategy<?>> strategies;
    
    @Autowired
    public KpiStrategyRegistry(List<KpiCalculationStrategy<?>> strategyList) {
        this.strategies = strategyList.stream()
            .collect(Collectors.toMap(
                KpiCalculationStrategy::getStrategyType,
                Function.identity()
            ));
    }
    
    @SuppressWarnings("unchecked")
    public <T> KpiCalculationStrategy<T> getStrategy(KPICode kpiCode, String chartType) {
        String strategyKey = buildStrategyKey(kpiCode, chartType);
        KpiCalculationStrategy<?> strategy = strategies.get(strategyKey);
        
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy found for KPI: " + kpiCode + " with chart type: " + chartType);
        }
        
        return (KpiCalculationStrategy<T>) strategy;
    }
    
    private String buildStrategyKey(KPICode kpiCode, String chartType) {
        String trendType = "line".equalsIgnoreCase(chartType) ? "TREND" : "NON_TREND";
        return kpiCode.name() + "_" + trendType;
    }
}