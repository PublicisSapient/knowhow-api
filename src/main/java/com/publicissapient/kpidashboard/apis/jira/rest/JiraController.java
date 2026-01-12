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

package com.publicissapient.kpidashboard.apis.jira.rest;

import java.util.List;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.jira.model.BoardRequestDTO;
import com.publicissapient.kpidashboard.apis.jira.service.JiraNonTrendKPIServiceR;
import com.publicissapient.kpidashboard.apis.jira.service.JiraServiceKanbanR;
import com.publicissapient.kpidashboard.apis.jira.service.JiraServiceR;
import com.publicissapient.kpidashboard.apis.jira.service.JiraToolConfigServiceImpl;
import com.publicissapient.kpidashboard.apis.jira.service.NonTrendServiceFactory;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.model.application.dto.AssigneeResponseDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * This controller class handles Jira KPIs request. It handles all KPIs of Scrum and Kanban.
 *
 * @author tauakram
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class JiraController {
	private static final String KPI_LIST = "kpiList";
	private static final String JIRA_SCRUM_KPI_REQ = "JiraScrumKpiRequest";

	private final CacheService cacheService;
	private final JiraServiceR jiraService;
	private final JiraServiceKanbanR jiraServiceKanban;
	private final JiraToolConfigServiceImpl jiraToolConfigService;

	private final NonTrendServiceFactory serviceFactory;

	@PostMapping(value = "/jira/kpi")
	public ResponseEntity<List<KpiElement>> getJiraAggregatedMetrics(
			@RequestBody KpiRequest kpiRequest)
			throws MissingServletRequestParameterException, EntityNotFoundException {
		MDC.put(JIRA_SCRUM_KPI_REQ, kpiRequest.getRequestTrackerId());
		log.info("Received Jira KPI request {}", kpiRequest);

		long jiraRequestStartTime = System.currentTimeMillis();
		MDC.put("JiraRequestStartTime", String.valueOf(jiraRequestStartTime));
		cacheService.setIntoApplicationCache(
				Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name(),
				kpiRequest.getRequestTrackerId());

		if (CollectionUtils.isEmpty(kpiRequest.getKpiList())) {
			throw new MissingServletRequestParameterException(KPI_LIST, "List");
		}

		List<KpiElement> responseList = jiraService.process(kpiRequest);
		MDC.put(
				"TotalJiraRequestTime", String.valueOf(System.currentTimeMillis() - jiraRequestStartTime));

		MDC.clear();
		if (responseList.isEmpty()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(responseList);
		} else {
			return ResponseEntity.ok().body(responseList);
		}
	}

	@PostMapping(value = "/jirakanban/kpi")
	public ResponseEntity<List<KpiElement>> getJiraKanbanAggregatedMetrics(
			@RequestBody KpiRequest kpiRequest)
			throws MissingServletRequestParameterException, EntityNotFoundException {
		MDC.put(JIRA_SCRUM_KPI_REQ, kpiRequest.getRequestTrackerId());
		log.info("Received Jira Kanban KPI request {}", kpiRequest);

		long jiraKanbanRequestStartTime = System.currentTimeMillis();
		MDC.put("JiraKanbanRequestStartTime", String.valueOf(jiraKanbanRequestStartTime));

		cacheService.setIntoApplicationCache(
				Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRAKANBAN.name(),
				kpiRequest.getRequestTrackerId());

		if (CollectionUtils.isEmpty(kpiRequest.getKpiList())) {
			throw new MissingServletRequestParameterException(KPI_LIST, "List");
		}

		List<KpiElement> responseList = jiraServiceKanban.process(kpiRequest);
		MDC.put(
				"TotalJiraKanbanRequestTime",
				String.valueOf(System.currentTimeMillis() - jiraKanbanRequestStartTime));

		MDC.clear();

		return ResponseEntity.ok().body(responseList);
	}

	@PostMapping(value = "/jira/board", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getJiraBoardDetailsList(
			@RequestBody BoardRequestDTO boardRequestDTO) {
		return jiraToolConfigService.getJiraBoardDetailsList(boardRequestDTO);
	}

	@GetMapping(value = "/jira/assignees/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ServiceResponse getJiraAssigneesList(@PathVariable("id") String projectConfigId) {
		ServiceResponse response;
		AssigneeResponseDTO assigneeResponseDTO =
				jiraToolConfigService.getProjectAssigneeDetails(projectConfigId);
		if (assigneeResponseDTO != null) {
			response =
					new ServiceResponse(true, "Successfully fetched assignee list", assigneeResponseDTO);
		} else {
			response = new ServiceResponse(false, "Error while fetching Assignee List", null);
		}
		return response;
	}

	@PostMapping(value = "/jira/nonTrend/kpi")
	public ResponseEntity<List<KpiElement>> getJiraIterationMetrics(
			@NotNull @RequestBody KpiRequest kpiRequest)
			throws MissingServletRequestParameterException, EntityNotFoundException {

		MDC.put(JIRA_SCRUM_KPI_REQ, kpiRequest.getRequestTrackerId());
		log.info("Received Jira KPI request for iteration{}", kpiRequest);

		long jiraRequestStartTime = System.currentTimeMillis();
		MDC.put("JiraRequestStartTime", String.valueOf(jiraRequestStartTime));
		cacheService.setIntoApplicationCache(
				Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name(),
				kpiRequest.getRequestTrackerId());

		if (CollectionUtils.isEmpty(kpiRequest.getKpiList())) {
			throw new MissingServletRequestParameterException(KPI_LIST, "List");
		}
		JiraNonTrendKPIServiceR jiraNonTrendKPIServiceR =
				serviceFactory.getService(kpiRequest.getKpiList().get(0).getKpiCategory());
		List<KpiElement> responseList = jiraNonTrendKPIServiceR.process(kpiRequest);
		MDC.put(
				"TotalJiraRequestTime", String.valueOf(System.currentTimeMillis() - jiraRequestStartTime));

		MDC.clear();
		return ResponseEntity.ok().body(responseList);
	}
}
