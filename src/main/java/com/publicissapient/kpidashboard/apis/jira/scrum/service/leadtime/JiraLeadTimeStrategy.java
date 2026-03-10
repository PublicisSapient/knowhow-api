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
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.model.LeadTimeChangeData;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.jira.ReleaseVersion;
import com.publicissapient.kpidashboard.common.util.DateUtil;

@Component
public class JiraLeadTimeStrategy implements LeadTimeCalculationStrategy {

	@Override
	public void calculateLeadTime(
			LeadTimeContext context, Map<String, List<LeadTimeChangeData>> leadTimeMapTimeWise) {
		if (CollectionUtils.isEmpty(context.getJiraIssueHistoryDataList())) {
			return;
		}

		context
				.getJiraIssueHistoryDataList()
				.forEach(
						jiraIssueHistoryData -> {
							AtomicReference<LocalDateTime> closedTicketDate = new AtomicReference<>();
							AtomicReference<LocalDateTime> releaseDate = new AtomicReference<>();

							jiraIssueHistoryData
									.getStatusUpdationLog()
									.forEach(
											jiraHistoryChangeLog -> {
												if (CollectionUtils.isNotEmpty(context.getDodStatus())
														&& context
																.getDodStatus()
																.contains(jiraHistoryChangeLog.getChangedTo())) {
													closedTicketDate.set(
															DateUtil.localDateTimeToUTC(jiraHistoryChangeLog.getUpdatedOn()));
												}
											});

							if (Objects.nonNull(context.getJiraIssueMap().get(jiraIssueHistoryData.getStoryID()))
									&& CollectionUtils.isNotEmpty(
											context
													.getJiraIssueMap()
													.get(jiraIssueHistoryData.getStoryID())
													.getReleaseVersions())) {
								List<ReleaseVersion> releaseVersionList =
										context
												.getJiraIssueMap()
												.get(jiraIssueHistoryData.getStoryID())
												.getReleaseVersions();
								releaseVersionList.forEach(
										releaseVersion ->
												releaseDate.set(
														DateUtil.convertJodaDateTimeToLocalDateTime(
																releaseVersion.getReleaseDate())));
							}

							if (closedTicketDate.get() != null && releaseDate.get() != null) {
								double leadTimeChangeInDays =
										KpiDataHelper.calWeekDaysExcludingWeekends(
												closedTicketDate.get(), releaseDate.get());
								String weekOrMonthName =
										getDateFormatted(context.getWeekOrMonth(), releaseDate.get());

								LeadTimeChangeData data =
										LeadTimeChangeData.builder()
												.storyID(jiraIssueHistoryData.getStoryID())
												.url(jiraIssueHistoryData.getUrl())
												.closedDate(DateUtil.tranformUTCLocalTimeToZFormat(closedTicketDate.get()))
												.releaseDate(
														DateUtil.dateTimeConverter(
																releaseDate.get().toString(),
																DateUtil.HOUR_MINUTE,
																DateUtil.DISPLAY_DATE_TIME_FORMAT))
												.leadTimeInDays(
														DateUtil.convertDoubleToDaysAndHoursString(leadTimeChangeInDays))
												.leadTime(leadTimeChangeInDays)
												.date(weekOrMonthName)
												.build();

								leadTimeMapTimeWise
										.computeIfAbsent(weekOrMonthName, k -> new ArrayList<>())
										.add(data);
							}
						});
	}
}
