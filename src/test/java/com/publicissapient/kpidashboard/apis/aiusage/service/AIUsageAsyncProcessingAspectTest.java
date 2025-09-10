package com.publicissapient.kpidashboard.apis.aiusage.service;

import com.publicissapient.kpidashboard.apis.aiusage.aspect.AIUsageAsyncProcessingAspect;
import com.publicissapient.kpidashboard.apis.aiusage.config.AIUsageFileFormat;
import com.publicissapient.kpidashboard.apis.aiusage.dto.InitiateUploadResponse;
import com.publicissapient.kpidashboard.apis.aiusage.enums.UploadStatus;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIUsageAsyncProcessingAspectTest {

    @Mock
    private AIUsageService aiUsageService;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private AIUsageFileFormat aiUsageFileFormat;

    @InjectMocks
    private AIUsageAsyncProcessingAspect aiUsageAsyncProcessingAspect;

    @BeforeEach
    void setUp() {
        lenient().when(aiUsageFileFormat.getHeaderToMappingMap()).thenReturn(Map.of(
                "email", "email",
                "promptCount", "promptCount",
                "businessUnit", "businessUnit",
                "vertical", "vertical",
                "account", "account"
        ));
    }

    @Test
    void testProcessFileAsync_EmptyFile() {
        String filePath = "empty.csv";
        UUID requestId = UUID.randomUUID();
        InitiateUploadResponse request = new InitiateUploadResponse(null, requestId, filePath);
        AIUsageRequest uploadStatus = new AIUsageRequest();
        uploadStatus.setStatus(UploadStatus.PROCESSING);

        when(aiUsageService.findByRequestId(requestId)).thenReturn(uploadStatus);

        aiUsageAsyncProcessingAspect.processFileAsync(request);

        verify(aiUsageService, times(1)).saveUploadStatus(uploadStatus);
        assertEquals(UploadStatus.FAILED, uploadStatus.getStatus());
    }
}
