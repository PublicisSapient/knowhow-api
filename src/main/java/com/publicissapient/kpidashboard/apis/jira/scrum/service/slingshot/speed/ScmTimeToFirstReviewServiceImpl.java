package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.speed;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
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
public class ScmTimeToFirstReviewServiceImpl
		extends BitBucketKPIService<Long, List<Object>, Map<String, Object>> {

	private static final String MR_COUNT = "No of PRs";
	private static final double MILLIS_PER_SECOND = 1000.0;
	private static final String ASSIGNEE_SET = "assigneeSet";
	private static final String MRS_LIST = "mrsList";

	private final ConfigHelperService configHelperService;
	private final KpiHelperService kpiHelperService;
	private final ScmKpiHelperService scmKpiHelperService;

	/** {@inheritDoc} */
	@Override
	public String getQualifierType() {
		return KPICode.TIME_TO_FIRST_REVIEW.name();
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
		calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.TIME_TO_FIRST_REVIEW);

		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.TIME_TO_FIRST_REVIEW);

		kpiElement.setTrendValueList(
				DeveloperKpiHelper.prepareDataCountGroups(
						trendValuesMap, KPICode.TIME_TO_FIRST_REVIEW.getKpiId()));
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
				fieldMapping.getThresholdValueKPI210(), KPICode.TIME_TO_FIRST_REVIEW.getKpiId());
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

		Map<String, Object> scmDataMap =
				fetchKPIDataFromDb(List.of(projectLeafNode), null, null, kpiRequest);
		List<ScmMergeRequests> allMrs = (List<ScmMergeRequests>) scmDataMap.get(MRS_LIST);
		List<Assignee> assigneeList = (List<Assignee>) scmDataMap.get(ASSIGNEE_SET);
		List<RepoToolValidationData> validationDataList = new ArrayList<>();
		if (CollectionUtils.isEmpty(allMrs)) {
			log.error("[BITBUCKET-AGGREGATED-VALUE]. No mrs found for project {}", projectLeafNode);
			return;
		}

		Map<String, List<DataCount>> kpiTrendDataByGroup =
				calculateKpi(
						kpiRequest,
						allMrs,
						scmTools,
						validationDataList,
						new HashSet<>(assigneeList),
						projectLeafNode.getProjectFilter().getName());
		mapTmp.get(projectLeafNode.getId()).setValue(kpiTrendDataByGroup);
		Collections.reverse(validationDataList);
		populateExcelData(requestTrackerId, validationDataList, kpiElement);
	}

	private void populateExcelData(
			String requestTrackerId,
			List<RepoToolValidationData> validationDataList,
			KpiElement kpiElement) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			List<KPIExcelData> excelData = new ArrayList<>();
			KPIExcelUtility.populateTimeToFirstReviewExcelData(validationDataList, excelData);
			kpiElement.setExcelData(excelData);
			kpiElement.setExcelColumns(KPIExcelColumn.TIME_TO_FIRST_REVIEW.getColumns());
		}
	}

	public Map<String, List<DataCount>> calculateKpi(
			KpiRequest kpiRequest,
			List<ScmMergeRequests> mergeRequests,
			List<Tool> scmTools,
			List<RepoToolValidationData> validationDataList,
			Set<Assignee> assignees,
			String projectName) {
		LocalDateTime currentDate = DateUtil.getTodayTime();
		int dataPoints = kpiRequest.getXAxisDataPoints();
		String duration = kpiRequest.getDuration();

		Map<String, List<DataCount>> kpiTrendDataByGroup = new LinkedHashMap<>();

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange periodRange =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(periodRange, duration);
			List<ScmMergeRequests> filteredMergeRequests =
					filterPickedRequestsByDateRange(mergeRequests, periodRange);

			scmTools.forEach(
					tool ->
							processToolData(
									tool,
									filteredMergeRequests,
									assignees,
									kpiTrendDataByGroup,
									validationDataList,
									dateLabel,
									projectName));

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}
		return kpiTrendDataByGroup;
	}

	/**
	 * Filters merge requests picked for review within the specified date range.
	 *
	 * @param mergeRequests list of merge requests to filter
	 * @param dateRange date range to filter by
	 * @return filtered list of merge requests
	 */
	private List<ScmMergeRequests> filterPickedRequestsByDateRange(
			List<ScmMergeRequests> mergeRequests, CustomDateRange dateRange) {
		return mergeRequests.stream()
				.filter(
						request -> request.getPickedForReviewOn() != null && request.getCreatedDate() != null)
				.filter(
						request ->
								DateUtil.isWithinDateTimeRange(
										DateUtil.convertMillisToLocalDateTime(request.getPickedForReviewOn()),
										dateRange.getStartDateTime(),
										dateRange.getEndDateTime()))
				.toList();
	}

	/**
	 * Processes data for a single SCM tool and calculates pickup times.
	 *
	 * @param tool the SCM tool to process
	 * @param mergeRequests list of merge requests for the period
	 * @param assignees set of assignees
	 * @param kpiTrendDataByGroup map to populate with trend data
	 * @param validationDataList list to populate with validation data
	 * @param dateLabel label for the time period
	 * @param projectName name of the project
	 */
	private void processToolData(
			Tool tool,
			List<ScmMergeRequests> mergeRequests,
			Set<Assignee> assignees,
			Map<String, List<DataCount>> kpiTrendDataByGroup,
			List<RepoToolValidationData> validationDataList,
			String dateLabel,
			String projectName) {
		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		List<ScmMergeRequests> matchingRequests =
				DeveloperKpiHelper.filterMergeRequestsForBranch(mergeRequests, tool);

		long totalPickUpTime =
				matchingRequests.stream().mapToLong(this::calculatePickupTimeHours).sum();
		long totalMergeRequests = matchingRequests.size();

		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;
		DeveloperKpiHelper.setDataCount(
				projectName,
				dateLabel,
				overallKpiGroup,
				totalPickUpTime,
				Map.of(MR_COUNT, totalMergeRequests),
				kpiTrendDataByGroup);

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests =
				DeveloperKpiHelper.groupMergeRequestsByUser(matchingRequests);
		validationDataList.addAll(
				prepareUserValidationData(
						userWiseMergeRequests, assignees, tool, projectName, dateLabel, kpiTrendDataByGroup));
	}

	/**
	 * Calculates average pickup time for a list of merge requests.
	 *
	 * @param mergeRequests list of merge requests
	 * @return average pickup time in hours
	 */
	private long calculateAveragePickupTime(List<ScmMergeRequests> mergeRequests) {
		List<Long> pickUpTimes = mergeRequests.stream().map(this::calculatePickupTimeHours).toList();
		return pickUpTimes.isEmpty()
				? 0
				: Math.round(pickUpTimes.stream().mapToLong(Long::longValue).average().orElse(0));
	}

	/**
	 * Calculates pickup time in hours for a single merge request.
	 *
	 * @param mergeRequest the merge request
	 * @return pickup time in hours
	 */
	private long calculatePickupTimeHours(ScmMergeRequests mergeRequest) {
		LocalDateTime pickedForReviewOn =
				DateUtil.convertMillisToLocalDateTime(mergeRequest.getPickedForReviewOn());
		LocalDateTime createdDate =
				DateUtil.convertMillisToLocalDateTime(mergeRequest.getCreatedDate());
		long seconds = ChronoUnit.SECONDS.between(createdDate, pickedForReviewOn);
		return KpiHelperService.convertMilliSecondsToHours(seconds * MILLIS_PER_SECOND);
	}

	/**
	 * Prepares validation data for each user/developer.
	 *
	 * @param userWiseMergeRequests map of user email to their merge requests
	 * @param assignees set of assignees
	 * @param tool the SCM tool
	 * @param projectName name of the project
	 * @param dateLabel label for the time period
	 * @param kpiTrendDataByGroup map to populate with user-level trend data
	 * @return list of validation data for all users
	 */
	private List<RepoToolValidationData> prepareUserValidationData(
			Map<String, List<ScmMergeRequests>> userWiseMergeRequests,
			Set<Assignee> assignees,
			Tool tool,
			String projectName,
			String dateLabel,
			Map<String, List<DataCount>> kpiTrendDataByGroup) {
		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		return userWiseMergeRequests.entrySet().stream()
				.map(
						entry -> {
							String userEmail = entry.getKey();
							List<ScmMergeRequests> userMergeRequests = entry.getValue();
							String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);

							long avgPickUpTime = calculateAveragePickupTime(userMergeRequests);
							long userMrCount = userMergeRequests.size();

							String userKpiGroup = branchName + "#" + developerName;
							DeveloperKpiHelper.setDataCount(
									projectName,
									dateLabel,
									userKpiGroup,
									avgPickUpTime,
									Map.of(MR_COUNT, userMrCount),
									kpiTrendDataByGroup);

							return userMergeRequests.stream()
									.filter(mr -> mr.getCreatedDate() != null && mr.getPickedForReviewOn() != null)
									.map(
											mr ->
													createValidationData(
															projectName, tool, developerName, userEmail, dateLabel, mr))
									.toList();
						})
				.flatMap(List::stream)
				.toList();
	}

	/**
	 * Creates validation data object for a merge request.
	 *
	 * @param projectName name of the project
	 * @param tool the SCM tool
	 * @param developerName name of the developer
	 * @param userEmail email of the developer
	 * @param dateLabel label for the time period
	 * @param mergeRequest the merge request
	 * @return populated validation data object
	 */
	private RepoToolValidationData createValidationData(
			String projectName,
			Tool tool,
			String developerName,
			String userEmail,
			String dateLabel,
			ScmMergeRequests mergeRequest) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(
				tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug());
		validationData.setDeveloperName(developerName);
		validationData.setDeveloperEmail(userEmail);
		validationData.setDate(dateLabel);
		validationData.setMergeRequestUrl(mergeRequest.getMergeRequestUrl());

		LocalDateTime createdDateTime =
				DateUtil.convertMillisToLocalDateTime(mergeRequest.getCreatedDate());
		LocalDateTime pickUpDateTime =
				DateUtil.convertMillisToLocalDateTime(mergeRequest.getPickedForReviewOn());
		long timeToMergeSeconds = ChronoUnit.SECONDS.between(createdDateTime, pickUpDateTime);
		validationData.setPickupTime(
				(double)
						KpiHelperService.convertMilliSecondsToHours(timeToMergeSeconds * MILLIS_PER_SECOND));

		LocalDateTime createdDateTimeUTC = DateUtil.localDateTimeToUTC(createdDateTime);
		LocalDateTime pickUpDateTimeUTC = DateUtil.localDateTimeToUTC(pickUpDateTime);
		validationData.setPrRaisedTime(String.valueOf(createdDateTimeUTC));
		validationData.setPrActivityTime(String.valueOf(pickUpDateTimeUTC));
		validationData.setPrStatus(mergeRequest.getState());
		return validationData;
	}
}
