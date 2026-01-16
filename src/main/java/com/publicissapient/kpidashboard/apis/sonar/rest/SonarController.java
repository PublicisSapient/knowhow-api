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

package com.publicissapient.kpidashboard.apis.sonar.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import javax.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.apache.commons.collections4.CollectionUtils;
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
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.sonar.service.SonarServiceKanbanR;
import com.publicissapient.kpidashboard.apis.sonar.service.SonarServiceR;
import com.publicissapient.kpidashboard.apis.sonar.service.SonarToolConfigServiceImpl;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author tauakram
 */
@RestController
@Slf4j
@AllArgsConstructor
@Tag(name = "Sonar Controller", description = "APIs for Sonar KPI Management")
public class SonarController {

	private static final String FETCHED_SUCCESSFULLY = "fetched successfully";

	private final CacheService cacheService;
	private final SonarServiceR sonarService;
	private final SonarServiceKanbanR sonarServiceKanban;
	private final SonarToolConfigServiceImpl sonarToolConfigService;

	/**
	 * Gets Sonar Aggregate Metrics for Scrum projects
	 *
	 * @param kpiRequest the kpi request
	 * @return {@code ResponseEntity<List<KpiElement>>}
	 * @throws Exception exception thrown when kpi processing fails
	 */
	@Operation(summary = "Get Sonar KPI Data", description = "API to get Sonar KPI data for Scrum projects")
	@ApiResponses(
			value = {
					@ApiResponse(
							responseCode = "200",
							description = "Sonar KPI data fetched successfully"),
					@ApiResponse(
							responseCode = "400",
							description = "Bad Request, invalid parameters supplied"),
					@ApiResponse(
							responseCode = "500",
							description = "Internal server error while processing Sonar KPI data")
			})
	@PostMapping(value = "/sonar/kpi", produces = APPLICATION_JSON_VALUE) // NOSONAR
	// @PreAuthorize("hasPermission(null,'KPI_FILTER')")
	public ResponseEntity<List<KpiElement>> getSonarAggregatedMetrics(
			@Parameter(description = "KPI Request payload containing the details for fetching Sonar KPI data", required = true)
			@NotNull @RequestBody KpiRequest kpiRequest) throws Exception { // NOSONAR

		log.info(
				"[SONAR][{}]. Received Sonar KPI request {}", kpiRequest.getRequestTrackerId(), kpiRequest);

		cacheService.setIntoApplicationCache(
				Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.SONAR.name(),
				kpiRequest.getRequestTrackerId());

		if (CollectionUtils.isEmpty(kpiRequest.getKpiList())) {
			throw new MissingServletRequestParameterException("kpiList", "List");
		}

		List<KpiElement> responseList = sonarService.process(kpiRequest);
		if (responseList.isEmpty()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(responseList);
		} else {
			return ResponseEntity.ok().body(responseList);
		}
	}

	/**
	 * Gets Sonar Aggregate Metrics for Kanban projects
	 *
	 * @param kpiRequest the request
	 * @return {@code ResponseEntity<List<KpiElement>>}
	 * @throws Exception exception thrown when kpi processing fails
	 */
	@Operation(summary = "Get Sonar Kanban KPI Data", description = "API to get Sonar KPI data for Kanban projects")
	@ApiResponses(
			value = {
					@ApiResponse(
							responseCode = "200",
							description = "Sonar Kanban KPI data fetched successfully"),
					@ApiResponse(
							responseCode = "400",
							description = "Bad Request, invalid parameters supplied"),
					@ApiResponse(
							responseCode = "500",
							description = "Internal server error while processing Sonar Kanban KPI data")
			})
	@PostMapping(value = "/sonarkanban/kpi", produces = APPLICATION_JSON_VALUE) // NOSONAR
	public ResponseEntity<List<KpiElement>> getSonarKanbanAggregatedMetrics(
			@Parameter(description = "KPI Request payload containing the details for fetching Sonar Kanban KPI data",
					required = true)
			@NotNull @RequestBody KpiRequest kpiRequest) throws Exception { // NOSONAR

		log.info(
				"[SONAR KANBAN][{}]. Received Sonar KPI request {}",
				kpiRequest.getRequestTrackerId(),
				kpiRequest);

		cacheService.setIntoApplicationCache(
				Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.SONARKANBAN.name(),
				kpiRequest.getRequestTrackerId());

		if (CollectionUtils.isEmpty(kpiRequest.getKpiList())) {
			throw new MissingServletRequestParameterException("kpiList", "List");
		}

		List<KpiElement> responseList = sonarServiceKanban.process(kpiRequest);
		if (responseList.isEmpty()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(responseList);
		} else {
			return ResponseEntity.ok().body(responseList);
		}
	}

