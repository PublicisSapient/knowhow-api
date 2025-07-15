/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.management.rest;

import com.publicissapient.kpidashboard.apis.management.service.DashboardHealthService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/actuator/dashboard-health")
@RequiredArgsConstructor
@Tag(name = "Dashboard Health", description = "APIs for retrieving health details for dashboards and their associated APIs")
public class DashboardHealthController {

	private final DashboardHealthService dashboardHealthService;

	@Operation(summary = "Get Overall Health Details", description = "Retrieves overall health details of the application including app health, metrics, dashboard statuses, and links to individual dashboard health endpoints.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved overall health details"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@GetMapping
	@ReadOperation
	public ResponseEntity<ServiceResponse> health() {
		log.info("Received request to fetch overall health details.");
		Map<String, Object> healthDetails = dashboardHealthService.healthDetails();
		log.info("Successfully fetched overall health details.");
		ServiceResponse response = new ServiceResponse(true, "Fetched health details", healthDetails);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Get Dashboard Health", description = "Retrieves the health details for a specified dashboard, including its overall status and API metrics.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved dashboard health details"),
			@ApiResponse(responseCode = "404", description = "Dashboard not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@GetMapping("/{dashboardName}")
	@ReadOperation
	public ResponseEntity<ServiceResponse> dashboardHealth(
			@Parameter(description = "Name of the dashboard whose health details are to be retrieved", required = true) @PathVariable String dashboardName) {
		log.info("Received request to fetch health details for dashboard: {}", dashboardName);
		try {
			Map<String, Object> dashboardHealth = dashboardHealthService.dashboardHealth(dashboardName);
			log.info("Successfully fetched health details for dashboard: {}", dashboardName);
			ServiceResponse response = new ServiceResponse(true, "Fetched health for dashboard: " + dashboardName,
					dashboardHealth);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("Error fetching health details for dashboard: {}", dashboardName, e);
			throw e;
		}
	}
}