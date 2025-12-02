/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.appsetting.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.publicissapient.kpidashboard.apis.appsetting.service.ApplicationConfigService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

/**
 * Test class for ApplicationConfigController.
 *
 * @author Publicis Sapient
 */
@ExtendWith(MockitoExtension.class)
class ApplicationConfigControllerTest {

	@InjectMocks
	private ApplicationConfigController applicationConfigController;

	@Mock
	private ApplicationConfigService applicationConfigService;

	private Map<String, Object> economicBenefitsConfig;
	private Map<String, Object> helpConfig;

	@BeforeEach
	void setUp() {
		economicBenefitsConfig = new LinkedHashMap<>();
		economicBenefitsConfig.put("totalTeamSize", 30);
		economicBenefitsConfig.put("avgCostPerTeamMember", 100000.0);
		economicBenefitsConfig.put("timeDuration", "Per Year");

		helpConfig = new LinkedHashMap<>();
		helpConfig.put("productDocumentation", "https://docs.example.com/product");
		helpConfig.put("apiDocumentation", "https://docs.example.com/api");
		helpConfig.put("videoTutorials", "https://videos.example.com/tutorials");
		helpConfig.put("raiseTicket", "https://support.example.com/tickets");
		helpConfig.put("supportChannel", "https://chat.example.com/support");
	}

	@Test
	void getApplicationConfig_Success_ShouldReturnMergedConfiguration() {
		// Given
		when(applicationConfigService.getEconomicBenefitsConfigs()).thenReturn(economicBenefitsConfig);
		when(applicationConfigService.getHelpConfig()).thenReturn(helpConfig);

		// When
		ResponseEntity<ServiceResponse> response = applicationConfigController.getApplicationConfig();

		// Then
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		
		ServiceResponse serviceResponse = response.getBody();
		assertNotNull(serviceResponse);
		assertTrue(serviceResponse.getSuccess());
		assertEquals("Application configuration retrieved successfully. Economic benefits parameters and help resources loaded.", 
				serviceResponse.getMessage());
		
		@SuppressWarnings("unchecked")
		Map<String, Object> responseData = (Map<String, Object>) serviceResponse.getData();
		assertNotNull(responseData);
		assertEquals(8, responseData.size());
		
		// Verify economic benefits config
		assertEquals(30, responseData.get("totalTeamSize"));
		assertEquals(100000.0, responseData.get("avgCostPerTeamMember"));
		assertEquals("Per Year", responseData.get("timeDuration"));
		
		// Verify help config
		assertEquals("https://docs.example.com/product", responseData.get("productDocumentation"));
		assertEquals("https://docs.example.com/api", responseData.get("apiDocumentation"));
		assertEquals("https://videos.example.com/tutorials", responseData.get("videoTutorials"));
		assertEquals("https://support.example.com/tickets", responseData.get("raiseTicket"));
		assertEquals("https://chat.example.com/support", responseData.get("supportChannel"));
	}

	@Test
	void getApplicationConfig_WithEmptyConfigs_ShouldReturnEmptyData() {
		// Given
		when(applicationConfigService.getEconomicBenefitsConfigs()).thenReturn(new LinkedHashMap<>());
		when(applicationConfigService.getHelpConfig()).thenReturn(new LinkedHashMap<>());

		// When
		ResponseEntity<ServiceResponse> response = applicationConfigController.getApplicationConfig();

		// Then
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		
		ServiceResponse serviceResponse = response.getBody();
		assertNotNull(serviceResponse);
		assertTrue(serviceResponse.getSuccess());
		
		@SuppressWarnings("unchecked")
		Map<String, Object> responseData = (Map<String, Object>) serviceResponse.getData();
		assertNotNull(responseData);
		assertTrue(responseData.isEmpty());
	}

	@Test
	void getApplicationConfig_WithPartialData_ShouldReturnAvailableData() {
		// Given
		Map<String, Object> partialEconomicConfig = new LinkedHashMap<>();
		partialEconomicConfig.put("totalTeamSize", 50);
		
		Map<String, Object> partialHelpConfig = new LinkedHashMap<>();
		partialHelpConfig.put("productDocumentation", "https://docs.example.com/product");
		
		when(applicationConfigService.getEconomicBenefitsConfigs()).thenReturn(partialEconomicConfig);
		when(applicationConfigService.getHelpConfig()).thenReturn(partialHelpConfig);

		// When
		ResponseEntity<ServiceResponse> response = applicationConfigController.getApplicationConfig();

		// Then
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		
		ServiceResponse serviceResponse = response.getBody();
		assertNotNull(serviceResponse);
		assertTrue(serviceResponse.getSuccess());
		
		@SuppressWarnings("unchecked")
		Map<String, Object> responseData = (Map<String, Object>) serviceResponse.getData();
		assertNotNull(responseData);
		assertEquals(2, responseData.size());
		assertEquals(50, responseData.get("totalTeamSize"));
		assertEquals("https://docs.example.com/product", responseData.get("productDocumentation"));
	}
}