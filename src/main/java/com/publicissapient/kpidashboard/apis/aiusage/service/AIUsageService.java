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

package com.publicissapient.kpidashboard.apis.aiusage.service;

import com.publicissapient.kpidashboard.apis.aiusage.config.AIUsageFileFormat;
import com.publicissapient.kpidashboard.apis.aiusage.dto.InitiateUploadRequest;
import com.publicissapient.kpidashboard.apis.aiusage.dto.UploadStatusResponse;
import com.publicissapient.kpidashboard.apis.aiusage.dto.mapper.UploadStatusMapper;
import com.publicissapient.kpidashboard.apis.aiusage.enumeration.UploadStatus;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsageUploadStatus;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsage;
import com.publicissapient.kpidashboard.apis.aiusage.repository.AIUsageRepository;
import com.publicissapient.kpidashboard.apis.aiusage.repository.AIUsageUploadStatusRepository;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@AllArgsConstructor
public class AIUsageService {

    public static final String COMMA_DELIMITER = ",";

    private final AIUsageRepository aiUsageRepository;
    private final AIUsageUploadStatusRepository aiUsageUploadStatusRepository;
    private final AIUsageFileFormat aiUsageFileFormat;
    private final UploadStatusMapper uploadStatusMapper;

    @Transactional
    public InitiateUploadRequest uploadFile(@NonNull String filePath, UUID requestId, OffsetDateTime submittedAt) {
        boolean isFileValid = isValidAIUsageCSVFile(filePath);

        if (isFileValid) {
            AIUsageUploadStatus receivedStatus = AIUsageUploadStatus.builder()
                    .requestId(String.valueOf(requestId))
                    .submittedAt(submittedAt.toInstant())
                    .status(UploadStatus.PENDING)
                    .build();
            aiUsageUploadStatusRepository.save(receivedStatus);

            return new InitiateUploadRequest("File upload request accepted for processing", requestId, filePath);
        } else {
            return new InitiateUploadRequest("Error while processing the file", requestId, filePath);
        }
    }

    public UploadStatusResponse getProcessingStatus(UUID requestId) {
        AIUsageUploadStatus uploadStatus = aiUsageUploadStatusRepository.findByRequestId(String.valueOf(requestId))
                .orElseThrow(() -> new IllegalArgumentException("No upload status found for requestId: " + requestId));
        return uploadStatusMapper.mapToDto(uploadStatus);
    }

    public AIUsageUploadStatus findByRequestId(UUID requestId) {
        return aiUsageUploadStatusRepository.findByRequestId(String.valueOf(requestId)).orElseThrow(
                () -> new IllegalArgumentException("No upload status found for requestId: " + requestId));
    }

    @Transactional
    public void saveUploadStatus(AIUsageUploadStatus uploadStatus) {
        aiUsageUploadStatusRepository.save(uploadStatus);
    }

    @Transactional
    public void saveAIUsageDocument(AIUsage aiUsage) {
        aiUsageRepository.save(aiUsage);
    }

    private boolean isValidAIUsageCSVFile(@NonNull String filePath) throws IllegalArgumentException {
        File file = new File(filePath);
        if (!(file.exists() && file.canRead())) {
            throw new IllegalArgumentException("Unable to access file at location: " + filePath);
        }

        if(!filePath.endsWith(".csv")) {
            throw new IllegalArgumentException("Invalid file format. Only CSV files are accepted.");
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV file is empty.");
            }

            String[] headers = headerLine.split(COMMA_DELIMITER);
            Set<String> headerSet = new HashSet<>();
            for (String header : headers) {
                headerSet.add(header.trim());
            }

            for (String requiredHeader : aiUsageFileFormat.getRequiredHeaders()) {
                if (!headerSet.contains(requiredHeader)) {
                    throw new IllegalArgumentException("Missing required header: " + requiredHeader);
                }
            }
        } catch (IOException e) {
            log.error("Error reading CSV file: {}", e.getMessage());
        }

        return true;
    }
}
