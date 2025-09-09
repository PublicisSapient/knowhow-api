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

package com.publicissapient.kpidashboard.apis.aiusage.dto.mapper;

import com.publicissapient.kpidashboard.apis.aiusage.dto.UploadStatusResponse;
import com.publicissapient.kpidashboard.apis.aiusage.enumeration.UploadStatus;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsageRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UploadStatusMapper {
    public UploadStatusResponse mapToDto(AIUsageRequest status) {
        return UploadStatusResponse.builder()
                .requestId(UUID.fromString(status.getRequestId()))
                .status(UploadStatus.valueOf(String.valueOf(status.getStatus())))
                .submittedAt(status.getSubmittedAt())
                .completedAt(status.getCompletedAt())
                .totalRecords(status.getTotalRecords())
                .successfulRecords(status.getSuccessfulRecords())
                .failedRecords(status.getFailedRecords())
                .errorMessage(status.getErrorMessage())
                .build();
    }
}
