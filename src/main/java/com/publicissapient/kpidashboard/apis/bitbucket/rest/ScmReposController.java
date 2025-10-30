package com.publicissapient.kpidashboard.apis.bitbucket.rest;

import com.publicissapient.kpidashboard.apis.bitbucket.dto.ScmRepositoryDTO;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmRepositoryService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/scm/config")
public class ScmReposController {

	private final ScmRepositoryService scmRepositoryService;

	public ScmReposController(ScmRepositoryService scmRepositoryService) {
		this.scmRepositoryService = scmRepositoryService;
	}

    @GetMapping("/connection/{connectionId}")
    @Operation(
            summary = "Get SCM repositories by connection ID",
            description = "Retrieves all SCM repositories associated with a specific connection ID. " +
                    "Repositories are returned sorted by last updated timestamp with the latest first."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved repositories or no repositories found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ServiceResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ServiceResponse.class)
                    )
            )
    })
    public ResponseEntity<ServiceResponse> getScmReposByConnectionId(
            @Parameter(
                    description = "MongoDB ObjectId of the connection",
                    required = true,
                    example = "507f1f77bcf86cd799439011",
                    schema = @Schema(type = "string", pattern = "^[0-9a-fA-F]{24}$")
            )
            @PathVariable("connectionId") String connectionId) {
		if (ObjectId.isValid(connectionId)) {
			List<ScmRepositoryDTO> scmRepositoryDTOList = scmRepositoryService
					.getScmRepositoryListByConnectionId(new ObjectId(connectionId));
			if (!CollectionUtils.isEmpty(scmRepositoryDTOList)) {
				ServiceResponse serviceResponse = new ServiceResponse(true, "Fetched Repositories for given connection",
						scmRepositoryDTOList);
				return ResponseEntity.ok(serviceResponse);
			}
		}
		return ResponseEntity.ok(new ServiceResponse(false, "No Repositories available for given connection", null));

    }

}