package com.publicissapient.kpidashboard.apis.jira.scrum.service.leadtime;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.model.LeadTimeChangeData;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.ReleaseVersion;
import com.publicissapient.kpidashboard.common.util.DateUtil;

@Component
public class RepoLeadTimeStrategy implements LeadTimeCalculationStrategy {

	@Override
	public void calculateLeadTime(
			LeadTimeContext context, Map<String, List<LeadTimeChangeData>> leadTimeMapTimeWise) {
		if (CollectionUtils.isEmpty(context.getMergeRequestList())
				|| MapUtils.isEmpty(context.getJiraIssueMap())) {
			return;
		}

		context
				.getMergeRequestList()
				.forEach(
						mergeRequests -> {
							AtomicReference<LocalDateTime> closedTicketDate = new AtomicReference<>();
							AtomicReference<LocalDateTime> releaseDate = new AtomicReference<>();
							LocalDateTime closedDate =
									DateUtil.convertMillisToLocalDateTime(mergeRequests.getClosedDate());
							closedTicketDate.set(closedDate);
							String fromBranch = mergeRequests.getFromBranch();
							AtomicReference<JiraIssue> matchJiraIssue = new AtomicReference<>();

							context
									.getJiraIssueMap()
									.forEach(
											(key, jiraIssue) -> {
												String matchIssueKey = ".*" + key + ".*";
												if (fromBranch.matches(matchIssueKey)) {
													matchJiraIssue.set(jiraIssue);
												}
											});

							if (matchJiraIssue.get() != null) {
								if (CollectionUtils.isNotEmpty(matchJiraIssue.get().getReleaseVersions())) {
									ReleaseVersion firstRelease = matchJiraIssue.get().getReleaseVersions().get(0);
									if (firstRelease.getReleaseDate() != null) {
										releaseDate.set(
												DateUtil.convertJodaDateTimeToLocalDateTime(firstRelease.getReleaseDate()));
									}
								}

								if (closedTicketDate.get() != null && releaseDate.get() != null) {
									double leadTimeChange =
											KpiDataHelper.calWeekDaysExcludingWeekends(
													closedTicketDate.get(), releaseDate.get());
									String weekOrMonthName =
											getDateFormatted(context.getWeekOrMonth(), releaseDate.get());

									LeadTimeChangeData data =
											LeadTimeChangeData.builder()
													.storyID(matchJiraIssue.get().getNumber())
													.url(matchJiraIssue.get().getUrl())
													.mergeID(mergeRequests.getRevisionNumber())
													.fromBranch(mergeRequests.getFromBranch())
													.closedDate(
															DateUtil.tranformUTCLocalTimeToZFormat(closedTicketDate.get()))
													.releaseDate(
															DateUtil.dateTimeConverter(
																	releaseDate.get().toString(),
																	DateUtil.HOUR_MINUTE,
																	DateUtil.DISPLAY_DATE_TIME_FORMAT))
													.leadTimeInDays(
															DateUtil.convertDoubleToDaysAndHoursString(leadTimeChange))
													.leadTime(leadTimeChange)
													.date(weekOrMonthName)
													.build();

									leadTimeMapTimeWise
											.computeIfAbsent(weekOrMonthName, k -> new ArrayList<>())
											.add(data);
								}
							}
						});
	}
}
