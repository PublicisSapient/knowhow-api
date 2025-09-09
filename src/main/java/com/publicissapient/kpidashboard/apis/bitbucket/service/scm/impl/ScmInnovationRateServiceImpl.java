package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketKPIService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class ScmInnovationRateServiceImpl extends BitBucketKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String ASSIGNEE_SET = "assigneeSet";
	private static final String COMMITS_LIST = "commitsList";

	private final ConfigHelperService configHelperService;
	private final KpiHelperService kpiHelperService;

	@Override
	public String getQualifierType() {
		return KPICode.INNOVATION_RATE.name();
	}

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
			throws ApplicationException {
		Map<String, Node> nodeMap = Map.of(projectNode.getId(), projectNode);
		projectWiseLeafNodeValue(kpiElement, nodeMap, projectNode, kpiRequest);

		log.debug("[PROJECT-WISE][{}]. Values of leaf node after KPI calculation {}", kpiRequest.getRequestTrackerId(),
				projectNode);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.INNOVATION_RATE);

		Map<String, List<DataCount>> trendValuesMap = getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue,
				KPICode.INNOVATION_RATE);
		kpiElement.setTrendValueList(DeveloperKpiHelper.prepareDataCountGroups(trendValuesMap));
		return kpiElement;
	}

	/**
	 * Populates KPI value to project leaf nodes. It also gives the trend analysis
	 * project wise.
	 *
	 * @param kpiElement
	 *            kpi element
	 * @param mapTmp
	 *            node map
	 * @param projectLeafNode
	 *            leaf node of project
	 * @param kpiRequest
	 *            kpi request
	 */
	@SuppressWarnings("unchecked")
	private void projectWiseLeafNodeValue(KpiElement kpiElement, Map<String, Node> mapTmp, Node projectLeafNode,
			KpiRequest kpiRequest) {
		String requestTrackerId = getRequestTrackerId();
		LocalDateTime currentDate = DateUtil.getTodayTime();
		int dataPoints = kpiRequest.getXAxisDataPoints();
		String duration = kpiRequest.getDuration();

		List<Tool> scmTools = DeveloperKpiHelper.getScmToolsForProject(projectLeafNode, configHelperService,
				kpiHelperService);

		if (CollectionUtils.isEmpty(scmTools)) {
			log.error("[BITBUCKET-AGGREGATED-VALUE]. No SCM tools found for project {}",
					projectLeafNode.getProjectFilter());
			return;
		}

		Map<String, Object> scmDataMap = fetchKPIDataFromDb(null, null, null, null);

		List<ScmCommits> mergeRequests = (List<ScmCommits>) scmDataMap.get(COMMITS_LIST);
		Set<Assignee> assignees = new HashSet<>((Collection<Assignee>) scmDataMap.get(ASSIGNEE_SET));

		if (CollectionUtils.isEmpty(mergeRequests)) {
			log.error("[BITBUCKET-AGGREGATED-VALUE]. No merge requests found for project {}", projectLeafNode);
			return;
		}

		Map<String, List<DataCount>> aggregatedDataMap = new LinkedHashMap<>();
		List<RepoToolValidationData> validationDataList = new ArrayList<>();

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange weekRange = KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(weekRange, duration);

			List<ScmCommits> filteredCommitsList = mergeRequests.stream()
					.filter(request -> DateUtil.isWithinDateTimeRange(
							DateUtil.convertMillisToLocalDateTime(request.getCommitTimestamp()),
							weekRange.getStartDateTime(), weekRange.getEndDateTime()))
					.toList();

			scmTools.forEach(tool -> processToolData(tool, filteredCommitsList, assignees, aggregatedDataMap,
					validationDataList, dateLabel, projectLeafNode.getProjectFilter().getName()));

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}

		mapTmp.get(projectLeafNode.getId()).setValue(aggregatedDataMap);
		populateExcelData(requestTrackerId, validationDataList, kpiElement);
	}

	private void processToolData(Tool tool, List<ScmCommits> commitsList, Set<Assignee> assignees,
			Map<String, List<DataCount>> aggregatedDataMap, List<RepoToolValidationData> validationDataList,
			String dateLabel, String projectName) {
		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		String branchName = getBranchSubFilter(tool, projectName);
		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;

		List<ScmCommits> matchingCommits = DeveloperKpiHelper.filterCommitsForBranch(commitsList, tool);

		double innovationRate = getInnovationRate(matchingCommits);

		DeveloperKpiHelper.setDataCount(projectName, dateLabel, overallKpiGroup, innovationRate, new HashMap<>(),
				aggregatedDataMap);

		Map<String, List<ScmCommits>> userWiseScmCommits = DeveloperKpiHelper.groupCommitsByUser(matchingCommits);

		validationDataList.addAll(prepareUserValidationData(userWiseScmCommits, assignees, tool, projectName, dateLabel,
				aggregatedDataMap));
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
			Map<String, List<DataCount>> aggregatedDataMap) {
		return userWiseCommits.entrySet().stream().map(entry -> {
			String userEmail = entry.getKey();
			List<ScmCommits> userCommits = entry.getValue();

			String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);
			double innovationRate = getInnovationRate(userCommits);
			int addedLines = userCommits.stream().mapToInt(ScmCommits::getAddedLines).sum();
			int changedLines = userCommits.stream().mapToInt(ScmCommits::getChangedLines).sum();

			String userKpiGroup = getBranchSubFilter(tool, projectName) + "#" + developerName;

			DeveloperKpiHelper.setDataCount(projectName, dateLabel, userKpiGroup, innovationRate, new HashMap<>(),
					aggregatedDataMap);

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

	private void populateExcelData(String requestTrackerId, List<RepoToolValidationData> validationDataList,
			KpiElement kpiElement) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			List<KPIExcelData> excelData = new ArrayList<>();
			KPIExcelUtility.populateInnovationRateExcelData(validationDataList, excelData);
			kpiElement.setExcelData(excelData);
			kpiElement.setExcelColumns(KPIExcelColumn.INNOVATION_RATE.getColumns());
		}
	}

	@Override
	public Double calculateKPIMetrics(Map<String, Object> stringObjectMap) {
		return null;
	}

	@Override
	public Double calculateKpiValue(List<Double> valueList, String kpiId) {
		return calculateKpiValueForDouble(valueList, kpiId);
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(List<Node> leafNodeList, String startDate, String endDate,
			KpiRequest kpiRequest) {
		Map<String, Object> scmDataMap = new HashMap<>();

		scmDataMap.put(ASSIGNEE_SET, getScmUsersFromBaseClass());
		scmDataMap.put(COMMITS_LIST, getCommitsFromBaseClass());
		return scmDataMap;
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(fieldMapping.getThresholdValueKPI162(), KPICode.INNOVATION_RATE.getKpiId());
	}
}
