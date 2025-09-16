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
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.hierarchy.integeration.controller;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.hierarchy.integeration.dto.HierarchyDetails;
import com.publicissapient.kpidashboard.apis.hierarchy.integeration.helper.ReaderRetryHelper;
import com.publicissapient.kpidashboard.apis.hierarchy.integeration.service.HierarchyDetailParser;
import com.publicissapient.kpidashboard.apis.hierarchy.integeration.service.IntegerationService;
import com.publicissapient.kpidashboard.apis.hierarchy.integeration.service.SF360Parser;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import com.publicissapient.kpidashboard.common.repository.application.OrganizationHierarchyRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * @author aksshriv1
 */
@Service
@Slf4j
public class IntegrateHierarchyScheduler {

	private final IntegerationService integerationService;
	private final RestTemplate restTemplate;
	private final ReaderRetryHelper retryHelper;
	private final CustomApiConfig customApiConfig;
	private final OrganizationHierarchyRepository organizationHierarchyRepository;

	public IntegrateHierarchyScheduler(IntegerationService integerationService, RestTemplate restTemplate,
			ReaderRetryHelper retryHelper, CustomApiConfig customApiConfig,
			OrganizationHierarchyRepository organizationHierarchyRepository) {
		this.integerationService = integerationService;
		this.restTemplate = restTemplate;
		this.retryHelper = retryHelper;
		this.customApiConfig = customApiConfig;
		this.organizationHierarchyRepository = organizationHierarchyRepository;
	}

	@Scheduled(cron = "${hierarchySync.cron}")
	public void callApi() {
		String apiUrl = customApiConfig.getCentralHierarchyUrl();

		HttpHeaders headers = new HttpHeaders();
		// add x-api-key
		headers.add("x-api-key", customApiConfig.getCentralHierarchyApiKey());

		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> requestEntity = new HttpEntity<>(headers);

		ReaderRetryHelper.RetryableOperation<ResponseEntity<String>> retryableOperation = () -> restTemplate
				.exchange(apiUrl, HttpMethod.GET, requestEntity, String.class);

		try {
			ResponseEntity<String> response = retryHelper.executeWithRetry(retryableOperation);
			if (response.getStatusCode().is2xxSuccessful()) {
				HierarchyDetailParser hierarchyDetailParser = new SF360Parser();
				HierarchyDetails hierarchyDetails = hierarchyDetailParser.convertToHierachyDetail(response.getBody());
				// Step 1: Fetch all existing records from the database
				List<OrganizationHierarchy> allDbNodes = organizationHierarchyRepository.findAll();
				Set<OrganizationHierarchy> centralHierarchies = integerationService
						.convertHieracyResponseToOrganizationHierachy(hierarchyDetails, allDbNodes);
				integerationService.syncOrganizationHierarchy(centralHierarchies, allDbNodes);
			} else {
				throw new HttpServerErrorException(response.getStatusCode(), "API call failed");
			}
		} catch (Exception e) {
			log.error("API call failed after retries. Error: {}", e.getMessage());
		}
	}

}
