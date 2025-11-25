/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.appsetting.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for managing help and documentation resources.
 * Provides endpoints to retrieve help configuration and redirect to external documentation URLs.
 * 
 * @author aksshriv1
 */
@RestController
@RequestMapping("/help")
public class HelpController {

    @Value("${help.productDocumentation.url:}")
    private String productDocumentationUrl;

    @Value("${help.apiDocumentation.url:}")
    private String apiDocumentationUrl;

    @Value("${help.videoTutorials.url:}")
    private String videoTutorialsUrl;

    @Value("${help.raiseTicket.url:}")
    private String raiseTicketUrl;

    @Value("${help.supportChannel.url:}")
    private String supportChannelUrl;

    /**
     * Retrieves all configured help resource URLs.
     * Returns a map containing URLs for product documentation, API documentation,
     * video tutorials, ticket raising, and support channels.
     * 
     * @return ResponseEntity containing a map of help resource names to their URLs
     */
    @GetMapping(value = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> getHelpConfig() {
        Map<String, String> resourceLinks = new LinkedHashMap<>();
        resourceLinks.put("productDocumentation", productDocumentationUrl != null ? productDocumentationUrl : "");
        resourceLinks.put("apiDocumentation", apiDocumentationUrl != null ? apiDocumentationUrl : "");
        resourceLinks.put("videoTutorials", videoTutorialsUrl != null ? videoTutorialsUrl : "");
        resourceLinks.put("raiseTicket", raiseTicketUrl != null ? raiseTicketUrl : "");
        resourceLinks.put("supportChannel", supportChannelUrl != null ? supportChannelUrl : "");
        return ResponseEntity.ok(resourceLinks);
    }

    /**
     * Redirects to the API documentation URL.
     * 
     * @return ResponseEntity with HTTP 302 redirect or 404 if URL is not configured
     */
    @GetMapping("/api-documentation")
    public ResponseEntity<Void> redirectToApiDocumentation() {
        return redirectToUrl(apiDocumentationUrl);
    }

    /**
     * Redirects to the product documentation URL.
     * 
     * @return ResponseEntity with HTTP 302 redirect or 404 if URL is not configured
     */
    @GetMapping("/product-documentation")
    public ResponseEntity<Void> redirectToProductDocumentation() {
        return redirectToUrl(productDocumentationUrl);
    }

    /**
     * Redirects to the video tutorials URL.
     * 
     * @return ResponseEntity with HTTP 302 redirect or 404 if URL is not configured
     */
    @GetMapping("/video-tutorials")
    public ResponseEntity<Void> redirectToVideoTutorials() {
        return redirectToUrl(videoTutorialsUrl);
    }

    /**
     * Redirects to the ticket raising URL.
     * 
     * @return ResponseEntity with HTTP 302 redirect or 404 if URL is not configured
     */
    @GetMapping("/raise-ticket")
    public ResponseEntity<Void> redirectToRaiseTicket() {
        return redirectToUrl(raiseTicketUrl);
    }

    /**
     * Redirects to the support channel URL.
     * 
     * @return ResponseEntity with HTTP 302 redirect or 404 if URL is not configured
     */
    @GetMapping("/support-channel")
    public ResponseEntity<Void> redirectToSupportChannel() {
        return redirectToUrl(supportChannelUrl);
    }

    /**
     * Helper method to perform URL redirection.
     * Validates the URL and returns appropriate HTTP status.
     * 
     * @param url the URL to redirect to
     * @return ResponseEntity with HTTP 302 (Found) for valid URLs or 404 (Not Found) for invalid/empty URLs
     */
    private ResponseEntity<Void> redirectToUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}
