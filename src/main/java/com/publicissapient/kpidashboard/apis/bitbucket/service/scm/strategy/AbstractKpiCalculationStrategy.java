package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy;

import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;

import java.time.LocalDateTime;

public abstract class AbstractKpiCalculationStrategy<T> implements KpiCalculationStrategy<T> {

    protected CustomDateRange getCustomDateRange(LocalDateTime currentDate, String duration, int dataPoints) {
        CustomDateRange periodRange = new CustomDateRange();
        if (duration.equalsIgnoreCase(CommonConstant.DAY)) {
            periodRange.setEndDateTime(currentDate.minusDays(1));
            periodRange.setStartDateTime(currentDate.minusDays(dataPoints - 1L));
        } else {
            periodRange.setEndDateTime(currentDate);
            periodRange.setStartDateTime(currentDate.minusWeeks(dataPoints - 1L));
        }
        return periodRange;
    }
}