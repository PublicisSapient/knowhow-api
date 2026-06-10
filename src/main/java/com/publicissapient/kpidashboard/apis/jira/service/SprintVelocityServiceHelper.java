package com.publicissapient.kpidashboard.apis.jira.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SprintVelocityServiceHelper {

	@Autowired private JiraIssueRepository jiraIssueRepository;

	@Autowired private ConfigHelperService configHelperService;

	public void getSprintIssuesForProject(
			List<JiraIssue> allJiraIssue,
			List<SprintDetails> sprintDetails,
			Map<Pair<String, String>, Set<JiraIssue>> currentSprintLeafVelocityMap) {
		if (CollectionUtils.isNotEmpty(sprintDetails)) {
			sprintDetails.stream()
					.filter(sd -> CollectionUtils.isNotEmpty(sd.getCompletedIssues()))
					.forEach(
							sd -> {
								Set<JiraIssue> filteredJiraIssuesListBasedOnTypeFromSprintDetails =
										KpiDataHelper.getFilteredJiraIssuesListBasedOnTypeFromSprintDetails(
												sd, sd.getCompletedIssues(), allJiraIssue);

								FieldMapping fieldMapping =
										configHelperService.getFieldMappingMap().get(sd.getBasicProjectConfigId());

								if (Objects.nonNull(fieldMapping)
										&& CollectionUtils.isNotEmpty(fieldMapping.getJiraIterationIssuetypeKPI39())) {
									aggregateSubtaskStoryPoints(
											filteredJiraIssuesListBasedOnTypeFromSprintDetails,
											sd.getBasicProjectConfigId().toString(),
											fieldMapping.getJiraIterationIssuetypeKPI39());
								}

								Pair<String, String> currentNodeIdentifier =
										Pair.of(sd.getBasicProjectConfigId().toString(), sd.getSprintID());
								log.debug(
										"Issue count for the sprint {} is {}",
										sd.getSprintID(),
										filteredJiraIssuesListBasedOnTypeFromSprintDetails.size());
								currentSprintLeafVelocityMap.put(
										currentNodeIdentifier, filteredJiraIssuesListBasedOnTypeFromSprintDetails);
							});
		}
	}

	private void aggregateSubtaskStoryPoints(
			Set<JiraIssue> issues, String configId, List<String> configuredIssueTypes) {
		if (CollectionUtils.isEmpty(issues) || CollectionUtils.isEmpty(configuredIssueTypes)) {
			return;
		}

		Set<String> parentIssuesWithNoPoints =
				issues.stream()
						.filter(issue -> issue.getStoryPoints() == null || issue.getStoryPoints() == 0.0)
						.map(JiraIssue::getNumber)
						.collect(Collectors.toSet());

		if (CollectionUtils.isEmpty(parentIssuesWithNoPoints)) {
			return;
		}

		Set<JiraIssue> subtasks =
				jiraIssueRepository.findByBasicProjectConfigIdAndParentStoryIdInAndOriginalTypeIn(
						configId, parentIssuesWithNoPoints, configuredIssueTypes);

		if (CollectionUtils.isEmpty(subtasks)) {
			log.debug(
					"No subtasks found with configured types {} for parent issues with zero story points",
					configuredIssueTypes);
			return;
		}

		Map<String, Double> parentToSubtaskStoryPoints =
				subtasks.stream()
						.filter(subtask -> CollectionUtils.isNotEmpty(subtask.getParentStoryId()))
						.flatMap(
								subtask ->
										subtask.getParentStoryId().stream()
												.map(
														parentId ->
																Pair.of(
																		parentId,
																		Optional.ofNullable(subtask.getStoryPoints()).orElse(0.0))))
						.collect(
								Collectors.groupingBy(Pair::getKey, Collectors.summingDouble(p -> p.getValue())));

		issues.forEach(
				issue -> {
					if (parentToSubtaskStoryPoints.containsKey(issue.getNumber())) {
						Double aggregatedPoints = parentToSubtaskStoryPoints.get(issue.getNumber());
						if (aggregatedPoints > 0) {
							issue.setStoryPoints(aggregatedPoints);
							log.debug(
									"Aggregated {} story points from subtasks to parent issue {}",
									aggregatedPoints,
									issue.getNumber());
						}
					}
				});
	}

	public double calculateSprintVelocityValue(
			Map<Pair<String, String>, Set<JiraIssue>> currentSprintLeafVelocityMap,
			Pair<String, String> currentNodeIdentifier,
			FieldMapping fieldMapping) {
		double sprintVelocityForCurrentLeaf = 0.0d;
		if (Objects.nonNull(currentSprintLeafVelocityMap.get(currentNodeIdentifier))) {
			log.debug(
					"Current Node identifier is present in currentSprintLeafVelocityMap map {} ",
					currentNodeIdentifier);
			Set<JiraIssue> issueDetailsSet = currentSprintLeafVelocityMap.get(currentNodeIdentifier);
			if (StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria())
					&& fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT)) {
				sprintVelocityForCurrentLeaf =
						issueDetailsSet.stream()
								.mapToDouble(ji -> Optional.ofNullable(ji.getStoryPoints()).orElse(0.0d))
								.sum();
			} else {
				double totalOriginalEstimate =
						issueDetailsSet.stream()
								.filter(
										jiraIssue ->
												Objects.nonNull(jiraIssue.getAggregateTimeOriginalEstimateMinutes()))
								.mapToDouble(JiraIssue::getAggregateTimeOriginalEstimateMinutes)
								.sum();
				double inHours = totalOriginalEstimate / 60;
				sprintVelocityForCurrentLeaf = inHours / fieldMapping.getStoryPointToHourMapping();
			}
		}
		log.debug(
				"Sprint velocity for the sprint {} is {}",
				currentNodeIdentifier.getValue(),
				sprintVelocityForCurrentLeaf);
		return sprintVelocityForCurrentLeaf;
	}
}
