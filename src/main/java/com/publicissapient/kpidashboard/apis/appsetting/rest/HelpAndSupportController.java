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

import com.publicissapient.kpidashboard.apis.appsetting.config.HelpConfig;
import com.publicissapient.kpidashboard.apis.appsetting.config.PEBConfig;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
@Tag(name = "Help", description = "Endpoints for managing support and documentation resources")
@Slf4j
public class HelpAndSupportController {

    @Autowired
    private HelpConfig helpConfig;

    public void setHelpConfig(HelpConfig helpConfig) {
        this.helpConfig = helpConfig;
    }

    /**
     * Retrieves all configured help resource URLs.
     * Returns a map containing URLs for product documentation, API documentation,
     * video tutorials, ticket raising, and support channels.
     * 
     * @return ResponseEntity containing a map of help resource names to their URLs
     */
    @GetMapping(value = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get Help Configuration",
            description = "Retrieves all configured help resource URLs including documentation, tutorials, and support channels")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved help configuration",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ServiceResponse.class))
                        })
            })
    public ResponseEntity<ServiceResponse> getHelpConfig() {
        log.info("Fetching help configuration");
        Map<String, String> resourceLinks = new LinkedHashMap<>();
        resourceLinks.put("productDocumentation", helpConfig.getProductDocumentationUrl() != null ? helpConfig.getProductDocumentationUrl() : "");
        resourceLinks.put("apiDocumentation", helpConfig.getApiDocumentationUrl() != null ? helpConfig.getApiDocumentationUrl() : "");
        resourceLinks.put("videoTutorials", helpConfig.getVideoTutorialsUrl() != null ? helpConfig.getVideoTutorialsUrl() : "");
        resourceLinks.put("raiseTicket", helpConfig.getRaiseTicketUrl() != null ? helpConfig.getRaiseTicketUrl() : "");
        resourceLinks.put("supportChannel", helpConfig.getSupportChannelUrl() != null ? helpConfig.getSupportChannelUrl() : "");
        log.info("Help configuration retrieved successfully");
        return ResponseEntity.ok(new ServiceResponse(true, "Help and Support configuration retrieved successfully", resourceLinks));
    }

    /**
     * Redirects to the API documentation URL.
     * 
     * @return ResponseEntity with HTTP 302 redirect or 404 if URL is not configured
     */
    @GetMapping("/api-documentation")
    @Operation(
            summary = "Redirect to API Documentation",
            description = "Redirects to the configured API documentation URL")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "302",
                        description = "Successfully redirected to API documentation"),
                @ApiResponse(
                        responseCode = "404",
                        description = "API documentation URL is not configured")
            })
    public ResponseEntity<Void> redirectToApiDocumentation() {
        log.info("Redirecting to API documentation");
        return redirectToUrl(helpConfig.getApiDocumentationUrl(), "API documentation");
    }

    /**
     * Redirects to the product documentation URL.
     * 
     * @return ResponseEntity with HTTP 302 redirect or 404 if URL is not configured
     */
    @GetMapping("/product-documentation")
    @Operation(
            summary = "Redirect to Product Documentation",
            description = "Redirects to the configured product documentation URL")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "302",
                        description = "Successfully redirected to product documentation"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Product documentation URL is not configured")
            })
    public ResponseEntity<Void> redirectToProductDocumentation() {
        log.info("Redirecting to product documentation");
        return redirectToUrl(helpConfig.getProductDocumentationUrl(), "Product documentation");
    }

    /**
     * Redirects to the video tutorials URL.
     * 
     * @return ResponseEntity with HTTP 302 redirect or 404 if URL is not configured
     */
    @GetMapping("/video-tutorials")
    @Operation(
            summary = "Redirect to Video Tutorials",
            description = "Redirects to the configured video tutorials URL")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "302",
                        description = "Successfully redirected to video tutorials"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Video tutorials URL is not configured")
            })
    public ResponseEntity<Void> redirectToVideoTutorials() {
        log.info("Redirecting to video tutorials");
        return redirectToUrl(helpConfig.getVideoTutorialsUrl(), "Video tutorials");
    }

    /**
     * Redirects to the ticket raising URL.
     * 
     * @return ResponseEntity with HTTP 302 redirect or 404 if URL is not configured
     */
    @GetMapping("/raise-ticket")
    @Operation(
            summary = "Redirect to Raise Ticket",
            description = "Redirects to the configured ticket raising URL")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "302",
                        description = "Successfully redirected to ticket raising page"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Ticket raising URL is not configured")
            })
    public ResponseEntity<Void> redirectToRaiseTicket() {
        log.info("Redirecting to raise ticket");
        return redirectToUrl(helpConfig.getRaiseTicketUrl(), "Raise ticket");
    }

    /**
     * Redirects to the support channel URL.
     * 
     * @return ResponseEntity with HTTP 302 redirect or 404 if URL is not configured
     */
    @GetMapping("/support-channel")
    @Operation(
            summary = "Redirect to Support Channel",
            description = "Redirects to the configured support channel URL")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "302",
                        description = "Successfully redirected to support channel"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Support channel URL is not configured")
            })
    public ResponseEntity<Void> redirectToSupportChannel() {
        log.info("Redirecting to support channel");
        return redirectToUrl(helpConfig.getSupportChannelUrl(), "Support channel");
    }

    /**
     * Helper method to perform URL redirection.
     * Validates the URL and returns appropriate HTTP status.
     * 
     * @param url the URL to redirect to
     * @param resourceName the name of the resource being redirected to
     * @return ResponseEntity with HTTP 302 (Found) for valid URLs or 404 (Not Found) for invalid/empty URLs
     */
    private ResponseEntity<Void> redirectToUrl(String url, String resourceName) {
        if (url == null || url.trim().isEmpty()) {
            log.warn("{} URL is not configured", resourceName);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("Redirecting to {} URL: {}", resourceName, url);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}
