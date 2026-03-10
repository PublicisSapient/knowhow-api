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

package com.publicissapient.kpidashboard.apis.jira.scrum.service.leadtime;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Map;

import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.model.LeadTimeChangeData;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.util.DateUtil;

public interface LeadTimeCalculationStrategy {
	void calculateLeadTime(
			LeadTimeContext context, Map<String, List<LeadTimeChangeData>> leadTimeMapTimeWise);

	default String getDateFormatted(String weekOrMonth, LocalDateTime currentDate) {
		if (weekOrMonth.equalsIgnoreCase(CommonConstant.WEEK)) {
			return DateUtil.getWeekRangeUsingDateTime(currentDate);
		} else {
			return currentDate.getYear()
					+ Constant.DASH
					+ Month.of(currentDate.toLocalDate().atTime(23, 59, 59).getMonthValue());
		}
	}
}
