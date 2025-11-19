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

@Component
public class InnovationRateNonTrendKpiServiceImpl extends AbstractKpiCalculationStrategy<List<IterationKpiValue>> {

	@Override
	public List<IterationKpiValue> calculateKpi(KpiRequest kpiRequest, List<ScmMergeRequests> mergeRequests,
			List<ScmCommits> commits, List<Tool> scmTools, List<RepoToolValidationData> validationDataList,
			Set<Assignee> assignees, String projectName) {
		List<IterationKpiValue> iterationKpiValueList = new ArrayList<>();

		scmTools.forEach(tool -> processToolData(tool, commits, assignees, iterationKpiValueList, validationDataList,
				kpiRequest, projectName));

		return iterationKpiValueList;
	}

	private void processToolData(Tool tool, List<ScmCommits> commits, Set<Assignee> assignees,
			List<IterationKpiValue> iterationKpiValueList, List<RepoToolValidationData> validationDataList,
			KpiRequest kpiRequest, String projectName) {
		int dataPoints = kpiRequest.getXAxisDataPoints();
		LocalDateTime currentDate = DateUtil.getTodayTime();
		String duration = kpiRequest.getDuration();
		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;

		List<ScmCommits> commitsForBranch = DeveloperKpiHelper.filterCommitsForBranch(commits, tool);

		CustomDateRange periodRange = getCustomDateRange(currentDate, duration, dataPoints);
		List<ScmCommits> currentCommitsList = commitsForBranch.stream()
				.filter(request -> DateUtil.isWithinDateTimeRange(
						DateUtil.convertMillisToLocalDateTime(request.getCommitTimestamp()),
						periodRange.getStartDateTime(), periodRange.getEndDateTime()))
				.toList();
		double currentInnovationRate = getInnovationRate(currentCommitsList);

		CustomDateRange prevPeriodRange = getCustomDateRange(periodRange.getStartDateTime(), duration, dataPoints);
		List<ScmCommits> previousCommitsList = commitsForBranch.stream()
				.filter(request -> DateUtil.isWithinDateTimeRange(
						DateUtil.convertMillisToLocalDateTime(request.getCommitTimestamp()),
						prevPeriodRange.getStartDateTime(), prevPeriodRange.getEndDateTime()))
				.toList();
		double previousInnovationRate = getInnovationRate(previousCommitsList);
		double deviationRate = Math.round((currentInnovationRate - previousInnovationRate)
				/ (currentInnovationRate + previousInnovationRate) * 100);

		iterationKpiValueList.add(new IterationKpiValue(branchName, Constant.AGGREGATED_VALUE, List.of(
				new IterationKpiData(overallKpiGroup, currentInnovationRate, deviationRate, null, "hrs", "%", null))));

		Map<String, List<ScmCommits>> userWiseCommits = DeveloperKpiHelper.groupCommitsByUser(commitsForBranch);

		validationDataList.addAll(prepareUserValidationData(periodRange, prevPeriodRange, userWiseCommits, assignees,
				tool, projectName, iterationKpiValueList));
	}

	private double getInnovationRate(List<ScmCommits> commits) {
		return commits.stream().mapToDouble(commit -> {
			long linesOfCodeChanged = commit.getTotalLinesAffected();
			return linesOfCodeChanged != 0
					? BigDecimal.valueOf((commit.getAddedLines() * 100.0 / linesOfCodeChanged) / 10)
							.setScale(2, RoundingMode.HALF_UP).doubleValue()
					: 0.0;
		}).average().orElse(0.0);
	}

	private List<RepoToolValidationData> prepareUserValidationData(CustomDateRange periodRange,
			CustomDateRange prevPeriodRange, Map<String, List<ScmCommits>> userWiseCommits, Set<Assignee> assignees,
			Tool tool, String projectName, List<IterationKpiValue> iterationKpiValueList) {
		return userWiseCommits.entrySet().stream().map(entry -> {
			String userEmail = entry.getKey();
			List<ScmCommits> userCommits = entry.getValue();

			String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);
			double innovationRate = getInnovationRate(userCommits);
			int addedLines = userCommits.stream().mapToInt(ScmCommits::getAddedLines).sum();
			int changedLines = userCommits.stream().mapToInt(ScmCommits::getChangedLines).sum();

			String userKpiGroup = DeveloperKpiHelper.getBranchSubFilter(tool, projectName) + "#" + developerName;

			List<ScmCommits> currentCommitsList = userCommits.stream()
					.filter(request -> DateUtil.isWithinDateTimeRange(
							DateUtil.convertMillisToLocalDateTime(request.getCommitTimestamp()),
							periodRange.getStartDateTime(), periodRange.getEndDateTime()))
					.toList();
			double currentInnovationRate = getInnovationRate(currentCommitsList);

			List<ScmCommits> previousCommitsList = userCommits.stream()
					.filter(request -> DateUtil.isWithinDateTimeRange(
							DateUtil.convertMillisToLocalDateTime(request.getCommitTimestamp()),
							prevPeriodRange.getStartDateTime(), prevPeriodRange.getEndDateTime()))
					.toList();
			double previousInnovationRate = getInnovationRate(previousCommitsList);
			double deviationRate = Math.round((currentInnovationRate - previousInnovationRate)
					/ (currentInnovationRate + previousInnovationRate) * 100);

			iterationKpiValueList.add(new IterationKpiValue(DeveloperKpiHelper.getBranchSubFilter(tool, projectName),
					developerName, List.of(new IterationKpiData(userKpiGroup, currentInnovationRate, deviationRate,
							null, "hrs", "%", null))));

			return createValidationData(projectName, tool, developerName, null, innovationRate, addedLines,
					changedLines);
		}).toList();
	}

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

	@Override
	public String getStrategyType() {
		return "INNOVATION_RATE_NON_TREND";
	}
}
