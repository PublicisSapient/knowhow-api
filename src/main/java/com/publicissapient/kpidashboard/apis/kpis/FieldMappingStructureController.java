package com.publicissapient.kpidashboard.apis.kpis;

import org.apache.commons.lang.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.model.FieldMappingStructureResponse;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.ProjectAccessUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;

/**
 * Rest Controller for all kpi field mapping requests.
 *
 * @author dayshank2
 */
@RestController
@RequestMapping("/kpiFieldMapping")
@AllArgsConstructor
@Tag(name = "KPI Field Mapping Controller", description = "APIs for KPI Field Mapping Structure")
public class FieldMappingStructureController {
	private final KpiHelperService kPIHelperService;

	private ProjectAccessUtil projectAccessUtil;

	@Operation(
			summary = "Fetch KPI Field Mapping Structure by KPI ID",
			description =
					"Retrieve the field mapping structure associated with a specific KPI ID within a project.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved the field mapping structure",
						content =
								@Content(
										mediaType = MediaType.APPLICATION_JSON_VALUE,
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(
						responseCode = "403",
						description = "Forbidden: Unauthorized to access the KPI field mapping"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error while fetching the KPI field mapping structure")
			})
	@GetMapping(value = "{projectBasicConfigId}/{kpiId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> fetchFieldMappingStructureByKpiFieldMappingData(
			@Parameter(
							description = "Project Basic Configuration Id",
							example = "646f1f4d4f1a2565f0e4c123",
							required = true)
					@PathVariable
					String projectBasicConfigId,
			@Parameter(description = "KPI Id", example = "kpi1", required = true) @PathVariable
					String kpiId) {
		projectBasicConfigId = CommonUtils.handleCrossScriptingTaintedValue(projectBasicConfigId);
		ServiceResponse response = null;
		boolean hasProjectAccess = projectAccessUtil.configIdHasUserAccess(projectBasicConfigId);
		if (!hasProjectAccess) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(
							new ServiceResponse(
									false, "Unauthorized to get the kpi field mapping", "Unauthorized"));
		}
		FieldMappingStructureResponse result =
				kPIHelperService.fetchFieldMappingStructureByKpiId(projectBasicConfigId, kpiId);

		if (result == null) {
			response = new ServiceResponse(false, "no field mapping stucture found", null);
		} else {
			if (StringUtils.isNotEmpty(result.getProjectToolConfigId())) {
				result.setKpiSource(
						kPIHelperService.updateKPISource(
								new ObjectId(projectBasicConfigId), new ObjectId(result.getProjectToolConfigId())));
				response = new ServiceResponse(true, "field mapping stucture", result);
			} else {
				response = new ServiceResponse(true, "Tool Source Absent", result);
			}
		}

		return ResponseEntity.status(HttpStatus.OK).body(response);
	}
}
