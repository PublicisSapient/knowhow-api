/*
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.report.dto.ReportRequest;
import com.publicissapient.kpidashboard.apis.report.service.ReportService;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Controller class for managing reports. */
@RestController
@RequestMapping("/reports")
@Slf4j
@Validated
@RequiredArgsConstructor
@Tag(name = "Report Controller", description = "APIs for Report Management")
public class ReportController {

	private final ReportService reportService;

	/**
	 * Creates a new report.
	 *
	 * @param report the report data transfer object
	 * @return the service response containing the created report
	 */
	@Operation(summary = "Create Report", description = "API to create a new report")
	@ApiResponses(
			value = {
					@ApiResponse(
							responseCode = "201",
							description = "Report created successfully"),
					@ApiResponse(
							responseCode = "400",
							description = "Invalid report data supplied")
			})
	@PostMapping
	public ServiceResponse create(
			@Parameter(description = "ReportRequest object containing report details", required = true)
			@Valid @RequestBody ReportRequest report) {
		log.info(
				"Received request to create a report with name: {}",
				CommonUtils.sanitize(report.getName()));
		return reportService.create(report);
	}

	/**
	 * Updates an existing report.
	 *
	 * @param id the report ID
	 * @param report the report data transfer object
	 * @return the service response containing the updated report
	 * @throws EntityNotFoundException if the report is not found
	 */
	@Operation(summary = "Update Report", description = "API to update an existing report")
	@ApiResponses(
			value = {
					@ApiResponse(
							responseCode = "200",
							description = "Report updated successfully"),
					@ApiResponse(
							responseCode = "404",
							description = "Report not found")
			})
	@PutMapping("/{id}")
	public ServiceResponse update(
			@Parameter(description = "ID of the report to be updated", required = true, example = "605c72ef4f1a2565f0e4b0b5")
			@PathVariable String id,
			@Parameter(description = "ReportRequest object containing updated report details", required = true)
			@Valid @RequestBody ReportRequest report)
			throws EntityNotFoundException {
		log.info("Received request to update report with ID: {}", CommonUtils.sanitize(id));
		return reportService.update(id, report);
	}

	/**
	 * Deletes a report by ID.
	 *
	 * @param id the report ID
	 * @return a response entity with no content
	 */
	@Operation(summary = "Delete Report", description = "API to delete a report by ID")
	@ApiResponses(
			value = {
					@ApiResponse(
							responseCode = "204",
							description = "Report deleted successfully"),
					@ApiResponse(
							responseCode = "404",
							description = "Report not found")
			})
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(
			@Parameter(description = "ID of the report to be deleted", required = true, example = "605c72ef4f1a2565f0e4b0b5")
			@PathVariable String id) {
		log.info("Received request to delete report with ID: {}", CommonUtils.sanitize(id));
		reportService.delete(id);
		log.debug("Report deleted successfully with ID: {}", CommonUtils.sanitize(id));
		return ResponseEntity.noContent().build();
	}

	/**
	 * Fetches a report by ID.
	 *
	 * @param id the report ID
	 * @return the service response containing the report
	 */
	@Operation(summary = "Get Report by ID", description = "API to get a report by ID")
	@ApiResponses(
			value = {
					@ApiResponse(
							responseCode = "200",
							description = "Report fetched successfully"),
					@ApiResponse(
							responseCode = "404",
							description = "Report not found")
			})
	@GetMapping("/{id}")
	@ResponseStatus(HttpStatus.OK)
	public ServiceResponse getReportById(
			@Parameter(description = "ID of the report to be fetched", required = true, example = "605c72ef4f1a2565f0e4b0b5")
			@PathVariable String id) {
		log.info("Received request to fetch report by ID: {}", CommonUtils.sanitize(id));
		return reportService.getReportById(id);
	}

	/**
	 * Fetches reports by createdBy with pagination.
	 *
	 * @param createdBy the report createdBy
	 * @param page the page number
	 * @param limit the page size
	 * @return the service response containing a page of reports
	 */
	@Operation(summary = "Get Reports by CreatedBy", description = "API to get reports by createdBy with pagination")
	@ApiResponses(
			value = {
					@ApiResponse(
							responseCode = "200",
							description = "Reports fetched successfully"),
					@ApiResponse(
							responseCode = "400",
							description = "Invalid parameters supplied")
			})
	@GetMapping
	@ResponseStatus(HttpStatus.OK)
	public ServiceResponse getReportsByCreatedBy(
			@Parameter(description = "Creator of the reports to be fetched", required = true, example = "john.doe")
			@RequestParam String createdBy,
			@Parameter(description = "Page number for pagination", example = "0")
			@RequestParam(defaultValue = "0") int page,
			@Parameter(description = "Number of reports per page", example = "10")
			@RequestParam(defaultValue = "10") int limit) {
		log.info(
				"Received request to fetch reports by createdBy: {}, page: {}, size: {}",
				CommonUtils.sanitize(createdBy),
				page,
				limit);
		return reportService.getReportsByCreatedBy(createdBy, page, limit);
	}
}
