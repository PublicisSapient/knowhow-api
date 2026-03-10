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
