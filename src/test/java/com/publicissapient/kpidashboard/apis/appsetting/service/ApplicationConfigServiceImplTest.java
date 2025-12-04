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

import com.publicissapient.kpidashboard.apis.appsetting.config.ApplicationConfigDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.appsetting.config.HelpConfig;
import com.publicissapient.kpidashboard.apis.appsetting.config.PEBConfig;
import com.knowhow.retro.aigatewayclient.client.config.AiGatewayConfig;

@ExtendWith(MockitoExtension.class)
class ApplicationConfigServiceImplTest {

    private ApplicationConfigServiceImpl applicationConfigService;
    private PEBConfig pebConfig;
    private HelpConfig helpConfig;
    private AiGatewayConfig aiGatewayConfig;

    @BeforeEach
    void setUp() {
        pebConfig = new PEBConfig();
        helpConfig = new HelpConfig();
        aiGatewayConfig = new AiGatewayConfig();
        applicationConfigService = new ApplicationConfigServiceImpl(pebConfig, helpConfig, aiGatewayConfig);
    }

    @Test
    void getApplicationConfig_WithConfiguredValues_ShouldReturnConfiguredValues() {
        // Given
        pebConfig.setTotalDevelopers(50);
        pebConfig.setAvgCostPerDeveloper(120000.0);
        pebConfig.setTimeDuration("Per Month");

        helpConfig.setProductDocumentationUrl("https://product.docs.com");
        helpConfig.setApiDocumentationUrl("https://api.docs.com");
        helpConfig.setVideoTutorialsUrl("https://videos.com");
        helpConfig.setRaiseTicketUrl("https://tickets.com");
        helpConfig.setSupportChannelUrl("https://support.com");

        aiGatewayConfig.setAudience("test-audience");
        aiGatewayConfig.setBaseUrl("https://ai-gateway.com");
        aiGatewayConfig.setDefaultAiProvider("openai");

        // When
        ApplicationConfigDto result = applicationConfigService.getApplicationConfig();

        // Then
        assertNotNull(result);
        assertEquals(50, result.getTotalTeamSize());
        assertEquals(120000.0, result.getAvgCostPerTeamMember());
        assertEquals("Per Month", result.getTimeDuration());
        assertEquals("https://product.docs.com", result.getProductDocumentation());
        assertEquals("https://api.docs.com", result.getApiDocumentation());
        assertEquals("https://videos.com", result.getVideoTutorials());
        assertEquals("https://tickets.com", result.getRaiseTicket());
        assertEquals("https://support.com", result.getSupportChannel());
        assertEquals("test-audience", result.getAudience());
        assertEquals("https://ai-gateway.com", result.getBaseUrl());
        assertEquals("openai", result.getDefaultAiProvider());
    }

    @Test
    void getApplicationConfig_WithNullValues_ShouldReturnDefaultValues() {
        // Given
        pebConfig.setTotalDevelopers(null);
        pebConfig.setAvgCostPerDeveloper(null);
        pebConfig.setTimeDuration(null);

        helpConfig.setProductDocumentationUrl(null);
        helpConfig.setApiDocumentationUrl(null);
        helpConfig.setVideoTutorialsUrl(null);
        helpConfig.setRaiseTicketUrl(null);
        helpConfig.setSupportChannelUrl(null);

        aiGatewayConfig.setAudience(null);
        aiGatewayConfig.setBaseUrl(null);
        aiGatewayConfig.setDefaultAiProvider(null);

        // When
        ApplicationConfigDto result = applicationConfigService.getApplicationConfig();

        // Then
        assertNotNull(result);
        assertEquals(30, result.getTotalTeamSize());
        assertEquals(100000.0, result.getAvgCostPerTeamMember());
        assertEquals("Per Year", result.getTimeDuration());
        assertEquals("", result.getProductDocumentation());
        assertEquals("", result.getApiDocumentation());
        assertEquals("", result.getVideoTutorials());
        assertEquals("", result.getRaiseTicket());
        assertEquals("", result.getSupportChannel());
        assertEquals("", result.getAudience());
        assertEquals("", result.getBaseUrl());
        assertEquals("", result.getDefaultAiProvider());
    }

    @Test
    void getApplicationConfig_WithMixedValues_ShouldReturnCorrectValues() {
        // Given
        pebConfig.setTotalDevelopers(25);
        pebConfig.setAvgCostPerDeveloper(null);
        pebConfig.setTimeDuration("Per Quarter");

        helpConfig.setProductDocumentationUrl("https://docs.example.com");
        helpConfig.setApiDocumentationUrl(null);
        helpConfig.setVideoTutorialsUrl("https://tutorials.example.com");
        helpConfig.setRaiseTicketUrl(null);
        helpConfig.setSupportChannelUrl("https://support.example.com");

        aiGatewayConfig.setAudience("mixed-audience");
        aiGatewayConfig.setBaseUrl(null);
        aiGatewayConfig.setDefaultAiProvider("claude");

        // When
        ApplicationConfigDto result = applicationConfigService.getApplicationConfig();

        // Then
        assertNotNull(result);
        assertEquals(25, result.getTotalTeamSize());
        assertEquals(100000.0, result.getAvgCostPerTeamMember()); // default
        assertEquals("Per Quarter", result.getTimeDuration());
        assertEquals("https://docs.example.com", result.getProductDocumentation());
        assertEquals("", result.getApiDocumentation()); // default empty
        assertEquals("https://tutorials.example.com", result.getVideoTutorials());
        assertEquals("", result.getRaiseTicket()); // default empty
        assertEquals("https://support.example.com", result.getSupportChannel());
        assertEquals("mixed-audience", result.getAudience());
        assertEquals("", result.getBaseUrl()); // default empty
        assertEquals("claude", result.getDefaultAiProvider());
    }
}
