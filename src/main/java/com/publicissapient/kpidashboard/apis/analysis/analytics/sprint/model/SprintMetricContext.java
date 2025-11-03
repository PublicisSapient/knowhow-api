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

package com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.types.ObjectId;

import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;

import lombok.Builder;
import lombok.Data;

/**
 * Context object holding all data required for sprint metric calculations
 */
@Data
@Builder
public class SprintMetricContext {

	/** Project Basic Config ID */
	private ObjectId basicProjectConfigId;

	/** Project display name */
	private String projectName;

	/** Sprint details for the requested sprints (sorted by date, latest first) */
	private List<SprintDetails> sprintDetailsList;

	/** Field mappings for this project */
	private FieldMapping fieldMapping;

	/** Number of sprints requested */
	private int numberOfSprints;

	/** Map of Jira issues by issue number */
	private Map<String, JiraIssue> jiraIssueMap;

	/** Map of Jira issue custom history by story ID */
	private Map<String, JiraIssueCustomHistory> historyMap;

	/**
	 * Get JiraIssue by issue number
	 *
	 * @param issueNumber
	 *            Issue number (e.g., "DTS-12345")
	 * @return Optional containing JiraIssue if found
	 */
	public Optional<JiraIssue> getJiraIssueByNumber(String issueNumber) {
		return Optional.ofNullable(jiraIssueMap.get(issueNumber));
	}

	/**
	 * Get JiraIssueCustomHistory by story ID
	 *
	 * @param storyId
	 *            Story ID
	 * @return Optional containing JiraIssueCustomHistory if found
	 */
	public Optional<JiraIssueCustomHistory> getHistoryByStoryId(String storyId) {
		return Optional.ofNullable(historyMap.get(storyId));
	}
}
