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

package com.publicissapient.kpidashboard.apis.zephyr.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.zephyr.service.ZephyrService;
import com.publicissapient.kpidashboard.apis.zephyr.service.ZephyrServiceKanban;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST endpoint to manage all KPI's based out of Zypher as source.
 *
 * @author tauakram
 */
@RestController
@Slf4j
@Tag(name = "Zephyr KPI API", description = "REST API's for Zephyr related KPI")
@RequiredArgsConstructor
public class ZephyrController {

	private final ZephyrService zephyrService;

	private final ZephyrServiceKanban zephyrServiceKanban;

	private final CacheService cacheService;

	/**
	 * Gets zephyr data metrics.
	 *
	 * @param kpiRequest the kpi request
	 * @return the zephyr metrics
	 * @throws Exception the exception
	 */
	@Operation(
			summary = "Retrieve Zephyr KPI metrics",
			description =
					"Posts a request to retrieve KPI metrics based on the provided KPI request details.",
			responses = {
				@ApiResponse(
						responseCode = "200",
						description = "Successful retrieval of KPI metrics",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = KpiElement.class),
										examples = {
											@ExampleObject(
													name = "Example Response",
													value = "[{\"kpiName\": \"Velocity\", \"kpiValue\": \"15\"}]")
										})),
				@ApiResponse(
						responseCode = "403",
						description = "Forbidden - No KPI metrics found for the given request"),
				@ApiResponse(
						responseCode = "400",
						description = "Bad Request - Missing required parameters")
			})
	@PostMapping(value = "/zypher/kpi", produces = APPLICATION_JSON_VALUE) // NOSONAR
	public ResponseEntity<List<KpiElement>> getZephyrMetrics(
			@NotNull @RequestBody KpiRequest kpiRequest) throws Exception { // NOSONAR

		log.info(
				"[ZEPHYR][{}]. Received Zephyr KPI request {}",
				kpiRequest.getRequestTrackerId(),
				kpiRequest);

		cacheService.setIntoApplicationCache(
				Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.ZEPHYR.name(),
				kpiRequest.getRequestTrackerId());

		if (CollectionUtils.isEmpty(kpiRequest.getKpiList())) {
			throw new MissingServletRequestParameterException("kpiList", "List");
		}

		List<KpiElement> responseList = zephyrService.process(kpiRequest);
		if (responseList.isEmpty()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(responseList);
		} else {
			return ResponseEntity.ok().body(responseList);
		}
	}

	/**
	 * Gets zephyr kanban data metrics.
	 *
	 * @param kpiRequest the kpi request
	 * @return the zephyr kanban metrics
	 * @throws Exception the exception
	 */
	@PostMapping(value = "/zypherkanban/kpi", produces = APPLICATION_JSON_VALUE) // NOSONAR
	// @PreAuthorize("hasPermission(null,'KPI_FILTER')")
	public ResponseEntity<List<KpiElement>> getZephyrKanbanMetrics(
			@NotNull @RequestBody KpiRequest kpiRequest) throws Exception { // NOSONAR

		log.info(
				"[ZEPHYR KANBAN][{}]. Received Zephyr KPI request {}",
				kpiRequest.getRequestTrackerId(),
				kpiRequest);

		cacheService.setIntoApplicationCache(
				Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.ZEPHYRKANBAN.name(),
				kpiRequest.getRequestTrackerId());

		if (CollectionUtils.isEmpty(kpiRequest.getKpiList())) {
			throw new MissingServletRequestParameterException("kpiList", "List");
		}

		List<KpiElement> responseList = zephyrServiceKanban.process(kpiRequest);
		if (responseList.isEmpty()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(responseList);
		} else {
			return ResponseEntity.ok().body(responseList);
		}
	}
}
