
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

import com.publicissapient.kpidashboard.apis.appsetting.config.AIGatewayConfig;
import com.publicissapient.kpidashboard.apis.appsetting.config.ApplicationConfigDto;
import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.publicissapient.kpidashboard.apis.appsetting.config.HelpConfig;
import com.publicissapient.kpidashboard.apis.appsetting.config.PEBConfig;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationConfigServiceImpl {

    private final PEBConfig pebConfig;
    private final HelpConfig helpConfig;
    private final AIGatewayConfig aIGatewayConfig;

    private static final int DEFAULT_TEAM_SIZE = 30;
    private static final double DEFAULT_COST_PER_MEMBER = 100000.00;
    private static final String DEFAULT_TIME_DURATION = "Per Year";

    public ApplicationConfigDto getApplicationConfig() {
        log.info("Fetching application configuration");
        ApplicationConfigDto config = new ApplicationConfigDto();

        config.setTotalTeamSize(pebConfig.getTotalDevelopers() != null ? pebConfig.getTotalDevelopers() : DEFAULT_TEAM_SIZE);
        config.setAvgCostPerTeamMember(pebConfig.getAvgCostPerDeveloper() != null ? pebConfig.getAvgCostPerDeveloper() : DEFAULT_COST_PER_MEMBER);
        config.setTimeDuration(pebConfig.getTimeDuration() != null ? pebConfig.getTimeDuration() : DEFAULT_TIME_DURATION);

        config.setProductDocumentation(Objects.toString(helpConfig.getProductDocumentationUrl(), StringUtils.EMPTY));
        config.setApiDocumentation(Objects.toString(helpConfig.getApiDocumentationUrl(), StringUtils.EMPTY));
        config.setVideoTutorials(Objects.toString(helpConfig.getVideoTutorialsUrl(), StringUtils.EMPTY));
        config.setRaiseTicket(Objects.toString(helpConfig.getRaiseTicketUrl(), StringUtils.EMPTY));
        config.setSupportChannel(Objects.toString(helpConfig.getSupportChannelUrl(), StringUtils.EMPTY));

        config.setAudience(Objects.toString(aIGatewayConfig.getAudience(), StringUtils.EMPTY));
        config.setBaseUrl(Objects.toString(aIGatewayConfig.getBaseUrl(), StringUtils.EMPTY));
        config.setDefaultAiProvider(Objects.toString(aIGatewayConfig.getDefaultAiProvider(), StringUtils.EMPTY));

        log.info("Application configuration retrieved successfully");
        return config;
    }
}