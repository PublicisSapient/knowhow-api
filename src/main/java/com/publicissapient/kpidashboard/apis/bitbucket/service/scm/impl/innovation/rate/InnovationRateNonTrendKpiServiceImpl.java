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
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.IterationKpiData;
import com.publicissapient.kpidashboard.apis.model.IterationKpiValue;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service implementation for calculating Innovation Rate KPI in non-trend mode.
 * Innovation Rate measures the percentage of added lines relative to total lines changed.
 */
@Component
public class InnovationRateNonTrendKpiServiceImpl extends AbstractKpiCalculationStrategy<List<IterationKpiValue>> {

	private static final double INNOVATION_RATE_DIVISOR = 10.0;
	private static final double PERCENTAGE_MULTIPLIER = 100.0;
	private static final int DECIMAL_SCALE = 2;
    private static final String LABEL_INFO = "pi-lightbulb";

	/**
	 * Calculates Innovation Rate KPI for all SCM tools.
	 *
	 * @param kpiRequest         the KPI request containing filters and parameters
	 * @param mergeRequests      list of merge requests (unused in this implementation)
	 * @param commits            list of SCM commits
	 * @param scmTools           list of SCM tools to process
	 * @param validationDataList list to populate with validation data
	 * @param assignees          set of assignees for developer mapping
	 * @param projectName        name of the project
	 * @return list of iteration KPI values
	 */
	@Override
	public List<IterationKpiValue> calculateKpi(KpiRequest kpiRequest, List<ScmMergeRequests> mergeRequests,
			List<ScmCommits> commits, List<Tool> scmTools, List<RepoToolValidationData> validationDataList,
			Set<Assignee> assignees, String projectName) {
		List<IterationKpiValue> iterationKpiValueList = new ArrayList<>();

		scmTools.forEach(tool -> processToolData(tool, commits, assignees, iterationKpiValueList, validationDataList,
				kpiRequest, projectName));

		return iterationKpiValueList;
	}

	/**
	 * Processes data for a single SCM tool and calculates innovation rates.
	 *
	 * @param tool                   the SCM tool to process
	 * @param commits                list of all commits
	 * @param assignees              set of assignees
	 * @param iterationKpiValueList  list to populate with KPI values
	 * @param validationDataList     list to populate with validation data
	 * @param kpiRequest             the KPI request
	 * @param projectName            name of the project
	 */
	private void processToolData(Tool tool, List<ScmCommits> commits, Set<Assignee> assignees,
			List<IterationKpiValue> iterationKpiValueList, List<RepoToolValidationData> validationDataList,
			KpiRequest kpiRequest, String projectName) {
		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		int dataPoints = kpiRequest.getXAxisDataPoints();
		LocalDateTime currentDate = DateUtil.getTodayTime();
		String duration = kpiRequest.getDuration();
		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		List<ScmCommits> commitsForBranch = DeveloperKpiHelper.filterCommitsForBranch(commits, tool);

		CustomDateRange currentPeriodRange = getCustomDateRange(currentDate, duration, dataPoints);
		List<ScmCommits> currentCommitsList = filterCommitsByDateRange(commitsForBranch, currentPeriodRange);
		double currentInnovationRate = getInnovationRate(currentCommitsList);

		CustomDateRange previousPeriodRange = getCustomDateRange(currentPeriodRange.getStartDateTime(), duration, dataPoints);
		List<ScmCommits> previousCommitsList = filterCommitsByDateRange(commitsForBranch, previousPeriodRange);
		double previousInnovationRate = getInnovationRate(previousCommitsList);
		double deviationRate = calculateDeviationRate(currentInnovationRate, previousInnovationRate);

		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;
		iterationKpiValueList.add(new IterationKpiValue(branchName, Constant.AGGREGATED_VALUE, List.of(
				new IterationKpiData(overallKpiGroup, currentInnovationRate, deviationRate, LABEL_INFO, "%", "%", null))));

		Map<String, List<ScmCommits>> userWiseCommits = DeveloperKpiHelper.groupCommitsByUser(commitsForBranch);
		validationDataList.addAll(prepareUserValidationData(currentPeriodRange, previousPeriodRange, userWiseCommits,
				assignees, tool, projectName, iterationKpiValueList));
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
	 * Calculates the innovation rate as the average percentage of added lines.
	 * Innovation rate = (added lines / total lines affected) * 100 / 10
	 *
	 * @param commits list of commits
	 * @return average innovation rate
	 */
	private double getInnovationRate(List<ScmCommits> commits) {
		double innovationRate = commits.stream()
				.mapToDouble(this::calculateCommitInnovationRate)
				.average()
				.orElse(0.0);
        return BigDecimal.valueOf(innovationRate).setScale(DECIMAL_SCALE, RoundingMode.HALF_UP).doubleValue();
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
		return percentage / INNOVATION_RATE_DIVISOR;
	}

	/**
	 * Prepares validation data for each user/developer.
	 *
	 * @param currentPeriodRange     current period date range
	 * @param previousPeriodRange    previous period date range
	 * @param userWiseCommits        map of user email to their commits
	 * @param assignees              set of assignees
	 * @param tool                   the SCM tool
	 * @param projectName            name of the project
	 * @param iterationKpiValueList  list to populate with user-level KPI values
	 * @return list of validation data for all users
	 */
	private List<RepoToolValidationData> prepareUserValidationData(CustomDateRange currentPeriodRange,
			CustomDateRange previousPeriodRange, Map<String, List<ScmCommits>> userWiseCommits, Set<Assignee> assignees,
			Tool tool, String projectName, List<IterationKpiValue> iterationKpiValueList) {
		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		return userWiseCommits.entrySet().stream().map(entry -> {
			String userEmail = entry.getKey();
			List<ScmCommits> userCommits = entry.getValue();
			String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);

			List<ScmCommits> currentCommitsList = filterCommitsByDateRange(userCommits, currentPeriodRange);
			double currentInnovationRate = getInnovationRate(currentCommitsList);

			List<ScmCommits> previousCommitsList = filterCommitsByDateRange(userCommits, previousPeriodRange);
			double previousInnovationRate = getInnovationRate(previousCommitsList);
			double deviationRate = calculateDeviationRate(currentInnovationRate, previousInnovationRate);

			String userKpiGroup = branchName + "#" + developerName;
			iterationKpiValueList.add(new IterationKpiValue(branchName, developerName,
					List.of(new IterationKpiData(userKpiGroup, currentInnovationRate, deviationRate, LABEL_INFO, "%", "%", null))));

			double overallInnovationRate = getInnovationRate(userCommits);
			int addedLines = userCommits.stream().mapToInt(ScmCommits::getAddedLines).sum();
			int changedLines = userCommits.stream().mapToInt(ScmCommits::getChangedLines).sum();

			return createValidationData(projectName, tool, developerName, null, overallInnovationRate, addedLines, changedLines);
		}).toList();
	}

	/**
	 * Creates validation data object for a developer.
	 *
	 * @param projectName    name of the project
	 * @param tool           the SCM tool
	 * @param developerName  name of the developer
	 * @param dateLabel      date label (can be null)
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
		return "INNOVATION_RATE_NON_TREND";
	}
}
