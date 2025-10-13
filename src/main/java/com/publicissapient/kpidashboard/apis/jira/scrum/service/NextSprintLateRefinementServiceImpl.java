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

package com.publicissapient.kpidashboard.apis.jira.scrum.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.iterationdashboard.JiraIterationKPIService;
import com.publicissapient.kpidashboard.apis.jira.service.iterationdashboard.JiraIterationServiceR;
import com.publicissapient.kpidashboard.apis.model.*;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.NormalizedJira;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NextSprintLateRefinementServiceImpl extends JiraIterationKPIService {
	private static final String INCLUDED_ISSUES = "included issues";

	@Autowired private ConfigHelperService configHelperService;
	@Autowired private SprintRepository sprintRepository;
	@Autowired private JiraIssueRepository jiraIssueRepository;
	@Autowired private JiraIterationServiceR jiraIterationServiceR;

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node sprintNode)
			throws ApplicationException {
		projectWiseLeafNodeValue(sprintNode, kpiElement, kpiRequest);
		return kpiElement;
	}

	@Override
	public String getQualifierType() {
		return KPICode.NEXT_SPRINT_LATE_REFINEMENT.name();
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			Node leafNode, String startDate, String endDate, KpiRequest kpiRequest) {
		Map<String, Object> resultListMap = new HashMap<>();
		if (null != leafNode) {
			FieldMapping fieldMapping =
					configHelperService
							.getFieldMappingMap()
							.get(leafNode.getProjectFilter().getBasicProjectConfigId());
			// to modify sprintdetails on the basis of configuration for the project
			if (CollectionUtils.isNotEmpty(fieldMapping.getJiraIssueTypeNamesKPI188())) {
				log.info("Future Late Refinement -> Requested sprint : {}", leafNode.getName());
				SprintDetails activeSprint = getSprintDetailsFromBaseClass();
				// Future Sprint
				ObjectId basicProjectConfigId = leafNode.getProjectFilter().getBasicProjectConfigId();
				List<SprintDetails> futureSprintList =
						sprintRepository.findByBasicProjectConfigIdAndStateIgnoreCaseOrderByStartDateASC(
								basicProjectConfigId, SprintDetails.SPRINT_STATE_FUTURE);

				// Find the next sprint
				SprintDetails sprintDetails =
						futureSprintList.stream()
								.filter(
										sprint ->
												sprint.getStartDate() != null
														&& DateUtil.stringToLocalDateTime(
																		sprint.getStartDate(), DateUtil.TIME_FORMAT_WITH_SEC)
																.isAfter(
																		DateUtil.stringToLocalDateTime(
																				activeSprint.getEndDate(), DateUtil.TIME_FORMAT_WITH_SEC)))
								.findFirst()
								.orElse(null);

				if (sprintDetails == null) {
					return new HashMap<>();
				}

				Set<String> totalIssues =
						jiraIssueRepository.findBySprintID(sprintDetails.getSprintID()).stream()
								.filter(a -> getTypeNames(fieldMapping).contains(a.getTypeName().toLowerCase()))
								.map(JiraIssue::getNumber)
								.collect(Collectors.toSet());
				Map<String, Object> mapOfFilter = new HashMap<>();
				jiraIterationServiceR.createAdditionalFilterMap(kpiRequest, mapOfFilter);
				Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
				uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfFilter);

				List<JiraIssue> totalJiraIssueList =
						jiraIssueRepository.findIssueByNumberWithAdditionalFilter(
								totalIssues, uniqueProjectMap);
				if (CollectionUtils.isNotEmpty(totalJiraIssueList)) {
					resultListMap.put(INCLUDED_ISSUES, new ArrayList<>(totalJiraIssueList));
				}
			}
		}

		return resultListMap;
	}

	private static Set<String> getTypeNames(FieldMapping fieldMapping) {
		return fieldMapping.getJiraIssueTypeNamesKPI188().stream()
				.flatMap(
						name ->
								"Defect".equalsIgnoreCase(name)
										? Stream.of("defect", NormalizedJira.DEFECT_TYPE.getValue().toLowerCase())
										: Stream.of(name.trim().toLowerCase()))
				.collect(Collectors.toSet());
	}

	/**
	 * Populates KPI value to sprint leaf nodes and gives the trend analysis at sprint level.
	 *
	 * @param latestSprint
	 * @param kpiElement
	 * @param kpiRequest
	 */
	@SuppressWarnings("unchecked")
	private void projectWiseLeafNodeValue(
			Node latestSprint, KpiElement kpiElement, KpiRequest kpiRequest) {
		Object basicProjectConfigId = latestSprint.getProjectFilter().getBasicProjectConfigId();
		FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigId);

		Map<String, Object> resultMap = fetchKPIDataFromDb(latestSprint, null, null, kpiRequest);
		List<JiraIssue> totalIssues = (List<JiraIssue>) resultMap.get(INCLUDED_ISSUES);
		Double unRefinedStories = 0.0;
		if (CollectionUtils.isNotEmpty(totalIssues)) {

			Map<String, IssueKpiModalValue> issueKpiModalObject =
					KpiDataHelper.createMapOfIssueModal(totalIssues);
			for (JiraIssue issue : totalIssues) {
				KPIExcelUtility.populateIssueModal(issue, fieldMapping, issueKpiModalObject);
				IssueKpiModalValue data = issueKpiModalObject.get(issue.getNumber());
				if (CollectionUtils.isNotEmpty(issue.getUnRefinedValue188())) {
					data.setUnRefined("Y");
					unRefinedStories++;
				} else {
					data.setUnRefined("N");
				}
			}
			kpiElement.setSprint(latestSprint.getName());
			kpiElement.setModalHeads(KPIExcelColumn.NEXT_SPRINT_LATE_REFINEMENT.getColumns());
			kpiElement.setIssueData(new HashSet<>(issueKpiModalObject.values()));
			kpiElement.setDataGroup(createDataGroup((double) totalIssues.size(), unRefinedStories));
		}
	}

	/**
	 * @param totalStories
	 * @param unRefinedStories
	 * @return
	 */
	private KpiDataGroup createDataGroup(Double totalStories, Double unRefinedStories) {
		KpiDataGroup dataGroup = new KpiDataGroup();
		List<KpiData> dataGroup1 = new ArrayList<>();
		dataGroup1.add(createKpiData("Un-Refined Stories", 1, "", unRefinedStories));
		dataGroup1.add(createKpiData("Total Stories", 2, "", totalStories));
		dataGroup1.add(createKpiData("%", 3, "", calculateUnrefined(unRefinedStories, totalStories)));
		dataGroup.setDataGroup1(dataGroup1);
		return dataGroup;
	}

	/**
	 * Creates kpi data object.
	 *
	 * @param name
	 * @param order
	 * @param unit
	 * @return
	 */
	private KpiData createKpiData(String name, Integer order, String unit, Double kpiValue) {
		KpiData data = new KpiData();
		data.setName(name);
		data.setOrder(order);
		data.setUnit(unit);
		data.setShowAsLegend(false);
		data.setKpiValue(kpiValue);
		return data;
	}

	private Double calculateUnrefined(Double unRefined, Double total) {
		return roundingOff((unRefined * 100) / total);
	}
}
