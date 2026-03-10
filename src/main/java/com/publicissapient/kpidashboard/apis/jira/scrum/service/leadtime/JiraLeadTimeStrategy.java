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
