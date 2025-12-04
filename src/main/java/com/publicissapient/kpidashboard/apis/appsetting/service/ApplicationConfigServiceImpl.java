/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
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

package com.publicissapient.kpidashboard.apis.appsetting.service;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;


import com.knowhow.retro.aigatewayclient.client.config.AiGatewayConfig;
import com.publicissapient.kpidashboard.apis.appsetting.config.ApplicationConfigDto;
import com.publicissapient.kpidashboard.apis.appsetting.config.HelpConfig;
import com.publicissapient.kpidashboard.apis.appsetting.config.PEBConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationConfigServiceImpl {
	private static final int DEFAULT_TEAM_SIZE = 30;
	private static final double DEFAULT_COST_PER_MEMBER = 100000.00;
	private static final String DEFAULT_TIME_DURATION = "Per Year";

	private final PEBConfig pebConfig;
	private final HelpConfig helpConfig;
	private final AiGatewayConfig aIGatewayConfig;

	public ApplicationConfigDto getApplicationConfig() {
		log.info("Fetching application configuration");

		ApplicationConfigDto config =
				ApplicationConfigDto.builder()
						.totalTeamSize(
								pebConfig.getTotalDevelopers() != null
										? pebConfig.getTotalDevelopers()
										: DEFAULT_TEAM_SIZE)
						.avgCostPerTeamMember(
								pebConfig.getAvgCostPerDeveloper() != null
										? pebConfig.getAvgCostPerDeveloper()
										: DEFAULT_COST_PER_MEMBER)
						.timeDuration(
								pebConfig.getTimeDuration() != null
										? pebConfig.getTimeDuration()
										: DEFAULT_TIME_DURATION)
						.productDocumentation(
								Objects.toString(helpConfig.getProductDocumentationUrl(), StringUtils.EMPTY))
						.apiDocumentation(
								Objects.toString(helpConfig.getApiDocumentationUrl(), StringUtils.EMPTY))
						.videoTutorials(Objects.toString(helpConfig.getVideoTutorialsUrl(), StringUtils.EMPTY))
						.raiseTicket(Objects.toString(helpConfig.getRaiseTicketUrl(), StringUtils.EMPTY))
						.supportChannel(Objects.toString(helpConfig.getSupportChannelUrl(), StringUtils.EMPTY))
						.audience(Objects.toString(aIGatewayConfig.getAudience(), StringUtils.EMPTY))
						.baseUrl(Objects.toString(aIGatewayConfig.getBaseUrl(), StringUtils.EMPTY))
						.defaultAiProvider(
								Objects.toString(aIGatewayConfig.getDefaultAiProvider(), StringUtils.EMPTY))
						.build();

		log.info("Application configuration retrieved successfully");
		return config;
	}
}
