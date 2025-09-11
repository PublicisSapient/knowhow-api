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
import com.publicissapient.kpidashboard.apis.aiusage.dto.InitiateUploadResponse;
import com.publicissapient.kpidashboard.apis.aiusage.dto.UploadStatusResponse;
import com.publicissapient.kpidashboard.apis.aiusage.dto.mapper.UploadStatusMapper;
import com.publicissapient.kpidashboard.apis.aiusage.enums.UploadStatus;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsage;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsageRequest;
import com.publicissapient.kpidashboard.apis.aiusage.repository.AIUsageRepository;
import com.publicissapient.kpidashboard.apis.aiusage.repository.AIUsageUploadStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIUsageServiceTest {

    @Mock
    private AIUsageRepository aiUsageRepository;

    @Mock
    private AIUsageUploadStatusRepository aiUsageUploadStatusRepository;

    @Mock
    private AIUsageFileFormat aiUsageFileFormat;

    @Mock
    private UploadStatusMapper uploadStatusMapper;

    @InjectMocks
    private AIUsageService aiUsageService;

    private UUID requestId;
    private Instant submittedAt;

    @BeforeEach
    void setUp() {
        requestId = UUID.randomUUID();
        submittedAt = Instant.now();
    }

    @Test
    void when_UploadFile_And_ValidInput_ExpectRequestSaved() {
        // Given
        String validFilePath = "src/test/resources/csv/valid_ai_usage.csv";
        AIUsageRequest uploadStatus = AIUsageRequest.builder()
                .requestId(String.valueOf(requestId))
                .submittedAt(submittedAt)
                .status(UploadStatus.PENDING)
                .build();

        // When
        when(aiUsageFileFormat.getExpectedHeaders()).thenReturn(List.of("email", "promptCount", "businessUnit", "vertical", "account"));

        when(aiUsageUploadStatusRepository.save(any(AIUsageRequest.class))).thenReturn(uploadStatus);

        InitiateUploadResponse result = aiUsageService.uploadFile(validFilePath, requestId, submittedAt);

        // Then
        assertNotNull(result);
        assertEquals("File upload request accepted for processing", result.getMessage());
        verify(aiUsageUploadStatusRepository, times(1)).save(any(AIUsageRequest.class));
    }

    @Test
    void when_UploadFile_And_InvalidFile_ExpectRequestFailed() {
        // Given
        String invalidFilePath = "src/test/resources/csv/invalid_ai_usage.csv";
        AIUsageRequest uploadStatus = AIUsageRequest.builder()
                .requestId(String.valueOf(requestId))
                .submittedAt(submittedAt)
                .status(UploadStatus.FAILED)
                .build();

        // When
        when(aiUsageFileFormat.getExpectedHeaders()).thenReturn(List.of("email", "promptCount", "businessUnit", "vertical", "account"));
        when(aiUsageUploadStatusRepository.save(any(AIUsageRequest.class))).thenReturn(uploadStatus);

        InitiateUploadResponse result = aiUsageService.uploadFile(invalidFilePath, requestId, submittedAt);

        // Then
        assertNotNull(result);
        assertEquals("Error while processing the file", result.getMessage());
        verify(aiUsageUploadStatusRepository, times(1)).save(any(AIUsageRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "src/test/resources/csv/invalid_ai_usage.csv",
            "src/test/resources/csv/empty.csv",
            "src/test/resources/csv/invalid_ai_usage.xlx",
            "nonexistent.csv"
    })
    void when_UploadFile_And_InvalidFilePath_expectException(String filePath) {
        lenient().when(aiUsageFileFormat.getExpectedHeaders()).thenReturn(List.of("email", "promptCount", "businessUnit", "vertical", "account"));

        AIUsageRequest receivedStatus = AIUsageRequest.builder()
                .requestId(String.valueOf(requestId))
                .submittedAt(submittedAt)
                .status(UploadStatus.FAILED)
                .build();

        InitiateUploadResponse expected = new InitiateUploadResponse("Error while processing the file", requestId, filePath);

        InitiateUploadResponse actual = aiUsageService.uploadFile(filePath, requestId, submittedAt);

        verify(aiUsageUploadStatusRepository, times(1)).save(receivedStatus);
        assertEquals(expected, actual);
    }

    @Test
    void when_GetProcessingStatus_And_ValidRequestId_expectResponse() {
        AIUsageRequest uploadStatus = new AIUsageRequest();
        when(aiUsageUploadStatusRepository.findByRequestId(String.valueOf(requestId))).thenReturn(Optional.of(uploadStatus));
        when(uploadStatusMapper.mapToDto(uploadStatus))
                .thenReturn(new UploadStatusResponse(requestId, UploadStatus.PENDING, Instant.now(), null, 0,0,0, null));

        UploadStatusResponse response = aiUsageService.getProcessingStatus(requestId);

        assertNotNull(response);
        verify(aiUsageUploadStatusRepository, times(1)).findByRequestId(String.valueOf(requestId));
    }

    @Test
    void when_GetProcessingStatus_And_InvalidRequestId_expectException() {
        UUID invalidRequestId = UUID.randomUUID();
        when(aiUsageUploadStatusRepository.findByRequestId(String.valueOf(invalidRequestId))).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> aiUsageService.getProcessingStatus(invalidRequestId));
    }

    @Test
    void when_FindByRequestId_And_ValidRequestId_expect() {
        AIUsageRequest uploadStatus = new AIUsageRequest();
        when(aiUsageUploadStatusRepository.findByRequestId(String.valueOf(requestId))).thenReturn(Optional.of(uploadStatus));

        AIUsageRequest result = aiUsageService.findByRequestId(requestId);

        assertNotNull(result);
        verify(aiUsageUploadStatusRepository, times(1)).findByRequestId(String.valueOf(requestId));
    }

    @Test
    void when_FindByRequestId_And_InvalidRequestId_expectException() {
        UUID invalidRequestId = UUID.randomUUID();
        when(aiUsageUploadStatusRepository.findByRequestId(String.valueOf(invalidRequestId))).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> aiUsageService.findByRequestId(invalidRequestId));
    }

    @Test
    void when_SaveUploadStatus_And_ValidStatus_expectSuccess() {
        AIUsageRequest uploadStatus = new AIUsageRequest();

        aiUsageService.saveUploadStatus(uploadStatus);

        verify(aiUsageUploadStatusRepository, times(1)).save(uploadStatus);
    }

    @Test
    void when_SaveAIUsageDocument_AndValidDocument_expectSavedDocument() {
        AIUsage aiUsage = new AIUsage();

        aiUsageService.saveAIUsageDocument(aiUsage);

        verify(aiUsageRepository, times(1)).save(aiUsage);
    }

    @Test
    void when_ReadCsvFile_AndValidFile_expectSuccess() throws IOException {
        // Given
        String filePath = Paths.get("src/test/resources/csv/valid_ai_usage.csv").toString();

        when(aiUsageFileFormat.getRequiredHeaders()).thenReturn(List.of("email", "promptCount", "businessUnit", "vertical", "account"));

        String expectedHeader = String.join(",", aiUsageFileFormat.getRequiredHeaders());

        // When
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String headerLine = reader.readLine();

            // Then
            assertEquals(expectedHeader, headerLine);
        }
    }

    @Test
    void when_UploadFile_And_NullFilePath_expectException() {
        assertThrows(NullPointerException.class, () -> {
            aiUsageService.uploadFile(null, requestId, submittedAt);
        });
    }
}
