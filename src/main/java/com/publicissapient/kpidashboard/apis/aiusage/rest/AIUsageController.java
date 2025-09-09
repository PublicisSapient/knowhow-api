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

package com.publicissapient.kpidashboard.apis.aiusage.rest;

import com.publicissapient.kpidashboard.apis.aiusage.rest.contract.AIUsageAPI;
import com.publicissapient.kpidashboard.apis.aiusage.dto.InitiateUploadRequest;
import com.publicissapient.kpidashboard.apis.aiusage.dto.InputAIUsage;
import com.publicissapient.kpidashboard.apis.aiusage.dto.UploadStatusResponse;
import com.publicissapient.kpidashboard.apis.aiusage.service.AIUsageService;
import jakarta.ws.rs.BadRequestException;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@RestController
@AllArgsConstructor
public class AIUsageController implements AIUsageAPI {

    private final AIUsageService aiUsageService;

    @Override
    public InitiateUploadRequest uploadAiUsageData(@RequestBody InputAIUsage inputAIUsage) {
        UUID requestId = UUID.randomUUID();
        Instant submittedAt = Instant.now();
        return aiUsageService.uploadFile(inputAIUsage.filePath(), requestId, submittedAt);
    }

    @Override
    public InitiateUploadRequest uploadAiUsageDataFile(@RequestPart MultipartFile file) {
        UUID requestId = UUID.randomUUID();
        Instant submittedAt = Instant.now();

        String filePath;
        try {
            Path tempFile = Files.createTempFile("ai_usage_", "_" + file.getOriginalFilename());
            file.transferTo(tempFile.toFile()); 
            filePath = tempFile.toString();
        } catch (IOException e) {
            throw new BadRequestException("Failed to process the uploaded file: " + e.getMessage());
        }

        return aiUsageService.uploadFile(filePath, requestId, submittedAt);
    }

    @Override
    public UploadStatusResponse getProcessingStatus(@PathVariable UUID requestId) {
        return aiUsageService.getProcessingStatus(requestId);
    }
}

