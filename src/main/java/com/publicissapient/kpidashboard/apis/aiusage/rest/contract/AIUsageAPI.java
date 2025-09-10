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

import com.publicissapient.kpidashboard.apis.aiusage.dto.InitiateUploadResponse;
import com.publicissapient.kpidashboard.apis.aiusage.dto.UploadAIUsageRequest;
import com.publicissapient.kpidashboard.apis.aiusage.dto.UploadStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.multipart.MultipartFile;

import javax.ws.rs.BadRequestException;
import java.util.UUID;

@RequestMapping("/ai-usage")
public interface AIUsageAPI {

    @Operation(
            summary = "Upload CSV file for processing AI usage data of multiple hierarchy levels",
            description = """
                    Accepts a CSV file location and processes it asynchronously, returning a request ID for tracking.
                    Predefined format (required CSV headers in the provided file): email, promptCount, businessUnit, vertical, account.
                    Custom mapping of CSV headers to expected fields can be configured in application properties.
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "202",
                            description = "File upload request accepted for processing",
                            content = @Content(schema = @Schema(implementation = InitiateUploadResponse.class),
                                    examples = @ExampleObject(
                                            name = "Accepted Response",
                                            value = "{\"requestId\":\"123e4567-e89b-12d3-a456-426614174000\"," +
                                                    " \"filePath\":\"\"/absolute/path/to/your/file.csv\"\"," +
                                                    " \"message\":\"\",Request accepted for processing\"}"
                                    ))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Bad request - Invalid input or file not accessible",
                            content = @Content(schema = @Schema(implementation = BadRequestException.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal server error",
                            content = @Content(schema = @Schema(implementation = HttpServerErrorException.InternalServerError.class))
                    )
            }
    )
    @PostMapping("/upload/path")
    InitiateUploadResponse uploadAiUsageData(
            @Parameter(description = "Absolute path to the CSV file to be processed", required = true,
                    example = "/absolute/path/to/your/file.csv")
            @RequestBody UploadAIUsageRequest filePath
    );

    @Operation(
            summary = "Upload CSV file for processing AI usage data via file upload",
            description = """
                    Accepts a CSV file via multipart upload and processes it asynchronously, returning a request ID for tracking.
                    Predefined format (required CSV headers in the provided file): email, promptCount, businessUnit, vertical, account.
                    Custom mapping of CSV headers to expected fields can be configured in application properties.
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "202",
                            description = "File upload request accepted for processing",
                            content = @Content(schema = @Schema(implementation = InitiateUploadResponse.class),
                                    examples = @ExampleObject(
                                            name = "Accepted Response",
                                            value = "{\"requestId\":\"123e4567-e89b-12d3-a456-426614174000\"," +
                                                    " \"filePath\":\"\"/absolute/path/to/your/file.csv\"\"," +
                                                    " \"message\":\"\",Request accepted for processing\"}"
                                    ))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Bad request - Invalid input or file not accessible",
                            content = @Content(schema = @Schema(implementation = BadRequestException.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal server error",
                            content = @Content(schema = @Schema(implementation = HttpServerErrorException.InternalServerError.class))
                    )
            }
    )
    @PostMapping(value = "/upload/file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    InitiateUploadResponse uploadAiUsageDataFile(
            @Parameter(description = "CSV file to be processed", required = true)
            @RequestPart("file") MultipartFile file
    );

    @Operation(
            summary = "Get processing status",
            description = "Retrieves the current processing status of a CSV upload request using the request ID.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Processing status retrieved successfully",
                            content = @Content(schema = @Schema(implementation = UploadStatusResponse.class),
                                    examples = @ExampleObject(
                                            name = "Example Response",
                                            value = "{\"requestId\":\"123e4567-e89b-12d3-a456-426614174000\"," +
                                                    " \"status\":\"PROCESSING\"," +
                                                    " \"submittedAt\":\"2024-10-01T12:00:00Z\"," +
                                                    " \"completedAt\":null," +
                                                    " \"totalRecords\":100," +
                                                    " \"successfulRecords\":50," +
                                                    " \"failedRecords\":5}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Request ID not found",
                            content = @Content(schema = @Schema(implementation = BadRequestException.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal server error",
                            content = @Content(schema = @Schema(implementation = HttpServerErrorException.InternalServerError.class))
                    )
            }
    )
    @GetMapping("/{requestId}/status")
    UploadStatusResponse getProcessingStatus(
            @Parameter(description = "Unique identifier for the upload request", required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID requestId
    );
}

