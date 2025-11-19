package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.innovation.rate;

import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy.AbstractKpiCalculationStrategy;
import org.springframework.stereotype.Component;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class InnovationRateTrendKpiServiceImpl extends AbstractKpiCalculationStrategy<Map<String, List<DataCount>>> {
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
			List<ScmCommits> filteredCommitsList = commits.stream()
					.filter(request -> DateUtil.isWithinDateTimeRange(
							DateUtil.convertMillisToLocalDateTime(request.getCommitTimestamp()),
							periodRange.getStartDateTime(), periodRange.getEndDateTime()))
					.toList();

			scmTools.forEach(tool -> processToolData(tool, filteredCommitsList, assignees, kpiTrendDataByGroup,
					validationDataList, dateLabel, projectName));

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}
		return kpiTrendDataByGroup;
	}

	private void processToolData(Tool tool, List<ScmCommits> commitsList, Set<Assignee> assignees,
			Map<String, List<DataCount>> kpiTrendDataByGroup, List<RepoToolValidationData> validationDataList,
			String dateLabel, String projectName) {
		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;

		List<ScmCommits> matchingCommits = DeveloperKpiHelper.filterCommitsForBranch(commitsList, tool);

		double innovationRate = getInnovationRate(matchingCommits);

		DeveloperKpiHelper.setDataCount(projectName, dateLabel, overallKpiGroup, innovationRate, new HashMap<>(),
				kpiTrendDataByGroup);

		Map<String, List<ScmCommits>> userWiseScmCommits = DeveloperKpiHelper.groupCommitsByUser(matchingCommits);

		validationDataList.addAll(prepareUserValidationData(userWiseScmCommits, assignees, tool, projectName, dateLabel,
				kpiTrendDataByGroup));
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

	private List<RepoToolValidationData> prepareUserValidationData(Map<String, List<ScmCommits>> userWiseCommits,
			Set<Assignee> assignees, Tool tool, String projectName, String dateLabel,
			Map<String, List<DataCount>> kpiTrendDataByGroup) {
		return userWiseCommits.entrySet().stream().map(entry -> {
			String userEmail = entry.getKey();
			List<ScmCommits> userCommits = entry.getValue();

			String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);
			double innovationRate = getInnovationRate(userCommits);
			int addedLines = userCommits.stream().mapToInt(ScmCommits::getAddedLines).sum();
			int changedLines = userCommits.stream().mapToInt(ScmCommits::getChangedLines).sum();

			String userKpiGroup = DeveloperKpiHelper.getBranchSubFilter(tool, projectName) + "#" + developerName;

			DeveloperKpiHelper.setDataCount(projectName, dateLabel, userKpiGroup, innovationRate, new HashMap<>(),
					kpiTrendDataByGroup);

			return createValidationData(projectName, tool, developerName, dateLabel, innovationRate, addedLines,
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
		return "INNOVATION_RATE_TREND";
	}
}
