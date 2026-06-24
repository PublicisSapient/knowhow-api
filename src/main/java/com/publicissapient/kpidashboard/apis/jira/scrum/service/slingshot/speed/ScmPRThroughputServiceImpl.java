package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.speed;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketKPIService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
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
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScmPRThroughputServiceImpl
		extends BitBucketKPIService<Long, List<Object>, Map<String, Object>> {

	private static final String ASSIGNEE_SET = "assigneeSet";
	private static final String MRS_LIST = "mrsList";

	private final ConfigHelperService configHelperService;
	private final KpiHelperService kpiHelperService;
	private final ScmKpiHelperService scmKpiHelperService;

	/** {@inheritDoc} */
	@Override
	public String getQualifierType() {
		return KPICode.PR_THROUGHPUT.name();
	}

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
			throws ApplicationException {
		kpiRequest.setXAxisDataPoints(12);
		kpiRequest.setDuration(CommonConstant.WEEK);
		Map<String, Node> nodeMap = Map.of(projectNode.getId(), projectNode);
		calculateProjectKpiTrendData(kpiElement, nodeMap, projectNode, kpiRequest);

		log.debug(
				"[PROJECT-WISE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				projectNode);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.PR_THROUGHPUT);

		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.PR_THROUGHPUT);

		kpiElement.setTrendValueList(
				DeveloperKpiHelper.prepareDataCountGroups(
						trendValuesMap, KPICode.PR_THROUGHPUT.getKpiId()));
		return kpiElement;
	}

	/** {@inheritDoc} */
	@Override
	public Long calculateKPIMetrics(Map<String, Object> t) {
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		Map<String, Object> scmDataMap = new HashMap<>();
		List<ScmMergeRequests> scmMergeRequests =
				scmKpiHelperService.getMergeRequests(
						leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId(),
						DeveloperKpiHelper.getStartAndEndDate(kpiRequest));
		List<Assignee> assigneeList =
				scmKpiHelperService.getJiraAssigneeForScmUsers(
						leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId());

		scmDataMap.put(ASSIGNEE_SET, assigneeList);
		scmDataMap.put(MRS_LIST, scmMergeRequests);

		return scmDataMap;
	}

	/** {@inheritDoc} */
	@Override
	public Long calculateKpiValue(List<Long> valueList, String kpiId) {
		return calculateKpiValueForLong(valueList, kpiId);
	}

	/** {@inheritDoc} */
	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI208(), KPICode.PR_THROUGHPUT.getKpiId());
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
		LocalDateTime currentDate = DateUtil.getTodayTime();
		int dataPoints = kpiRequest.getXAxisDataPoints();
		String duration = kpiRequest.getDuration();

		List<Tool> scmTools =
				DeveloperKpiHelper.getScmToolsForProject(
						projectLeafNode, configHelperService, kpiHelperService);
		if (CollectionUtils.isEmpty(scmTools)) {
			log.error(
					"[BITBUCKET-AGGREGATED-VALUE]. No SCM tools found for project {}",
					projectLeafNode.getProjectFilter());
			return;
		}

		Map<String, Object> scmDataMap =
				fetchKPIDataFromDb(List.of(projectLeafNode), null, null, kpiRequest);
		List<ScmMergeRequests> allMrs = (List<ScmMergeRequests>) scmDataMap.get(MRS_LIST);
		List<Assignee> assigneeList = (List<Assignee>) scmDataMap.get(ASSIGNEE_SET);

		if (CollectionUtils.isEmpty(allMrs)) {
			log.error("[BITBUCKET-AGGREGATED-VALUE]. No mrs found for project {}", projectLeafNode);
			return;
		}

		Map<String, String> emailToNameMap =
				CollectionUtils.emptyIfNull(assigneeList).stream()
						.filter(a -> CollectionUtils.isNotEmpty(a.getEmail()))
						.flatMap(
								a ->
										a.getEmail().stream()
												.filter(email -> email != null)
												.map(email -> Map.entry(email, a.getAssigneeName())))
						.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));

		Map<ObjectId, List<ScmMergeRequests>> mrsByProcessorItem =
				allMrs.stream()
						.filter(c -> c.getProcessorItemId() != null)
						.collect(Collectors.groupingBy(ScmMergeRequests::getProcessorItemId));

		Map<String, List<DataCount>> kpiTrendDataByGroup = new LinkedHashMap<>();
		List<RepoToolValidationData> validationDataList = new ArrayList<>();

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange periodRange =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(periodRange, duration);

			scmTools.forEach(
					tool -> {
						if (!DeveloperKpiHelper.isValidTool(tool)) {
							return;
						}
						ObjectId processorItemId = tool.getProcessorItemList().get(0).getId();
						List<ScmMergeRequests> toolMrs =
								mrsByProcessorItem.getOrDefault(processorItemId, Collections.emptyList());
						List<ScmMergeRequests> mrsInRange =
								DeveloperKpiHelper.filterMergeRequestsByMergedDate(toolMrs, periodRange);
						processToolData(
								tool,
								mrsInRange,
								emailToNameMap,
								dateLabel,
								projectLeafNode.getProjectFilter().getName(),
								kpiTrendDataByGroup,
								validationDataList);
					});

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}

		mapTmp.get(projectLeafNode.getId()).setValue(kpiTrendDataByGroup);
		Collections.reverse(validationDataList);
		populateExcelData(requestTrackerId, validationDataList, kpiElement);
	}

	private void processToolData(
			Tool tool,
			List<ScmMergeRequests> mrsInRange,
			Map<String, String> emailToNameMap,
			String dateLabel,
			String projectName,
			Map<String, List<DataCount>> kpiTrendDataByGroup,
			List<RepoToolValidationData> validationDataList) {

		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		String branchName = getBranchSubFilter(tool, projectName);
		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;

		List<ScmMergeRequests> mergeRequests =
				mrsInRange.stream()
						.filter(mrs -> mrs.isClosed() && "merged".equalsIgnoreCase(mrs.getState()))
						.toList();

		long totalMergeRequests = mergeRequests.size();

		setDataCount(projectName, dateLabel, overallKpiGroup, totalMergeRequests, kpiTrendDataByGroup);

		Map<String, List<ScmMergeRequests>> userWiseMergeRequest =
				DeveloperKpiHelper.groupMergeRequestsByUser(mergeRequests);

		userWiseMergeRequest.forEach(
				(userEmail, userMergeRequest) -> {
					String developerName = emailToNameMap.getOrDefault(userEmail, userEmail);
					long mrCount = userMergeRequest.size();
					String userKpiGroup = branchName + "#" + developerName;
					setDataCount(projectName, dateLabel, userKpiGroup, mrCount, kpiTrendDataByGroup);
					List<String> mrUrls =
							userMergeRequest.stream()
									.map(ScmMergeRequests::getMergeRequestUrl)
									.filter(java.util.Objects::nonNull)
									.toList();
					validationDataList.add(
							createValidationData(
									projectName, tool, developerName, userEmail, dateLabel, mrCount, mrUrls));
				});
	}

	private void populateExcelData(
			String requestTrackerId,
			List<RepoToolValidationData> validationDataList,
			KpiElement kpiElement) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			List<KPIExcelData> excelData = new ArrayList<>();
			KPIExcelUtility.populatePRThroughputExcelData(validationDataList, excelData);
			kpiElement.setExcelData(excelData);
			kpiElement.setExcelColumns(KPIExcelColumn.SCM_PR_THROUGHPUT.getColumns());
		}
	}

	private void setDataCount(
			String projectName,
			String week,
			String kpiGroup,
			Long mrCount,
			Map<String, List<DataCount>> dataCountMap) {

		List<DataCount> dataCounts = dataCountMap.computeIfAbsent(kpiGroup, k -> new ArrayList<>());

		DataCount dataCount =
				dataCounts.stream()
						.filter(dc -> dc.getDate().equals(week))
						.findFirst()
						.orElseGet(
								() -> {
									DataCount newDataCount = new DataCount();
									newDataCount.setSProjectName(projectName);
									newDataCount.setDate(week);
									newDataCount.setKpiGroup(kpiGroup);
									newDataCount.setValue(0L);
									newDataCount.setLineValue(0L);
									dataCounts.add(newDataCount);
									return newDataCount;
								});

		dataCount.setValue(((Number) dataCount.getValue()).longValue() + mrCount);
		Map<String, Object> hoverValues = new HashMap<>();
		dataCount.setHoverValue(hoverValues);
	}

	private RepoToolValidationData createValidationData(
			String projectName,
			Tool tool,
			String developerName,
			String developerEmail,
			String dateLabel,
			long mrCount,
			List<String> mergeRequestUrls) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(
				tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug());
		validationData.setDeveloperName(developerName);
		validationData.setDeveloperEmail(developerEmail);
		validationData.setDate(dateLabel);
		validationData.setMrCount(mrCount);
		validationData.setMergeRequestUrls(mergeRequestUrls);
		return validationData;
	}
}