	/**
	 * Provides the list of Sonar version based on branches Support and type
	 *
	 * @return #{@code ServiceResponse}
	 */
	@GetMapping(value = "/sonar/version", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getSonarVersionList() {
		ServiceResponse response = sonarToolConfigService.getSonarVersionList();
		return ResponseEntity.ok(response);
	}

	/**
	 * Provides the list of Sonar Project's Key.
	 *
	 * @param connectionId the Sonar connection details
	 * @param organizationKey in case of Sonar Cloud
	 * @return @{@code ServiceResponse}
	 */
	@Operation(
			summary = "Get Sonar Project Key List",
			description = "API to get the list of Sonar Project's Key based on connection ID and organization key")
	@ApiResponses(
			value = {
					@ApiResponse(
							responseCode = "200",
							description = "Sonar Project Key List fetched successfully"),
					@ApiResponse(
							responseCode = "404",
							description = "No Sonar projects found for the given connection and organization key")
			})
	@GetMapping(
			value = "/sonar/project/{connectionId}/{organizationKey}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getSonarProjectList(
			@Parameter(
					description = "Sonar connection ID",
					required = true,
					example = "sonarConnectionIdExample")
			@PathVariable String connectionId,
			@Parameter(
					description = "Sonar organization key (required for Sonar Cloud)",
					required = true,
					example = "sonarOrganizationKeyExample")
			@PathVariable String organizationKey) {
		List<String> projectKeyList =
				sonarToolConfigService.getSonarProjectKeyList(connectionId, organizationKey);
		if (CollectionUtils.isEmpty(projectKeyList)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(new ServiceResponse(false, "No projects found", null));
		} else {
			return ResponseEntity.status(HttpStatus.OK)
					.body(new ServiceResponse(true, FETCHED_SUCCESSFULLY, projectKeyList));
		}
	}

	/**
	 * Provides the list of Sonar Project's Branch API call only if version is supported.
	 *
	 * @param connectionId the Sonar server connection details
	 * @param version the Sonar server api version
	 * @param projectKey the Sonar server project's key
	 * @return @{@code ServiceResponse}
	 */
	@Operation(
			summary = "Get Sonar Project Branch List",
			description = "API to get the list of Sonar Project's Branch based on connection ID, version and project key")
	@ApiResponses(
			value = {
					@ApiResponse(
							responseCode = "200",
							description = "Sonar Project Branch List fetched successfully"),
					@ApiResponse(
							responseCode = "404",
							description = "No branches found for the given Sonar project"),
					@ApiResponse(
							responseCode = "500",
							description = "Internal server error while fetching Sonar project branches")
			})
	@GetMapping(
			value = "/sonar/branch/{connectionId}/{version}/{projectKey}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getSonarProjectBranchList(
			@Parameter(
					description = "Sonar connection ID",
					required = true,
					example = "sonarConnectionIdExample")
			@PathVariable String connectionId,
			@Parameter(
					description = "Sonar version",
					required = true,
					example = "sonarVersionExample")
			@PathVariable String version,
			@Parameter(
					description = "Sonar project key",
					required = true,
					example = "sonarProjectKeyExample")
			@PathVariable String projectKey) {
		ServiceResponse response =
				sonarToolConfigService.getSonarProjectBranchList(connectionId, version, projectKey);
		HttpStatus httpStatus = HttpStatus.OK;
		if (Boolean.FALSE.equals(response.getSuccess())) {
			httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
		} else if (response.getData() == null) {
			httpStatus = HttpStatus.NOT_FOUND;
		}
		return ResponseEntity.status(httpStatus).body(response);
	}
}
