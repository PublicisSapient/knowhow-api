/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *    http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.jenkins.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;

import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsServiceKanbanR;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsServiceR;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsToolConfigServiceImpl;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller for all jenkins related api.
 *
 * @author pkum34
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Jenkins API", description = "APIs for Jenkins KPI Metrics")
public class JenkinsController {

	private final JenkinsServiceR jenkinsService;

	private final JenkinsServiceKanbanR jenkinsServiceKanban;

	private final CacheService cacheService;

	private final JenkinsToolConfigServiceImpl jenkinsToolConfigService;

	/**
	 * Gets jenkins aggregated metrics.
	 *
	 * @param kpiRequest the kpi request
	 * @return the jenkins aggregated metrics
	 * @throws Exception the exception
	 */
	@Operation(
			summary = "Get Jenkins KPI Maturity Values",
			description =
					"Processes Jenkins-based KPI requests and returns calculated maturity values for the specified KPIs")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "KPI maturity values calculated successfully",
						content =
								@Content(
										mediaType = "application/json",
										array = @ArraySchema(schema = @Schema(implementation = KpiElement.class)))),
				@ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error",
						content = @Content)
			})
	@PostMapping(value = "/jenkins/kpi", produces = APPLICATION_JSON_VALUE) // NOSONAR
	// @PreAuthorize("hasPermission(null,'KPI_FILTER')")
	public ResponseEntity<List<KpiElement>> getJenkinsAggregatedMetrics(
			@Parameter(description = "KPI Request Payload", required = true) @NotNull @RequestBody
					KpiRequest kpiRequest)
			throws Exception { // NOSONAR
		MDC.put("JenkinsKpiRequest", kpiRequest.getRequestTrackerId());
		log.info("Received Jenkins KPI request {}", kpiRequest);
		long jenkinsRequestStartTime = System.currentTimeMillis();
		MDC.put("JenkinsRequestStartTime", String.valueOf(jenkinsRequestStartTime));
		cacheService.setIntoApplicationCache(
				Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JENKINS.name(),
				kpiRequest.getRequestTrackerId());

		if (CollectionUtils.isEmpty(kpiRequest.getKpiList())) {
			throw new MissingServletRequestParameterException("kpiList", "List");
		}

		List<KpiElement> responseList = jenkinsService.process(kpiRequest);
		MDC.put(
				"TotalJenkinsRequestTime",
				String.valueOf(System.currentTimeMillis() - jenkinsRequestStartTime));

		log.info("");
		MDC.clear();
		if (responseList.isEmpty()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(responseList);
		} else {
			return ResponseEntity.ok().body(responseList);
		}
	}

	/**
	 * Gets jenkins kanban aggregated metrics.
	 *
	 * @param kpiRequest the kpi request
	 * @return the jenkins kanban aggregated metrics
	 * @throws Exception the exception
	 */
	@Operation(
			summary = "Get Jenkins Kanban KPI Maturity Values",
			description =
					"Processes Jenkins Kanban-based KPI requests and returns calculated maturity values for the specified KPIs")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "KPI maturity values calculated successfully",
						content =
								@Content(
										mediaType = "application/json",
										array = @ArraySchema(schema = @Schema(implementation = KpiElement.class)))),
				@ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error",
						content = @Content)
			})
	@PostMapping(value = "/jenkinskanban/kpi", produces = APPLICATION_JSON_VALUE) // NOSONAR
	public ResponseEntity<List<KpiElement>> getJenkinsKanbanAggregatedMetrics(
			@Parameter(description = "KPI Request Payload", required = true) @NotNull @RequestBody
					KpiRequest kpiRequest)
			throws Exception { // NOSONAR
		MDC.put("JenkinsKanbanKpiRequest", kpiRequest.getRequestTrackerId());
		log.info("Received Jenkins Kanban KPI request {}", kpiRequest);
		long jenkinsKanbanRequestStartTime = System.currentTimeMillis();
		MDC.put("JenkinsKanbanRequestStartTime", String.valueOf(jenkinsKanbanRequestStartTime));
		cacheService.setIntoApplicationCache(
				Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JENKINSKANBAN.name(),
				kpiRequest.getRequestTrackerId());

		if (CollectionUtils.isEmpty(kpiRequest.getKpiList())) {
			throw new MissingServletRequestParameterException("kpiList", "List");
		}

		List<KpiElement> responseList = jenkinsServiceKanban.process(kpiRequest);
		MDC.put(
				"TotalJenkinsKanbanRequestTime",
				String.valueOf(System.currentTimeMillis() - jenkinsKanbanRequestStartTime));

		log.info("");
		MDC.clear();
		if (responseList.isEmpty()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(responseList);
		} else {
			return ResponseEntity.ok().body(responseList);
		}
	}

	/**
	 * @param connectionId the jenkins server connection details
	 * @return @{@code ServiceResponse}
	 */
	@Operation(
			summary = "Get Jenkins Job Names",
			description =
					"Fetches the list of Jenkins job names associated with the specified connection ID")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Jenkins job names fetched successfully",
						content = {
							@Content(
									mediaType = "application/json",
									schema = @Schema(implementation = ServiceResponse.class))
						}),
				@ApiResponse(
						responseCode = "404",
						description = "No Jobs details found for the given connection ID",
						content = @Content),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error",
						content = @Content)
			})
	@GetMapping(
			value = "/jenkins/jobName/{connectionId}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getJenkinsJobs(
			@Parameter(
							description = "Jenkins Connection ID",
							example = "60d21b4667d0d8992e610c85",
							required = true)
					@PathVariable
					String connectionId) {
		List<String> jobUrlList = jenkinsToolConfigService.getJenkinsJobNameList(connectionId);
		if (CollectionUtils.isEmpty(jobUrlList)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(new ServiceResponse(false, "No Jobs details found", null));
		} else {
			List<String> jobNameList = new ArrayList<>();
			for (String jobUrl : jobUrlList) {
				int jobIndex = jobUrl.indexOf("job");
				String jobName = jobUrl.substring(jobIndex + 4, jobUrl.length() - 1);
				jobName = jobName.replace("/job", "");
				jobNameList.add(jobName);
			}
			return ResponseEntity.ok()
					.body(new ServiceResponse(true, "Fetched Jobs Successfully", jobNameList));
		}
	}
}
