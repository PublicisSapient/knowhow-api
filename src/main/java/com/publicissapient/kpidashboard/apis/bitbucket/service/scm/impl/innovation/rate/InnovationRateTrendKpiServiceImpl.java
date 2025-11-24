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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.innovation.rate;

import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy.AbstractKpiCalculationStrategy;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service implementation for calculating Innovation Rate KPI in trend mode.
 * Tracks innovation rate over multiple time periods to show trends.
 */
@Component
public class InnovationRateTrendKpiServiceImpl extends AbstractKpiCalculationStrategy<Map<String, List<DataCount>>> {

	private static final double INNOVATION_RATE_DIVISOR = 10.0;
	private static final double PERCENTAGE_MULTIPLIER = 100.0;
	private static final int DECIMAL_SCALE = 2;
	/**
	 * Calculates Innovation Rate KPI trend data across multiple time periods.
	 *
	 * @param kpiRequest         the KPI request containing filters and parameters
	 * @param mergeRequests      list of merge requests (unused in this implementation)
	 * @param commits            list of SCM commits
	 * @param scmTools           list of SCM tools to process
	 * @param validationDataList list to populate with validation data
	 * @param assignees          set of assignees for developer mapping
	 * @param projectName        name of the project
	 * @return map of KPI group to trend data
	 */
	@Override
	public Map<String, List<DataCount>> calculateKpi(KpiRequest kpiRequest, List<ScmMergeRequests> mergeRequests,
			List<ScmCommits> commits, List<Tool> scmTools, List<RepoToolValidationData> validationDataList,
			Set<Assignee> assignees, String projectName) {
		Map<String, List<DataCount>> kpiTrendDataByGroup = new LinkedHashMap<>();
		int dataPoints = kpiRequest.getXAxisDataPoints();
		LocalDateTime currentDate = DateUtil.getTodayTime();
		String duration = kpiRequest.getDuration();

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange periodRange = KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(periodRange, duration);
			List<ScmCommits> filteredCommitsList = filterCommitsByDateRange(commits, periodRange);

			scmTools.forEach(tool -> processToolData(tool, filteredCommitsList, assignees, kpiTrendDataByGroup,
					validationDataList, dateLabel, projectName));

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}
		return kpiTrendDataByGroup;
	}

	/**
	 * Filters commits within the specified date range.
	 *
	 * @param commits   list of commits to filter
	 * @param dateRange date range to filter by
	 * @return filtered list of commits
	 */
	private List<ScmCommits> filterCommitsByDateRange(List<ScmCommits> commits, CustomDateRange dateRange) {
		return commits.stream()
				.filter(commit -> DateUtil.isWithinDateTimeRange(
						DateUtil.convertMillisToLocalDateTime(commit.getCommitTimestamp()),
						dateRange.getStartDateTime(), dateRange.getEndDateTime()))
				.toList();
	}

	/**
	 * Processes data for a single SCM tool and calculates innovation rates.
	 *
	 * @param tool                  the SCM tool to process
	 * @param commitsList           list of commits for the period
	 * @param assignees             set of assignees
	 * @param kpiTrendDataByGroup   map to populate with trend data
	 * @param validationDataList    list to populate with validation data
	 * @param dateLabel             label for the time period
	 * @param projectName           name of the project
	 */
	private void processToolData(Tool tool, List<ScmCommits> commitsList, Set<Assignee> assignees,
			Map<String, List<DataCount>> kpiTrendDataByGroup, List<RepoToolValidationData> validationDataList,
			String dateLabel, String projectName) {
		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		List<ScmCommits> matchingCommits = DeveloperKpiHelper.filterCommitsForBranch(commitsList, tool);
		double innovationRate = getInnovationRate(matchingCommits);

		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;
		DeveloperKpiHelper.setDataCount(projectName, dateLabel, overallKpiGroup, innovationRate, new HashMap<>(),
				kpiTrendDataByGroup);

		Map<String, List<ScmCommits>> userWiseScmCommits = DeveloperKpiHelper.groupCommitsByUser(matchingCommits);
		validationDataList.addAll(prepareUserValidationData(userWiseScmCommits, assignees, tool, projectName, dateLabel,
				kpiTrendDataByGroup));
	}

	/**
	 * Calculates the innovation rate as the average percentage of added lines.
	 * Innovation rate = (added lines / total lines affected) * 100 / 10
	 *
	 * @param commits list of commits
	 * @return average innovation rate
	 */
	private double getInnovationRate(List<ScmCommits> commits) {
		return commits.stream()
				.mapToDouble(this::calculateCommitInnovationRate)
				.average()
				.orElse(0.0);
	}

	/**
	 * Calculates innovation rate for a single commit.
	 *
	 * @param commit the commit to calculate for
	 * @return innovation rate for the commit
	 */
	private double calculateCommitInnovationRate(ScmCommits commit) {
		long totalLinesAffected = commit.getTotalLinesAffected();
		if (totalLinesAffected == 0) {
			return 0.0;
		}
		double percentage = (commit.getAddedLines() * PERCENTAGE_MULTIPLIER) / totalLinesAffected;
		return BigDecimal.valueOf(percentage / INNOVATION_RATE_DIVISOR)
				.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP)
				.doubleValue();
	}

	/**
	 * Prepares validation data for each user/developer.
	 *
	 * @param userWiseCommits      map of user email to their commits
	 * @param assignees            set of assignees
	 * @param tool                 the SCM tool
	 * @param projectName          name of the project
	 * @param dateLabel            label for the time period
	 * @param kpiTrendDataByGroup  map to populate with user-level trend data
	 * @return list of validation data for all users
	 */
	private List<RepoToolValidationData> prepareUserValidationData(Map<String, List<ScmCommits>> userWiseCommits,
			Set<Assignee> assignees, Tool tool, String projectName, String dateLabel,
			Map<String, List<DataCount>> kpiTrendDataByGroup) {
		return userWiseCommits.entrySet().stream()
				.map(entry -> processUserData(entry.getKey(), entry.getValue(), assignees, tool, projectName, dateLabel,
						kpiTrendDataByGroup))
				.toList();
	}

	/**
	 * Processes data for a single user.
	 *
	 * @param userEmail            user's email
	 * @param userCommits          user's commits
	 * @param assignees            set of assignees
	 * @param tool                 the SCM tool
	 * @param projectName          name of the project
	 * @param dateLabel            label for the time period
	 * @param kpiTrendDataByGroup  map to populate with trend data
	 * @return validation data for the user
	 */
	private RepoToolValidationData processUserData(String userEmail, List<ScmCommits> userCommits,
			Set<Assignee> assignees, Tool tool, String projectName, String dateLabel,
			Map<String, List<DataCount>> kpiTrendDataByGroup) {
		String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);
		double innovationRate = getInnovationRate(userCommits);
		int addedLines = userCommits.stream().mapToInt(ScmCommits::getAddedLines).sum();
		int changedLines = userCommits.stream().mapToInt(ScmCommits::getChangedLines).sum();

		String userKpiGroup = DeveloperKpiHelper.getBranchSubFilter(tool, projectName) + "#" + developerName;
		DeveloperKpiHelper.setDataCount(projectName, dateLabel, userKpiGroup, innovationRate, new HashMap<>(),
				kpiTrendDataByGroup);

		return createValidationData(projectName, tool, developerName, dateLabel, innovationRate, addedLines,
				changedLines);
	}

	/**
	 * Creates validation data object for a developer.
	 *
	 * @param projectName    name of the project
	 * @param tool           the SCM tool
	 * @param developerName  name of the developer
	 * @param dateLabel      label for the time period
	 * @param innovationRate calculated innovation rate
	 * @param addedLines     total added lines
	 * @param changedLines   total changed lines
	 * @return populated validation data object
	 */
	private RepoToolValidationData createValidationData(String projectName, Tool tool, String developerName,
			String dateLabel, double innovationRate, long addedLines, long changedLines) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug());
		validationData.setDeveloperName(developerName);
		validationData.setDate(dateLabel);
		validationData.setInnovationRate(innovationRate);
		validationData.setAddedLines(addedLines);
		validationData.setChangedLines(changedLines);
		return validationData;
	}

	/**
	 * Returns the strategy type identifier.
	 *
	 * @return strategy type string
	 */
	@Override
	public String getStrategyType() {
		return "INNOVATION_RATE_TREND";
	}
}
