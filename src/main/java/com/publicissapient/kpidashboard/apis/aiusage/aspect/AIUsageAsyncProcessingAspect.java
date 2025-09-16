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
import com.publicissapient.kpidashboard.apis.aiusage.dto.InitiateUploadResponse;
import com.publicissapient.kpidashboard.apis.aiusage.enums.RequiredHeaders;
import com.publicissapient.kpidashboard.apis.aiusage.enums.UploadStatus;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsage;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsageRequest;
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
    private final MongoTemplate mongoTemplate;

    @AfterReturning(pointcut = "execution(* com.publicissapient.kpidashboard.apis.aiusage.service.AIUsageService.uploadFile(..))",
            returning = "response")
    @Transactional
    public void processFileAsync(@NotNull @Valid InitiateUploadResponse response) {
        String filePath = response.filePath();
        UUID requestId = response.requestId();

        log.info("Starting async processing for requestId: {}", requestId);

        AIUsageRequest uploadStatus = aiUsageService.findByRequestId(requestId);
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
                    int previousFailedRecordsCount = failedRecordsCount.get();
                    AIUsage aiUsage = createAIUsageFromColumns(columns, columnIndexMap, headerMapping, failedRecordsCount, uploadStatus);
                    upsertAIUsage(aiUsage);
                    if (Objects.equals(previousFailedRecordsCount, failedRecordsCount.get())) {
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
                                             Map<String, String> headerMapping, AtomicInteger failedRecordsCount,
                                             AIUsageRequest uploadStatus) {
        AIUsage aiUsage = new AIUsage();
        for (Map.Entry<String, String> entry : headerMapping.entrySet()) {
            String csvColumn = entry.getKey();
            String mappedField = entry.getValue();
            Integer index = columnIndexMap.get(csvColumn);
            if (index != null && index < columns.length) {
                String value = columns[index].trim();
                try {
                    setValuesInDocument(failedRecordsCount, uploadStatus, mappedField, aiUsage, value);
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

    private static void setValuesInDocument(AtomicInteger failedRecordsCount, AIUsageRequest uploadStatus, String mappedField, AIUsage aiUsage, String value) {
        RequiredHeaders field = RequiredHeaders.fromString(mappedField);
        if (field != null) {
            try {
                field.apply(aiUsage, value);
            } catch (Exception e) {
                uploadStatus.setStatus(UploadStatus.FAILED);
                failedRecordsCount.incrementAndGet();
            }
        } else {
            failedRecordsCount.incrementAndGet();
        }
    }

    private void upsertAIUsage(AIUsage aiUsage) {
        Query query = new Query(Criteria.where(RequiredHeaders.EMAIL.getName()).is(aiUsage.getEmail())
                .and(RequiredHeaders.BUSINESS_UNIT.getName()).is(aiUsage.getBusinessUnit()));

        Update update = new Update();
        update.set(RequiredHeaders.PROMPT_COUNT.getName(), aiUsage.getPromptCount());
        update.set(RequiredHeaders.ACCOUNT.getName(), aiUsage.getAccount());
        update.set(RequiredHeaders.VERTICAL.getName(), aiUsage.getVertical());

        mongoTemplate.upsert(query, update, AIUsage.class);
    }
}
