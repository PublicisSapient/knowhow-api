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

package com.publicissapient.kpidashboard.apis.aiusage.rest.contract;

import com.publicissapient.kpidashboard.apis.aiusage.dto.LatestAIUsageResponse;
import com.publicissapient.kpidashboard.apis.aiusage.dto.UploadStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpServerErrorException;

@RequestMapping("/ai-usage")
public interface AIUsageAPI {

    @GetMapping("/latest")
    @Operation(summary = "Get the last values for the available metrics by email address",
            description = "Fetches the most recent AI usage metric values for a specified email address.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Latest metric values retrieved successfully",
                            content = @Content(schema = @Schema(implementation = UploadStatusResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal server error",
                            content = @Content(schema = @Schema(implementation = HttpServerErrorException.InternalServerError.class))
                    )
            }
    )
    LatestAIUsageResponse getProcessingStatusFromSharedDataService(
            @Parameter(description = "Email Address", required = true) @RequestParam String email);
}

