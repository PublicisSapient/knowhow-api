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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.publicissapient.kpidashboard.apis.appsetting.config.HelpConfig;
import com.publicissapient.kpidashboard.apis.appsetting.config.PEBConfig;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class ApplicationConfigControllerTest {

    @Mock
    private PEBConfig pebConfig;

    @Mock
    private HelpConfig helpConfig;

    @InjectMocks
    private ApplicationConfigController applicationConfigController;

    @Before
    public void setUp() {
        when(pebConfig.getTotalDevelopers()).thenReturn(25);
        when(pebConfig.getAvgCostPerDeveloper()).thenReturn(120000.0);
        when(pebConfig.getTimeDuration()).thenReturn("Per Year");
        
        when(helpConfig.getProductDocumentationUrl()).thenReturn("https://docs.example.com/product");
        when(helpConfig.getApiDocumentationUrl()).thenReturn("https://docs.example.com/api");
        when(helpConfig.getVideoTutorialsUrl()).thenReturn("https://videos.example.com/tutorials");
        when(helpConfig.getRaiseTicketUrl()).thenReturn("https://support.example.com/tickets");
        when(helpConfig.getSupportChannelUrl()).thenReturn("https://chat.example.com/support");
    }

    @Test
    public void testGetApplicationConfig() {
        ResponseEntity<ServiceResponse> response = applicationConfigController.getApplicationConfig();
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getSuccess());
        assertEquals("Application configuration retrieved successfully. Economic benefits parameters and help resources loaded.", 
                response.getBody().getMessage());
        
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        assertEquals(25, data.get("totalTeamSize"));
        assertEquals(120000.0, data.get("avgCostPerTeamMember"));
        assertEquals("Per Year", data.get("timeDuration"));
        assertEquals("https://docs.example.com/product", data.get("productDocumentation"));
        assertEquals("https://docs.example.com/api", data.get("apiDocumentation"));
    }

    @Test
    public void testGetApplicationConfigWithNullValues() {
        when(pebConfig.getTotalDevelopers()).thenReturn(null);
        when(pebConfig.getAvgCostPerDeveloper()).thenReturn(null);
        when(pebConfig.getTimeDuration()).thenReturn(null);
        when(helpConfig.getProductDocumentationUrl()).thenReturn(null);
        
        ResponseEntity<ServiceResponse> response = applicationConfigController.getApplicationConfig();
        
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        assertEquals(30, data.get("totalTeamSize"));
        assertEquals(100000.0, data.get("avgCostPerTeamMember"));
        assertEquals("Per Year", data.get("timeDuration"));
        assertEquals("", data.get("productDocumentation"));
    }
}