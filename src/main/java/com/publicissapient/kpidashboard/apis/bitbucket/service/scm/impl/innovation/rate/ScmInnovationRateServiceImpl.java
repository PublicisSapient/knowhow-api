package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.innovation.rate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketKPIService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy.KpiCalculationStrategy;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy.KpiStrategyRegistry;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class ScmInnovationRateServiceImpl
		extends BitBucketKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String ASSIGNEE_SET = "assigneeSet";
	private static final String COMMITS_LIST = "commitsList";

	private final ConfigHelperService configHelperService;
	private final KpiHelperService kpiHelperService;
	private final KpiStrategyRegistry kpiStrategyRegistry;

	@Override
	public String getQualifierType() {
		return KPICode.INNOVATION_RATE.name();
	}

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
			throws ApplicationException {
		Map<String, Node> nodeMap = Map.of(projectNode.getId(), projectNode);
		calculateProjectKpiTrendData(kpiElement, nodeMap, projectNode, kpiRequest);

		log.debug(
				"[PROJECT-WISE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				projectNode);

		if (kpiElement.getChartType().equalsIgnoreCase("line")) {

			Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
			calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.INNOVATION_RATE);

			Map<String, List<DataCount>> trendValuesMap =
					getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.INNOVATION_RATE);
			kpiElement.setTrendValueList(
					DeveloperKpiHelper.prepareDataCountGroups(trendValuesMap, KPICode.INNOVATION_RATE.getKpiId()));
		} else {
			kpiElement.setTrendValueList(nodeMap.get(projectNode.getId()).getValue());
		}
		return kpiElement;
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
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		Map<String, Object> scmDataMap = new HashMap<>();

		scmDataMap.put(ASSIGNEE_SET, getScmUsersFromBaseClass());
		scmDataMap.put(COMMITS_LIST, getCommitsFromBaseClass());
		return scmDataMap;
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI162(), KPICode.INNOVATION_RATE.getKpiId());
	}

	/**
	 * Populates KPI value to project leaf nodes. It also gives the trend analysis project wise.
	 *
	 * @param kpiElement kpi element
	 * @param mapTmp node map
	 * @param projectLeafNode leaf node of project
	 * @param kpiRequest kpi request
	 */
	@SuppressWarnings("unchecked")
	private void calculateProjectKpiTrendData(
			KpiElement kpiElement,
			Map<String, Node> mapTmp,
			Node projectLeafNode,
			KpiRequest kpiRequest) {
		String requestTrackerId = getRequestTrackerId();

		List<Tool> scmTools =
				DeveloperKpiHelper.getScmToolsForProject(
						projectLeafNode, configHelperService, kpiHelperService);

		if (CollectionUtils.isEmpty(scmTools)) {
			log.error(
					"[BITBUCKET-AGGREGATED-VALUE]. No SCM tools found for project {}",
					projectLeafNode.getProjectFilter());
			return;
		}

		Map<String, Object> scmDataMap = fetchKPIDataFromDb(null, null, null, null);

		List<ScmCommits> commits = (List<ScmCommits>) scmDataMap.get(COMMITS_LIST);
		Set<Assignee> assignees = new HashSet<>((Collection<Assignee>) scmDataMap.get(ASSIGNEE_SET));

		if (CollectionUtils.isEmpty(commits)) {
			log.error(
					"[BITBUCKET-AGGREGATED-VALUE]. No merge requests found for project {}", projectLeafNode);
			return;
		}

		List<RepoToolValidationData> validationDataList = new ArrayList<>();

		KpiCalculationStrategy<?> strategy =
				kpiStrategyRegistry.getStrategy(KPICode.INNOVATION_RATE, kpiElement.getChartType());

		List<ScmCommits> filteredNonMergeCommits =
				commits.stream().filter(commit -> !commit.getIsMergeCommit()).toList();
		Object kpiTrendDataByGroup =
				strategy.calculateKpi(
						kpiRequest,
						null,
						filteredNonMergeCommits,
						scmTools,
						validationDataList,
						assignees,
						projectLeafNode.getProjectFilter().getName());

		mapTmp.get(projectLeafNode.getId()).setValue(kpiTrendDataByGroup);
		populateExcelData(requestTrackerId, validationDataList, kpiElement);
	}

	private void populateExcelData(
			String requestTrackerId,
			List<RepoToolValidationData> validationDataList,
			KpiElement kpiElement) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			List<KPIExcelData> excelData = new ArrayList<>();
			KPIExcelUtility.populateInnovationRateExcelData(validationDataList, excelData);
			kpiElement.setExcelData(excelData);
			kpiElement.setExcelColumns(KPIExcelColumn.INNOVATION_RATE.getColumns());
		}
	}
}
