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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.model.LeadTimeChangeData;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.application.Deployment;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;

@Component
public class RepoDeploymentLeadTimeStrategy implements LeadTimeCalculationStrategy {

	@Override
	public void calculateLeadTime(
			LeadTimeContext context, Map<String, List<LeadTimeChangeData>> leadTimeMapTimeWise) {
		if (CollectionUtils.isEmpty(context.getMergeRequestList())
				|| CollectionUtils.isEmpty(context.getDeploymentList())
				|| CollectionUtils.isEmpty(context.getScmCommitList())) {
			return;
		}

		Map<String, List<ScmMergeRequests>> mrBySha =
				context.getMergeRequestList().stream()
						.filter(mr -> CollectionUtils.isNotEmpty(mr.getCommitShas()))
						.flatMap(mr -> mr.getCommitShas().stream().map(sha -> Map.entry(sha, mr)))
						.collect(
								Collectors.groupingBy(
										Map.Entry::getKey,
										Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

		Map<String, List<Deployment>> deploymentBySha =
				context.getDeploymentList().stream()
						.filter(deployment -> null != deployment.getChangeSets())
						.flatMap(d -> d.getChangeSets().stream().map(sha -> Map.entry(sha, d)))
						.collect(
								Collectors.groupingBy(
										Map.Entry::getKey,
										Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

		context
				.getScmCommitList()
				.forEach(
						commit -> {
							String sha = commit.getSha();
							Long commitTime = commit.getCommitTimestamp();

							List<ScmMergeRequests> mrs = mrBySha.getOrDefault(sha, Collections.emptyList());
							List<Deployment> deployments =
									deploymentBySha.getOrDefault(sha, Collections.emptyList());

							if (mrs.isEmpty() || deployments.isEmpty()) {
								return;
							}

							ScmMergeRequests earliestMr =
									mrs.stream()
											.min(Comparator.comparing(ScmMergeRequests::getMergedAt))
											.orElse(null);
							Deployment earliestDeployment =
									deployments.stream()
											.min(Comparator.comparing(Deployment::getStartTime))
											.orElse(null);

							if (earliestMr == null
									|| earliestDeployment == null
									|| earliestMr.getMergedAt() == null
									|| earliestDeployment.getStartTime() == null) {
								return;
							}

							LocalDateTime commitDateTime = DateUtil.convertMillisToLocalDateTime(commitTime);
							LocalDateTime mergeDateTime = earliestMr.getMergedAt();
							DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DateUtil.TIME_FORMAT);
							LocalDateTime deployStartDateTime =
									LocalDateTime.parse(earliestDeployment.getStartTime(), formatter);

							double commitToMerge = KpiDataHelper.calWeekMinutes(commitDateTime, mergeDateTime);
							double mergeToDeployStart =
									KpiDataHelper.calWeekMinutes(mergeDateTime, deployStartDateTime);

							double totalDeployDuration =
									deployments.stream()
											.filter(d -> d.getStartTime() != null && d.getEndTime() != null)
											.mapToDouble(
													d -> {
														LocalDateTime startDateTime =
																LocalDateTime.parse(d.getStartTime(), formatter);
														LocalDateTime endDateTime =
																LocalDateTime.parse(d.getEndTime(), formatter);
														return KpiDataHelper.calWeekMinutes(startDateTime, endDateTime);
													})
											.sum();

							double totalLeadTime =
									(commitToMerge + mergeToDeployStart + totalDeployDuration) / 1440;

							LocalDateTime lastDeployEndTime =
									deployments.stream()
											.filter(d -> d.getEndTime() != null)
											.map(d -> LocalDateTime.parse(d.getEndTime(), formatter))
											.max(Comparator.naturalOrder())
											.orElse(deployStartDateTime);

							String weekOrMonthName =
									getDateFormatted(context.getWeekOrMonth(), lastDeployEndTime);

							LeadTimeChangeData data =
									LeadTimeChangeData.builder()
											.storyID(commit.getSha())
											.mergeID(earliestMr.getExternalId())
											.fromBranch(earliestMr.getFromBranch())
											.closedDate(DateUtil.tranformUTCLocalTimeToZFormat(commitDateTime))
											.releaseDate(
													DateUtil.dateTimeConverter(
															lastDeployEndTime.toString(),
															DateUtil.HOUR_MINUTE,
															DateUtil.DISPLAY_DATE_TIME_FORMAT))
											.leadTimeInDays(DateUtil.convertDoubleToDaysAndHoursString(totalLeadTime))
											.leadTime(totalLeadTime)
											.date(weekOrMonthName)
											.build();

							leadTimeMapTimeWise
									.computeIfAbsent(weekOrMonthName, k -> new ArrayList<>())
									.add(data);
						});
	}
}
