package com.publicissapient.kpidashboard.apis.appsetting.service;

import java.util.Objects;

import com.publicissapient.kpidashboard.apis.model.ApplicationConfigDto;
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

        log.info("Application configuration retrieved successfully");
        return config;
    }
}