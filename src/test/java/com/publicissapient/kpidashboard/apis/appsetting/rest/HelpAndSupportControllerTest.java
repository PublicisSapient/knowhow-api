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

import com.publicissapient.kpidashboard.apis.appsetting.config.HelpConfig;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Test class for HelpAndSupportController.
 * 
 * @author Publicis Sapient
 */
@RunWith(MockitoJUnitRunner.class)
public class HelpAndSupportControllerTest {

    private static final String PRODUCT = "https://docs.example.com/product";
    private static final String API = "https://docs.example.com/api";
    private static final String VIDEOS_EXAMPLE_COM = "https://videos.example.com";
    private static final String TICKET = "https://support.example.com/ticket";
    private static final String CHANNEL = "https://support.example.com/channel";

    @InjectMocks
    private HelpAndSupportController helpAndSupportController;

    @Mock
    private HelpConfig helpConfig;

    @Before
    public void setUp() {
        helpConfig = HelpConfig.builder()
                .productDocumentationUrl(PRODUCT)
                .apiDocumentationUrl(API)
                .videoTutorialsUrl(VIDEOS_EXAMPLE_COM)
                .raiseTicketUrl(TICKET)
                .supportChannelUrl(CHANNEL)
                .build();

        helpAndSupportController = new HelpAndSupportController();
        helpAndSupportController.setHelpConfig(helpConfig);

    }

    @Test
    public void testGetHelpConfig_Success() {
        ResponseEntity<ServiceResponse> response = helpAndSupportController.getHelpConfig();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().getSuccess());
        Map<String, String> data = (Map<String, String>) response.getBody().getData();
        assertEquals(5, data.size());
        assertEquals(PRODUCT, data.get("productDocumentation"));
        assertEquals(API, data.get("apiDocumentation"));
        assertEquals(VIDEOS_EXAMPLE_COM, data.get("videoTutorials"));
        assertEquals(TICKET, data.get("raiseTicket"));
        assertEquals(CHANNEL, data.get("supportChannel"));
    }

    @Test
    public void testGetHelpConfig_WithNullValues() {
        helpConfig = HelpConfig.builder()
                .productDocumentationUrl(null)
                .apiDocumentationUrl(null)
                .videoTutorialsUrl(VIDEOS_EXAMPLE_COM)
                .raiseTicketUrl(TICKET)
                .supportChannelUrl(CHANNEL)
                .build();
        helpAndSupportController.setHelpConfig(helpConfig);

        ResponseEntity<ServiceResponse> response = helpAndSupportController.getHelpConfig();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, String> data = (Map<String, String>) response.getBody().getData();
        assertEquals("", data.get("productDocumentation"));
        assertEquals("", data.get("apiDocumentation"));
    }

    @Test
    public void testRedirectToApiDocumentation_Success() {
        ResponseEntity<Void> response = helpAndSupportController.redirectToApiDocumentation();

        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(URI.create(API), response.getHeaders().getLocation());
    }

    @Test
    public void testRedirectToApiDocumentation_NotFound() {
        helpConfig.setApiDocumentationUrl("");

        ResponseEntity<Void> response = helpAndSupportController.redirectToApiDocumentation();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void testRedirectToProductDocumentation_Success() {
        ResponseEntity<Void> response = helpAndSupportController.redirectToProductDocumentation();

        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(URI.create(PRODUCT), response.getHeaders().getLocation());
    }

    @Test
    public void testRedirectToProductDocumentation_NullUrl() {
        helpConfig.setProductDocumentationUrl(null);

        ResponseEntity<Void> response = helpAndSupportController.redirectToProductDocumentation();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void testRedirectToVideoTutorials_Success() {
        ResponseEntity<Void> response = helpAndSupportController.redirectToVideoTutorials();

        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(URI.create(VIDEOS_EXAMPLE_COM), response.getHeaders().getLocation());
    }

    @Test
    public void testRedirectToVideoTutorials_EmptyUrl() {
        helpConfig.setVideoTutorialsUrl("   ");

        ResponseEntity<Void> response = helpAndSupportController.redirectToVideoTutorials();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void testRedirectToRaiseTicket_Success() {
        ResponseEntity<Void> response = helpAndSupportController.redirectToRaiseTicket();

        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(URI.create(TICKET), response.getHeaders().getLocation());
    }

    @Test
    public void testRedirectToRaiseTicket_NotFound() {
        helpConfig.setRaiseTicketUrl("");

        ResponseEntity<Void> response = helpAndSupportController.redirectToRaiseTicket();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void testRedirectToSupportChannel_Success() {
        ResponseEntity<Void> response = helpAndSupportController.redirectToSupportChannel();

        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(URI.create(CHANNEL), response.getHeaders().getLocation());
    }

    @Test
    public void testRedirectToSupportChannel_NotFound() {
        helpConfig.setSupportChannelUrl(null);

        ResponseEntity<Void> response = helpAndSupportController.redirectToSupportChannel();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
