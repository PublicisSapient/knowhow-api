package com.publicissapient.kpidashboard.apis.aiusage.service;

import com.publicissapient.kpidashboard.apis.aiusage.dto.InitiateUploadResponse;
import com.publicissapient.kpidashboard.apis.aiusage.dto.UploadAIUsageRequest;
import com.publicissapient.kpidashboard.apis.aiusage.dto.UploadStatusResponse;
import com.publicissapient.kpidashboard.apis.aiusage.enums.UploadStatus;
import com.publicissapient.kpidashboard.apis.aiusage.rest.AIUsageController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AIUsageControllerTest {

    @Mock
    private AIUsageService aiUsageService;

    @InjectMocks
    private AIUsageController aiUsageController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testUploadAiUsageData_ValidPath() {
        UploadAIUsageRequest request = new UploadAIUsageRequest("valid/path.csv");
        UUID requestId = UUID.randomUUID();
        Instant submittedAt = Instant.now();
        InitiateUploadResponse response = new InitiateUploadResponse("Success", requestId, "valid/path.csv");

        when(aiUsageService.uploadFile(anyString(), any(UUID.class), any(Instant.class))).thenReturn(response);

        InitiateUploadResponse result = aiUsageController.uploadAiUsageData(request);

        assertEquals("Success", result.getMessage());
        verify(aiUsageService, times(1)).uploadFile(anyString(), any(UUID.class), any(Instant.class));
    }

    @Test
    void testUploadAiUsageData_InvalidPath() {
        UploadAIUsageRequest request = new UploadAIUsageRequest("invalid/path.csv");
        UUID requestId = UUID.randomUUID();
        Instant submittedAt = Instant.now();
        InitiateUploadResponse response = new InitiateUploadResponse("Error", requestId, "invalid/path.csv");

        when(aiUsageService.uploadFile(anyString(), any(UUID.class), any(Instant.class))).thenReturn(response);

        InitiateUploadResponse result = aiUsageController.uploadAiUsageData(request);

        assertEquals("Error", result.getMessage());
        verify(aiUsageService, times(1)).uploadFile(anyString(), any(UUID.class), any(Instant.class));
    }

    @Test
    void testUploadAiUsageDataFile_Valid() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("file.csv");
        UUID requestId = UUID.randomUUID();
        InitiateUploadResponse response = new InitiateUploadResponse("Success", requestId, "file.csv");

        when(aiUsageService.uploadFile(anyString(), any(UUID.class), any(Instant.class))).thenReturn(response);

        InitiateUploadResponse result = aiUsageController.uploadAiUsageDataFile(file);

        assertEquals("Success", result.getMessage());
        verify(aiUsageService, times(1)).uploadFile(anyString(), any(UUID.class), any(Instant.class));
    }

    @Test
    void testUploadAiUsageDataFile_Null() {
        assertThrows(NullPointerException.class, () -> aiUsageController.uploadAiUsageDataFile(null));
    }

    @Test
    void testGetProcessingStatus_ValidId() {
        UUID requestId = UUID.randomUUID();
        UploadStatusResponse response = UploadStatusResponse.builder()
                .status(UploadStatus.PROCESSING)
                .requestId(requestId)
                .submittedAt(Instant.now())
                .completedAt(null)
                .errorMessage(null)
                .failedRecords(0)
                .totalRecords(0)
                .successfulRecords(0)
                .build();

        when(aiUsageService.getProcessingStatus(any(UUID.class))).thenReturn(response);

        UploadStatusResponse result = aiUsageController.getProcessingStatus(requestId);

        assertEquals(response, result);
        verify(aiUsageService, times(1)).getProcessingStatus(any(UUID.class));
    }

    @Test
    void testGetProcessingStatus_InvalidId() {
        UUID requestId = UUID.randomUUID();

        when(aiUsageService.getProcessingStatus(any(UUID.class))).thenThrow(new IllegalArgumentException("Invalid ID"));

        assertThrows(IllegalArgumentException.class, () -> aiUsageController.getProcessingStatus(requestId));
    }
}
