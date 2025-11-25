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
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Test class for HelpController.
 * 
 * @author aksshriv1
 */
@RunWith(MockitoJUnitRunner.class)
public class HelpControllerTest {

    public static final String PRODUCT_DOCUMENTATION_URL = "productDocumentationUrl";
    public static final String API_DOCUMENTATION_URL = "apiDocumentationUrl";
    public static final String VIDEO_TUTORIALS_URL = "videoTutorialsUrl";
    public static final String RAISE_TICKET_URL = "raiseTicketUrl";
    public static final String SUPPORT_CHANNEL_URL = "supportChannelUrl";
    public static final String PRODUCT = "https://docs.example.com/product";
    public static final String API = "https://docs.example.com/api";
    public static final String VIDEOS_EXAMPLE_COM = "https://videos.example.com";
    public static final String TICKET = "https://support.example.com/ticket";
    public static final String CHANNEL = "https://support.example.com/channel";
    @InjectMocks
    private HelpController helpController;

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(helpController, PRODUCT_DOCUMENTATION_URL, PRODUCT);
        ReflectionTestUtils.setField(helpController, API_DOCUMENTATION_URL, API);
        ReflectionTestUtils.setField(helpController, VIDEO_TUTORIALS_URL, VIDEOS_EXAMPLE_COM);
        ReflectionTestUtils.setField(helpController, RAISE_TICKET_URL, TICKET);
        ReflectionTestUtils.setField(helpController, SUPPORT_CHANNEL_URL, CHANNEL);
    }

    @Test
    public void testGetHelpConfig_Success() {
        ResponseEntity<Map<String, String>> response = helpController.getHelpConfig();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(5, response.getBody().size());
        assertEquals(PRODUCT, response.getBody().get("productDocumentation"));
        assertEquals(API, response.getBody().get("apiDocumentation"));
        assertEquals(VIDEOS_EXAMPLE_COM, response.getBody().get("videoTutorials"));
        assertEquals(TICKET, response.getBody().get("raiseTicket"));
        assertEquals(CHANNEL, response.getBody().get("supportChannel"));
    }

    @Test
    public void testGetHelpConfig_WithNullValues() {
        ReflectionTestUtils.setField(helpController, PRODUCT_DOCUMENTATION_URL, null);
        ReflectionTestUtils.setField(helpController, API_DOCUMENTATION_URL, null);

        ResponseEntity<Map<String, String>> response = helpController.getHelpConfig();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("", response.getBody().get("productDocumentation"));
        assertEquals("", response.getBody().get("apiDocumentation"));
    }

    @Test
    public void testRedirectToApiDocumentation_Success() {
        ResponseEntity<Void> response = helpController.redirectToApiDocumentation();

        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(URI.create(API), response.getHeaders().getLocation());
    }

    @Test
    public void testRedirectToApiDocumentation_NotFound() {
        ReflectionTestUtils.setField(helpController, API_DOCUMENTATION_URL, "");

        ResponseEntity<Void> response = helpController.redirectToApiDocumentation();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void testRedirectToProductDocumentation_Success() {
        ResponseEntity<Void> response = helpController.redirectToProductDocumentation();

        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(URI.create(PRODUCT), response.getHeaders().getLocation());
    }

    @Test
    public void testRedirectToProductDocumentation_NullUrl() {
        ReflectionTestUtils.setField(helpController, PRODUCT_DOCUMENTATION_URL, null);

        ResponseEntity<Void> response = helpController.redirectToProductDocumentation();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void testRedirectToVideoTutorials_Success() {
        ResponseEntity<Void> response = helpController.redirectToVideoTutorials();

        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(URI.create(VIDEOS_EXAMPLE_COM), response.getHeaders().getLocation());
    }

    @Test
    public void testRedirectToVideoTutorials_EmptyUrl() {
        ReflectionTestUtils.setField(helpController, VIDEO_TUTORIALS_URL, "   ");

        ResponseEntity<Void> response = helpController.redirectToVideoTutorials();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void testRedirectToRaiseTicket_Success() {
        ResponseEntity<Void> response = helpController.redirectToRaiseTicket();

        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(URI.create(TICKET), response.getHeaders().getLocation());
    }

    @Test
    public void testRedirectToRaiseTicket_NotFound() {
        ReflectionTestUtils.setField(helpController, RAISE_TICKET_URL, "");

        ResponseEntity<Void> response = helpController.redirectToRaiseTicket();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void testRedirectToSupportChannel_Success() {
        ResponseEntity<Void> response = helpController.redirectToSupportChannel();

        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(URI.create(CHANNEL), response.getHeaders().getLocation());
    }

    @Test
    public void testRedirectToSupportChannel_NotFound() {
        ReflectionTestUtils.setField(helpController, SUPPORT_CHANNEL_URL, null);

        ResponseEntity<Void> response = helpController.redirectToSupportChannel();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
