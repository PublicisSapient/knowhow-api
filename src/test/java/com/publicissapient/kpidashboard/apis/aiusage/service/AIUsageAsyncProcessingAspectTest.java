package com.publicissapient.kpidashboard.apis.aiusage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.publicissapient.kpidashboard.apis.aiusage.aspect.AIUsageAsyncProcessingAspect;
import com.publicissapient.kpidashboard.apis.aiusage.config.AIUsageFileFormat;
import com.publicissapient.kpidashboard.apis.aiusage.dto.InitiateUploadResponse;
import com.publicissapient.kpidashboard.apis.aiusage.enums.RequiredHeaders;
import com.publicissapient.kpidashboard.apis.aiusage.enums.UploadStatus;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsage;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsageRequest;

@ExtendWith(MockitoExtension.class)
class AIUsageAsyncProcessingAspectTest {
	@Mock private MongoTemplate mongoTemplate;

	@InjectMocks private AIUsageAsyncProcessingAspect aiUsageAsyncProcessingAspect;

	private Method setValuesInDocumentMethod;

	private Method createAIUsageFromColumnsMethod;

	private AIUsage aiUsage;

	@Mock private AIUsageService aiUsageService;

	@Mock private AIUsageFileFormat aiUsageFileFormat;

	@BeforeEach
	void setUp() throws NoSuchMethodException {
		setValuesInDocumentMethod =
				AIUsageAsyncProcessingAspect.class.getDeclaredMethod(
						"setValuesInDocument",
						AtomicInteger.class,
						AIUsageRequest.class,
						String.class,
						AIUsage.class,
						String.class);
		setValuesInDocumentMethod.setAccessible(true);

		aiUsage = new AIUsage();
		aiUsage.setEmail("test@example.com");
		aiUsage.setBusinessUnit("Finance");
		aiUsage.setPromptCount(5);
		aiUsage.setAccount("Account1");
		aiUsage.setVertical("Vertical1");

		createAIUsageFromColumnsMethod =
				AIUsageAsyncProcessingAspect.class.getDeclaredMethod(
						"createAIUsageFromColumns",
						String[].class,
						Map.class,
						Map.class,
						AtomicInteger.class,
						AIUsageRequest.class);
		createAIUsageFromColumnsMethod.setAccessible(true);
	}

	@Test
	void testSetValuesInDocument_ValidField() throws Exception {
		AtomicInteger failedRecordsCount = new AtomicInteger(0);
		AIUsageRequest uploadStatus = new AIUsageRequest();
		aiUsage = new AIUsage();
		String mappedField = "email"; // Assume this is a valid field in RequiredHeaders
		String value = "test@example.com";

		setValuesInDocumentMethod.invoke(
				null, failedRecordsCount, uploadStatus, mappedField, aiUsage, value);

		assertEquals("test@example.com", aiUsage.getEmail());
		assertEquals(0, failedRecordsCount.get());
	}

	@Test
	void testSetValuesInDocument_InvalidField() throws Exception {
		AtomicInteger failedRecordsCount = new AtomicInteger(0);
		AIUsageRequest uploadStatus = new AIUsageRequest();
		aiUsage = new AIUsage();
		String mappedField = "invalidField";
		String value = "test";

		setValuesInDocumentMethod.invoke(
				null, failedRecordsCount, uploadStatus, mappedField, aiUsage, value);

		assertEquals(1, failedRecordsCount.get());
	}

	@Test
	void testSetValuesInDocument_Exception() throws Exception {
		AtomicInteger failedRecordsCount = new AtomicInteger(0);
		AIUsageRequest uploadStatus = new AIUsageRequest();
		aiUsage = new AIUsage();
		String mappedField = "promptCount";
		String value = null;

		setValuesInDocumentMethod.invoke(
				null, failedRecordsCount, uploadStatus, mappedField, aiUsage, value);

		assertEquals(UploadStatus.FAILED, uploadStatus.getStatus());
		assertEquals(1, failedRecordsCount.get());
	}

	@Test
	void testUpsertAIUsage_UsingReflection() throws Exception {
		Method method =
				AIUsageAsyncProcessingAspect.class.getDeclaredMethod("upsertAIUsage", AIUsage.class);
		method.setAccessible(true);

		method.invoke(aiUsageAsyncProcessingAspect, aiUsage);

		Query expectedQuery =
				new Query(
						Criteria.where(RequiredHeaders.EMAIL.getName())
								.is(aiUsage.getEmail())
								.and(RequiredHeaders.BUSINESS_UNIT.getName())
								.is(aiUsage.getBusinessUnit()));

		Update expectedUpdate =
				new Update()
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

		aiUsage =
				(AIUsage)
						createAIUsageFromColumnsMethod.invoke(
								aiUsageAsyncProcessingAspect,
								columns,
								columnIndexMap,
								headerMapping,
								failedRecordsCount,
								uploadStatus);

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
		headerMapping.put("account", "account"); // Missing in columns

		AtomicInteger failedRecordsCount = new AtomicInteger(0);
		AIUsageRequest uploadStatus = new AIUsageRequest();

		createAIUsageFromColumnsMethod.invoke(
				aiUsageAsyncProcessingAspect,
				columns,
				columnIndexMap,
				headerMapping,
				failedRecordsCount,
				uploadStatus);

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
				aiUsageAsyncProcessingAspect,
				columns,
				columnIndexMap,
				headerMapping,
				failedRecordsCount,
				uploadStatus);

		assertEquals(1, failedRecordsCount.get()); // Invalid number format
	}

	@Test
	void shouldProcessCsvFileSuccessfully() throws Exception {
		// given
		Path tempFile = Files.createTempFile("aiusage", ".csv");
		try (FileWriter writer = new FileWriter(tempFile.toFile())) {

			writer.write("email,businessUnit,promptCount,vertical,account\n");
			writer.write("user@test.com,BU1,5,V1,A1\n");
		}

		UUID requestId = UUID.randomUUID();
		AIUsageRequest request = new AIUsageRequest();
		request.setRequestId(String.valueOf(requestId));
		request.setStatus(UploadStatus.PENDING);

		when(aiUsageService.findByRequestId(requestId)).thenReturn(request);

		when(aiUsageFileFormat.getHeaderToMappingMap())
				.thenReturn(
						Map.of(
								"email",
								"email",
								"businessUnit",
								"businessUnit",
								"promptCount",
								"promptCount",
								"vertical",
								"vertical",
								"account",
								"account"));

		InitiateUploadResponse response =
				new InitiateUploadResponse("", requestId, tempFile.toString());

		// when
		aiUsageAsyncProcessingAspect.processFileAsync(response);

		await()
				.atMost(5, TimeUnit.SECONDS)
				.untilAsserted(
						() -> {
							ArgumentCaptor<AIUsageRequest> captor = ArgumentCaptor.forClass(AIUsageRequest.class);
							verify(aiUsageService, atLeastOnce()).saveUploadStatus(captor.capture());

							AIUsageRequest saved = captor.getValue();
							assertThat(saved.getStatus()).isEqualTo(UploadStatus.COMPLETED);
							assertThat(saved.getSuccessfulRecords()).isEqualTo(1);
						});
	}
}
