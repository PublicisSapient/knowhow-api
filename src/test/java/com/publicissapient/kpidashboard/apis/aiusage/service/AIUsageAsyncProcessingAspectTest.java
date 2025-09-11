package com.publicissapient.kpidashboard.apis.aiusage.service;

import com.publicissapient.kpidashboard.apis.aiusage.aspect.AIUsageAsyncProcessingAspect;
import com.publicissapient.kpidashboard.apis.aiusage.enums.RequiredHeaders;
import com.publicissapient.kpidashboard.apis.aiusage.enums.UploadStatus;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsage;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AIUsageAsyncProcessingAspectTest {
    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private AIUsageAsyncProcessingAspect aiUsageAsyncProcessingAspect;

    private Method setValuesInDocumentMethod;

    private Method createAIUsageFromColumnsMethod;

    private AIUsage aiUsage;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        setValuesInDocumentMethod = AIUsageAsyncProcessingAspect.class.getDeclaredMethod(
                "setValuesInDocument", AtomicInteger.class, AIUsageRequest.class, String.class, AIUsage.class, String.class);
        setValuesInDocumentMethod.setAccessible(true);

        aiUsage = new AIUsage();
        aiUsage.setEmail("test@example.com");
        aiUsage.setBusinessUnit("Finance");
        aiUsage.setPromptCount(5);
        aiUsage.setAccount("Account1");
        aiUsage.setVertical("Vertical1");

        createAIUsageFromColumnsMethod = AIUsageAsyncProcessingAspect.class.getDeclaredMethod(
                "createAIUsageFromColumns", String[].class, Map.class, Map.class, AtomicInteger.class, AIUsageRequest.class);
        createAIUsageFromColumnsMethod.setAccessible(true);
    }

    @Test
    void testSetValuesInDocument_ValidField() throws Exception {
        AtomicInteger failedRecordsCount = new AtomicInteger(0);
        AIUsageRequest uploadStatus = new AIUsageRequest();
        AIUsage aiUsage = new AIUsage();
        String mappedField = "email"; // Assume this is a valid field in RequiredHeaders
        String value = "test@example.com";

        setValuesInDocumentMethod.invoke(null, failedRecordsCount, uploadStatus, mappedField, aiUsage, value);

        assertEquals("test@example.com", aiUsage.getEmail());
        assertEquals(0, failedRecordsCount.get());
    }

    @Test
    void testSetValuesInDocument_InvalidField() throws Exception {
        AtomicInteger failedRecordsCount = new AtomicInteger(0);
        AIUsageRequest uploadStatus = new AIUsageRequest();
        AIUsage aiUsage = new AIUsage();
        String mappedField = "invalidField";
        String value = "test";

        setValuesInDocumentMethod.invoke(null, failedRecordsCount, uploadStatus, mappedField, aiUsage, value);

        assertEquals(1, failedRecordsCount.get());
    }

    @Test
    void testSetValuesInDocument_Exception() throws Exception {
        AtomicInteger failedRecordsCount = new AtomicInteger(0);
        AIUsageRequest uploadStatus = new AIUsageRequest();
        AIUsage aiUsage = new AIUsage();
        String mappedField = "promptCount";
        String value = null;

        setValuesInDocumentMethod.invoke(null, failedRecordsCount, uploadStatus, mappedField, aiUsage, value);

        assertEquals(UploadStatus.FAILED, uploadStatus.getStatus());
        assertEquals(1, failedRecordsCount.get());
    }

    @Test
    void testUpsertAIUsage_UsingReflection() throws Exception {
        Method method = AIUsageAsyncProcessingAspect.class.getDeclaredMethod("upsertAIUsage", AIUsage.class);
        method.setAccessible(true);

        method.invoke(aiUsageAsyncProcessingAspect, aiUsage);

        Query expectedQuery = new Query(Criteria.where(RequiredHeaders.EMAIL.getName()).is(aiUsage.getEmail())
                .and(RequiredHeaders.BUSINESS_UNIT.getName()).is(aiUsage.getBusinessUnit()));

        Update expectedUpdate = new Update()
                .set(RequiredHeaders.PROMPT_COUNT.getName(), aiUsage.getPromptCount())
                .set(RequiredHeaders.ACCOUNT.getName(), aiUsage.getAccount())
                .set(RequiredHeaders.VERTICAL.getName(), aiUsage.getVertical());

        verify(mongoTemplate).upsert(expectedQuery, expectedUpdate, AIUsage.class);
    }

    @Test
    void testCreateAIUsageFromColumns_ValidData() throws Exception {
        String[] columns = {"test@example.com", "5", "Finance", "Vertical1", "Account1"};
        Map<String, Integer> columnIndexMap = new HashMap<>();
        columnIndexMap.put("email", 0);
        columnIndexMap.put("promptCount", 1);
        columnIndexMap.put("businessUnit", 2);
        columnIndexMap.put("vertical", 3);
        columnIndexMap.put("account", 4);

        Map<String, String> headerMapping = new HashMap<>();
        headerMapping.put("email", "email");
        headerMapping.put("promptCount", "promptCount");
        headerMapping.put("businessUnit", "businessUnit");
        headerMapping.put("vertical", "vertical");
        headerMapping.put("account", "account");

        AtomicInteger failedRecordsCount = new AtomicInteger(0);
        AIUsageRequest uploadStatus = new AIUsageRequest();

        AIUsage aiUsage = (AIUsage) createAIUsageFromColumnsMethod.invoke(
                aiUsageAsyncProcessingAspect, columns, columnIndexMap, headerMapping, failedRecordsCount, uploadStatus);

        assertEquals("test@example.com", aiUsage.getEmail());
        assertEquals(5, aiUsage.getPromptCount());
        assertEquals("Finance", aiUsage.getBusinessUnit());
        assertEquals("Vertical1", aiUsage.getVertical());
        assertEquals("Account1", aiUsage.getAccount());
        assertEquals(0, failedRecordsCount.get());
    }

    @Test
    void testCreateAIUsageFromColumns_MissingColumn() throws Exception {
        String[] columns = {"test@example.com", "5", "Finance"};
        Map<String, Integer> columnIndexMap = new HashMap<>();
        columnIndexMap.put("email", 0);
        columnIndexMap.put("promptCount", 1);
        columnIndexMap.put("businessUnit", 2);

        Map<String, String> headerMapping = new HashMap<>();
        headerMapping.put("email", "email");
        headerMapping.put("promptCount", "promptCount");
        headerMapping.put("businessUnit", "businessUnit");
        headerMapping.put("vertical", "vertical"); // Missing in columns
        headerMapping.put("account", "account");   // Missing in columns

        AtomicInteger failedRecordsCount = new AtomicInteger(0);
        AIUsageRequest uploadStatus = new AIUsageRequest();

        createAIUsageFromColumnsMethod.invoke(
                aiUsageAsyncProcessingAspect, columns, columnIndexMap, headerMapping, failedRecordsCount, uploadStatus);

        assertEquals(2, failedRecordsCount.get()); // Two missing columns
    }

    @Test
    void testCreateAIUsageFromColumns_InvalidNumberFormat() throws Exception {
        String[] columns = {"test@example.com", "invalidNumber", "Finance", "Vertical1", "Account1"};
        Map<String, Integer> columnIndexMap = new HashMap<>();
        columnIndexMap.put("email", 0);
        columnIndexMap.put("promptCount", 1);
        columnIndexMap.put("businessUnit", 2);
        columnIndexMap.put("vertical", 3);
        columnIndexMap.put("account", 4);

        Map<String, String> headerMapping = new HashMap<>();
        headerMapping.put("email", "email");
        headerMapping.put("promptCount", "promptCount");
        headerMapping.put("businessUnit", "businessUnit");
        headerMapping.put("vertical", "vertical");
        headerMapping.put("account", "account");

        AtomicInteger failedRecordsCount = new AtomicInteger(0);
        AIUsageRequest uploadStatus = new AIUsageRequest();

        createAIUsageFromColumnsMethod.invoke(
                aiUsageAsyncProcessingAspect, columns, columnIndexMap, headerMapping, failedRecordsCount, uploadStatus);

        assertEquals(1, failedRecordsCount.get()); // Invalid number format
    }
}

