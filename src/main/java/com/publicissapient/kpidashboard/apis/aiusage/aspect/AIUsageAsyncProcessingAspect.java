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

package com.publicissapient.kpidashboard.apis.aiusage.aspect;

import com.publicissapient.kpidashboard.apis.aiusage.config.AIUsageFileFormat;
import com.publicissapient.kpidashboard.apis.aiusage.dto.InitiateUploadRequest;
import com.publicissapient.kpidashboard.apis.aiusage.enumeration.UploadStatus;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsage;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsageUploadStatus;
import com.publicissapient.kpidashboard.apis.aiusage.service.AIUsageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Aspect
@Component
@Slf4j
@AllArgsConstructor
@Validated
public class AIUsageAsyncProcessingAspect {
    public static final String COMMA_DELIMITER = ",";

    private final AIUsageService aiUsageService;
    private final AIUsageFileFormat aiUsageFileFormat;
    private MongoTemplate mongoTemplate;

    @AfterReturning(pointcut = "execution(* com.publicissapient.kpidashboard.apis.aiusage.service.AIUsageService.uploadFile(..))",
            returning = "response")
    @Transactional
    public void processFileAsync(@NotNull @Valid InitiateUploadRequest response) {
        String filePath = response.filePath();
        UUID requestId = UUID.fromString(response.requestId());

        log.info("Starting async processing for requestId: {}", requestId);

        AIUsageUploadStatus uploadStatus = aiUsageService.findByRequestId(String.valueOf(requestId));
        uploadStatus.setStatus(UploadStatus.PROCESSING);

        Map<String, String> headerMapping = aiUsageFileFormat.getHeaderToMappingMap();

        AtomicInteger successfulRecordsCount = new AtomicInteger(0);
        AtomicInteger totalRecordsCount = new AtomicInteger(0);
        AtomicInteger failedRecordsCount = new AtomicInteger(0);

        CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                String headerLine = reader.readLine();
                if (headerLine == null) {
                    throw new IllegalArgumentException("CSV file is empty.");
                }

                String[] headers = headerLine.split(COMMA_DELIMITER);
                Map<String, Integer> columnIndexMap = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    columnIndexMap.put(headers[i].trim(), i);
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    totalRecordsCount.incrementAndGet();
                    String[] columns = line.split(COMMA_DELIMITER);
                    AtomicInteger previousFailedRecordsCount = failedRecordsCount;
                    AIUsage aiUsage = createAIUsageFromColumns(columns, columnIndexMap, headerMapping, failedRecordsCount);
                    log.info(String.valueOf(successfulRecordsCount));
                    upsertAIUsage(aiUsage);
                    if (Objects.equals(previousFailedRecordsCount.get(), failedRecordsCount.get())) {
                        successfulRecordsCount.incrementAndGet();
                    }
                }
            } catch (IOException e) {
                log.info("Error processing file for requestId: {}, {}", requestId, e.getMessage());
                uploadStatus.setStatus(UploadStatus.FAILED);
                uploadStatus.setErrorMessage(e.getMessage());
            } finally {
                log.info("Processing completed for requestId: {}", requestId);
                if (uploadStatus.getStatus() != UploadStatus.FAILED && Objects.equals(failedRecordsCount.get(), 0)) {
                    uploadStatus.setStatus(UploadStatus.COMPLETED);
                }
                uploadStatus.setCompletedAt(Instant.now());
                uploadStatus.setSuccessfulRecords(successfulRecordsCount.get());
                uploadStatus.setFailedRecords(failedRecordsCount.get());
                uploadStatus.setTotalRecords(totalRecordsCount.get());

                log.info("Final status for requestId {}: Status={}, TotalRecords={}, SuccessfulRecords={}, FailedRecords={}",
                        requestId, uploadStatus.getStatus(), uploadStatus.getTotalRecords(),
                        uploadStatus.getSuccessfulRecords(), uploadStatus.getFailedRecords());
                aiUsageService.saveUploadStatus(uploadStatus);
            }
        });
    }

    private AIUsage createAIUsageFromColumns(String[] columns, Map<String, Integer> columnIndexMap,
                                             Map<String, String> headerMapping, AtomicInteger failedRecordsCount) {
        AIUsage aiUsage = new AIUsage();
        for (Map.Entry<String, String> entry : headerMapping.entrySet()) {
            String csvColumn = entry.getKey();
            String mappedField = entry.getValue();
            Integer index = columnIndexMap.get(csvColumn);
            if (index != null && index < columns.length) {
                String value = columns[index].trim();
                try {
                    switch (mappedField) {
                        case "email":
                            aiUsage.setEmail(value);
                            break;
                        case "promptCount":
                            setPromptCount(aiUsage, value, failedRecordsCount);
                            break;
                        case "businessUnit":
                            aiUsage.setBusinessUnit(value);
                            break;
                        case "account":
                            aiUsage.setAccount(value);
                            break;
                        case "vertical":
                            aiUsage.setVertical(value);
                            break;
                        default:
                            log.error("No mapping found for field: {}", mappedField);
                            failedRecordsCount.incrementAndGet();
                    }
                } catch (NumberFormatException e) {
                    log.error("Error parsing integer for field {}: {}", mappedField, e.getMessage());
                    failedRecordsCount.incrementAndGet();
                }
            } else {
                log.error("Column {} not found in CSV.", csvColumn);
                failedRecordsCount.incrementAndGet();
            }
        }
        return aiUsage;
    }

    private void upsertAIUsage(AIUsage aiUsage) {
        Query query = new Query(Criteria.where("email").is(aiUsage.getEmail())
                .and("businessUnit").is(aiUsage.getBusinessUnit()));

        Update update = new Update();
        update.set("promptCount", aiUsage.getPromptCount());
        update.set("account", aiUsage.getAccount());
        update.set("vertical", aiUsage.getVertical());

        mongoTemplate.upsert(query, update, AIUsage.class);
    }

    private void setPromptCount(AIUsage aiUsage, String value, AtomicInteger failedRecordsCount) {
        try {
            Integer integerValue = Integer.parseInt(value);
            aiUsage.setPromptCount(integerValue);
        } catch (NumberFormatException e) {
            aiUsage.setPromptCount(null);
            failedRecordsCount.incrementAndGet();
        }
    }

}
