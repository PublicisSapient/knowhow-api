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

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.config.HelpConfig;
import com.publicissapient.kpidashboard.apis.appsetting.config.PEBConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * Service implementation for application configuration operations.
 */
@Service
@Slf4j
public class ApplicationConfigServiceImpl implements ApplicationConfigService {

    private final PEBConfig pebConfig;
    private final HelpConfig helpConfig;

    public ApplicationConfigServiceImpl(PEBConfig pebConfig, HelpConfig helpConfig) {
        this.pebConfig = pebConfig;
        this.helpConfig = helpConfig;
    }

    @Override
    public Map<String, Object> getEconomicBenefitsConfigs() {
        log.info("Fetching economic benefits configuration");
        Map<String, Object> configData = new LinkedHashMap<>();
        
        configData.put("totalTeamSize", 
            pebConfig.getTotalDevelopers() != null ? pebConfig.getTotalDevelopers() : 30);
        configData.put("avgCostPerTeamMember", 
            pebConfig.getAvgCostPerDeveloper() != null ? pebConfig.getAvgCostPerDeveloper() : 100000.00);
        configData.put("timeDuration", 
            pebConfig.getTimeDuration() != null ? pebConfig.getTimeDuration() : "Per Year");
        
        log.info("Economic benefits configuration retrieved successfully");
        return configData;
    }

    @Override
    public Map<String, Object> getHelpConfig() {
        log.info("Fetching help configuration");
        Map<String, Object> helpData = new LinkedHashMap<>();
        
        helpData.put("productDocumentation", 
            helpConfig.getProductDocumentationUrl() != null ? helpConfig.getProductDocumentationUrl() : "");
        helpData.put("apiDocumentation", 
            helpConfig.getApiDocumentationUrl() != null ? helpConfig.getApiDocumentationUrl() : "");
        helpData.put("videoTutorials", 
            helpConfig.getVideoTutorialsUrl() != null ? helpConfig.getVideoTutorialsUrl() : "");
        helpData.put("raiseTicket", 
            helpConfig.getRaiseTicketUrl() != null ? helpConfig.getRaiseTicketUrl() : "");
        helpData.put("supportChannel", 
            helpConfig.getSupportChannelUrl() != null ? helpConfig.getSupportChannelUrl() : "");
        
        log.info("Help configuration retrieved successfully");
        return helpData;
    }
}