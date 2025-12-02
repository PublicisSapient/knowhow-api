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

package com.publicissapient.kpidashboard.apis.appsetting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.appsetting.config.HelpConfig;
import com.publicissapient.kpidashboard.apis.appsetting.config.PEBConfig;

@ExtendWith(MockitoExtension.class)
class ApplicationConfigServiceImplTest {

	private ApplicationConfigServiceImpl applicationConfigService;
	private PEBConfig pebConfig;
	private HelpConfig helpConfig;

	@BeforeEach
	void setUp() {
		pebConfig = new PEBConfig();
		helpConfig = new HelpConfig();
		applicationConfigService = new ApplicationConfigServiceImpl(pebConfig, helpConfig);
	}

	@Test
	void getEconomicBenefitsConfigs_WithConfiguredValues_ShouldReturnConfiguredValues() {
		// Given
		pebConfig.setTotalDevelopers(50);
		pebConfig.setAvgCostPerDeveloper(120000.0);
		pebConfig.setTimeDuration("Per Month");

		// When
		Map<String, Object> result = applicationConfigService.getEconomicBenefitsConfigs();

		// Then
		assertNotNull(result);
		assertEquals(50, result.get("totalTeamSize"));
		assertEquals(120000.0, result.get("avgCostPerTeamMember"));
		assertEquals("Per Month", result.get("timeDuration"));
	}

	@Test
	void getEconomicBenefitsConfigs_WithNullValues_ShouldReturnDefaultValues() {
		// Given
		pebConfig.setTotalDevelopers(null);
		pebConfig.setAvgCostPerDeveloper(null);
		pebConfig.setTimeDuration(null);

		// When
		Map<String, Object> result = applicationConfigService.getEconomicBenefitsConfigs();

		// Then
		assertNotNull(result);
		assertEquals(30, result.get("totalTeamSize"));
		assertEquals(100000.0, result.get("avgCostPerTeamMember"));
		assertEquals("Per Year", result.get("timeDuration"));
	}

	@Test
	void getHelpConfig_WithConfiguredValues_ShouldReturnConfiguredValues() {
		// Given
		helpConfig.setProductDocumentationUrl("https://product.docs.com");
		helpConfig.setApiDocumentationUrl("https://api.docs.com");
		helpConfig.setVideoTutorialsUrl("https://videos.com");
		helpConfig.setRaiseTicketUrl("https://tickets.com");
		helpConfig.setSupportChannelUrl("https://support.com");

		// When
		Map<String, Object> result = applicationConfigService.getHelpConfig();

		// Then
		assertNotNull(result);
		assertEquals("https://product.docs.com", result.get("productDocumentation"));
		assertEquals("https://api.docs.com", result.get("apiDocumentation"));
		assertEquals("https://videos.com", result.get("videoTutorials"));
		assertEquals("https://tickets.com", result.get("raiseTicket"));
		assertEquals("https://support.com", result.get("supportChannel"));
	}

	@Test
	void getHelpConfig_WithNullValues_ShouldReturnEmptyStrings() {
		// Given
		helpConfig.setProductDocumentationUrl(null);
		helpConfig.setApiDocumentationUrl(null);
		helpConfig.setVideoTutorialsUrl(null);
		helpConfig.setRaiseTicketUrl(null);
		helpConfig.setSupportChannelUrl(null);

		// When
		Map<String, Object> result = applicationConfigService.getHelpConfig();

		// Then
		assertNotNull(result);
		assertEquals("", result.get("productDocumentation"));
		assertEquals("", result.get("apiDocumentation"));
		assertEquals("", result.get("videoTutorials"));
		assertEquals("", result.get("raiseTicket"));
		assertEquals("", result.get("supportChannel"));
	}
}