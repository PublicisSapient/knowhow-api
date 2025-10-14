package com.publicissapient.kpidashboard.apis.hierarchy.integeration.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.hierarchy.integration.controller.IntegrateHierarchyScheduler;
import com.publicissapient.kpidashboard.apis.hierarchy.integration.helper.ReaderRetryHelper;
import com.publicissapient.kpidashboard.apis.hierarchy.integration.service.IntegerationService;
import com.publicissapient.kpidashboard.common.repository.application.OrganizationHierarchyRepository;

@RunWith(MockitoJUnitRunner.class)
public class IntegrateHierarchySchedulerTest {

	@Mock private IntegerationService integerationService;

	@Mock private RestTemplate restTemplate;

	@Mock private ReaderRetryHelper retryHelper;

	@Mock private CustomApiConfig customApiConfig;

	@Mock private OrganizationHierarchyRepository organizationHierarchyRepository;

	@InjectMocks private IntegrateHierarchyScheduler integrateHierarchyScheduler;

	private String apiUrl = "http://test-api-url";
	private String apiKey = "test-api-key";

	@Before
	public void setUp() {
		when(customApiConfig.getCentralHierarchyUrl()).thenReturn(apiUrl);
		when(customApiConfig.getCentralHierarchyApiKey()).thenReturn(apiKey);
	}

	@Test
	public void testCallApi_SuccessfulResponse() throws Exception {
		// Arrange
		HttpHeaders headers = new HttpHeaders();
		headers.add("x-api-key", apiKey);
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> responseEntity = new ResponseEntity<>("{}", HttpStatus.OK);

		when(retryHelper.executeWithRetry(any())).thenReturn(responseEntity);
		// Act
		integrateHierarchyScheduler.callApi();
		// Assert
		verify(retryHelper, times(1)).executeWithRetry(any());
	}

	@Test
	public void testCallApi_ExceptionDuringApiCall() throws Exception {
		// Arrange
		when(retryHelper.executeWithRetry(any())).thenThrow(new RuntimeException("API call failed"));
		// Act
		integrateHierarchyScheduler.callApi();
		// Assert
		verify(retryHelper, times(1)).executeWithRetry(any());
	}
}
