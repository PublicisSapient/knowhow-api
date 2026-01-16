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

package com.publicissapient.kpidashboard.apis.filter.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.filter.factory.FilterServiceFactory;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyService;
import com.publicissapient.kpidashboard.apis.model.AccountFilterRequest;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * REST service managing all the aggregated requests.
 *
 * @author tauakram
 */
@Slf4j
@RestController
@Tag(name = "Account Hierarchy Controller", description = "APIs for Account Hierarchy Management")
public class AccountHierarchyController {

	/**
	 * Returns filter options.
	 *
	 * @param filter request body map of organisationId or businessunitId or accountId.
	 * @return AccountFilterResponse
	 * @throws ApplicationException ApplicationException
	 */
	@Operation(
			summary = "Filter Account Data",
			description = "Returns filtered account data based on the provided criteria.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Successfully retrieved filtered data"),
				@ApiResponse(responseCode = "400", description = "Invalid request parameters"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@PostMapping(
			value = "/filterdata",
			consumes = APPLICATION_JSON_VALUE,
			produces = APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> filterData(
			@Parameter(
							description = "Account Filter Request",
							required = true,
							example =
									"{ "
											+ "  \"maturityLevel\": \"1\", "
											+ "  \"currentSelection\": 2, "
											+ "  \"currentSelectionLabel\": \"Sprint\", "
											+ "  \"kanban\": false, "
											+ "  \"sprintIncluded\": [\"SPRINT_23\", \"SPRINT_24\"], "
											+ "  \"activeSprintIncluded\": true "
											+ "  \"filterDataList\": [ "
											+ "    { "
											+ "      \"maturityLevel\": \"PROJECT\", "
											+ "      \"level\": 1, "
											+ "      \"label\": \"Project\", "
											+ "      \"show\": true, "
											+ "      \"filterData\": [ "
											+ "        { "
											+ "          \"nodeId\": \"PROJ-101\", "
											+ "          \"nodeName\": \"project-alpha\", "
											+ "          \"nodeDisplayName\": \"Project Alpha\", "
											+ "          \"path\": \"/ORG/PROJ-101\", "
											+ "          \"labelName\": \"Project\", "
											+ "          \"level\": 1, "
											+ "          \"onHold\": false "
											+ "        } "
											+ "      ] "
											+ "    } ]"
											+ "}")
					@RequestBody
					AccountFilterRequest filter)
			throws ApplicationException {
		AccountHierarchyService<?, ?> accountHierarchyService = null;
		try {
			if (filter.isKanban()) {
				accountHierarchyService = FilterServiceFactory.getFilterService(Constant.KANBAN);
			} else {
				accountHierarchyService = FilterServiceFactory.getFilterService(Constant.SCRUM);
			}
		} catch (ApplicationException ae) {
			log.error("[Hierarchy ]. Error while creating filter data . No data found");
			throw ae;
		}
		ServiceResponse response = new ServiceResponse(false, "No hierarchy found", null);
		if (null != accountHierarchyService) {
			response =
					new ServiceResponse(
							true, "fetched successfully", accountHierarchyService.getFilteredList(filter));
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}
}
