/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

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