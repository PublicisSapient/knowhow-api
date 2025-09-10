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

package com.publicissapient.kpidashboard.apis.aiusage.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.publicissapient.kpidashboard.apis.aiusage.enums.UploadStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record UploadStatusResponse(
        UUID requestId,
        UploadStatus status,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss 'UTC'", timezone = "UTC")
        Instant submittedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss 'UTC'", timezone = "UTC")
        Instant completedAt,
        int totalRecords,
        int successfulRecords,
        int failedRecords,
        String errorMessage) {
}
