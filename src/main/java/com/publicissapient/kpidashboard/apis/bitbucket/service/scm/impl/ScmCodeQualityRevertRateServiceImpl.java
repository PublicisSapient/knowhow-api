/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketKPIService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.*;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author valsa anil
 */
/**
 * SCM Code Quality Revert Rate Service Implementation
 *
 * <h3>REVERT RATE LOGIC</h3>
 *
 * <b>What:</b> Measures the percentage of merge requests that are later
 * reverted, indicating code stability and quality issues.
 *
 * <p>
 * <b>Why:</b> High revert rates suggest unstable code changes, poor testing, or
 * insufficient code review processes.
 *
 * <p>
 * <b>How it works:</b>
 *
 * <pre>
 * 1. Analyzes merge requests within specified time periods 2. Identifies
 * reverted pull requests by checking commit patterns 3. Calculates: Revert Rate
 * = (reverted PRs / total merged PRs) * 100
 *
 * Example: - 100 PRs merged in a week - 5 PRs were later reverted - Revert Rate
 * = (5/100) * 100 = 5%
 *
 * <h3>OUTPUT FORMAT</h3> Returns MetricsHolder map containing: - Key: "branch
 * -> repository -> project#developer" or "#Overall" - Value: MetricsHolder with
 * total merges and revert counts
 *
 * @author valsa anil
 * @since 2025
 * @see MetricsHolder
 * @see ScmCodeQualityMetricsImpl
 */
@Service
@Slf4j
@AllArgsConstructor
public class ScmCodeQualityRevertRateServiceImpl
		extends BitBucketKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String ASSIGNEE_SET = "assigneeSet";
	private static final String MERGE_REQUEST_LIST = "mergeRequestList";

	private final ConfigHelperService configHelperService;
	private final KpiHelperService kpiHelperService;
	private final ScmKpiHelperService scmKpiHelperService;

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
			throws ApplicationException {
		Map<String, MetricsHolder> revertRateMap = new HashMap<>();
		calculateProjectKpiTrendData(projectNode, kpiRequest, revertRateMap);

		log.debug(
				"[PROJECT-WISE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				projectNode);
		kpiElement.setTrendValueList(revertRateMap);
		return kpiElement;
	}

	/**
	 * Fetch data from db
	 *
	 * @param leafNodeList leaf node list
	 * @param startDate start date
	 * @param endDate end date
	 * @param kpiRequest kpi request
	 * @return map of data
	 */
	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {

		LocalDateTime currentDate = DateUtil.getTodayTime();
		int dataPoints = kpiRequest.getXAxisDataPoints();
		String duration = kpiRequest.getDuration();
		CustomDateRange dateRange =
				KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration, dataPoints);
		ObjectId projectBasicConfigId =
				leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId();
		Map<String, Object> scmDataMap = new HashMap<>();
		scmDataMap.put(ASSIGNEE_SET, getScmUsersFromBaseClass());
		scmDataMap.put(
				MERGE_REQUEST_LIST, scmKpiHelperService.getMergeRequests(projectBasicConfigId, dateRange));
		return scmDataMap;
	}

	@Override
	public String getQualifierType() {
		return KPICode.REVERT_RATE.name();
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
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI180(), KPICode.REVERT_RATE.getKpiId());
	}

	@SuppressWarnings("unchecked")
	private void calculateProjectKpiTrendData(
			Node projectLeafNode, KpiRequest kpiRequest, Map<String, MetricsHolder> revertRateMap) {
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
		List<ScmMergeRequests> mergeRequests =
				(List<ScmMergeRequests>) scmDataMap.get(MERGE_REQUEST_LIST);
		Set<Assignee> assignees = new HashSet<>((Collection<Assignee>) scmDataMap.get(ASSIGNEE_SET));

		if (CollectionUtils.isEmpty(mergeRequests)) {
			log.error(
					"[BITBUCKET-AGGREGATED-VALUE]. No merge requests found for project {}", projectLeafNode);
			return;
		}

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange periodRange =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);

			List<ScmMergeRequests> mergedRequestsInRange =
					DeveloperKpiHelper.filterMergeRequestsByUpdateDate(mergeRequests, periodRange);

			scmTools.forEach(
					tool ->
							processToolData(
									tool,
									mergedRequestsInRange,
									assignees,
									projectLeafNode.getProjectFilter().getName(),
									revertRateMap));

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}
	}

	private void processToolData(
			Tool tool,
			List<ScmMergeRequests> mergeRequests,
			Set<Assignee> assignees,
			String projectName,
			Map<String, MetricsHolder> revertRateMap) {
		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		String branchName = getBranchSubFilter(tool, projectName);
		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;

		List<ScmMergeRequests> branchMergeRequests =
				DeveloperKpiHelper.filterMergeRequestsForBranch(mergeRequests, tool);

		long totalMergeRequests = branchMergeRequests.size();
		long revertedPRs = countRevertedPRs(branchMergeRequests);
		MetricsHolder calculation =
				revertRateMap.computeIfAbsent(overallKpiGroup, key -> new MetricsHolder());
		calculation.addTotalMerges(totalMergeRequests);
		calculation.addTotalRevertPRs(revertedPRs);

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests =
				DeveloperKpiHelper.groupMergeRequestsByUser(branchMergeRequests);
		prepareUserValidationData(userWiseMergeRequests, assignees, tool, projectName, revertRateMap);
	}

	private void prepareUserValidationData(
			Map<String, List<ScmMergeRequests>> userWiseMergeRequests,
			Set<Assignee> assignees,
			Tool tool,
			String projectName,
			Map<String, MetricsHolder> revertRateMap) {

		for (Map.Entry<String, List<ScmMergeRequests>> entry : userWiseMergeRequests.entrySet()) {
			String userEmail = entry.getKey();
			List<ScmMergeRequests> userMergeRequests = entry.getValue();

			String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);
			long userMrCount = userMergeRequests.size();
			long revertedPRs = countRevertedPRs(userMergeRequests);

			String userKpiGroup = getBranchSubFilter(tool, projectName) + "#" + developerName;
			MetricsHolder calculation =
					revertRateMap.computeIfAbsent(userKpiGroup, key -> new MetricsHolder());
			calculation.addTotalRevertPRs(revertedPRs);
			calculation.addTotalMerges(userMrCount);
		}
	}

	private long countRevertedPRs(List<ScmMergeRequests> mergeRequests) {
		return mergeRequests.stream().filter(this::isRevertedPR).count();
	}

	private boolean isRevertedPR(ScmMergeRequests mergeRequest) {
		return mergeRequest.getTitle() != null
				&& mergeRequest.getTitle().toLowerCase().contains("revert");
	}
}
