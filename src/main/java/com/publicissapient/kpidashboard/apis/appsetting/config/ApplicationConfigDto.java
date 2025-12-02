package com.publicissapient.kpidashboard.apis.appsetting.config;
import lombok.Data;

@Data
public class ApplicationConfigDto {
    private Integer totalTeamSize;
    private Double avgCostPerTeamMember;
    private String timeDuration;
    private String productDocumentation;
    private String apiDocumentation;
    private String videoTutorials;
    private String raiseTicket;
    private String supportChannel;

    private String audience;
    private String baseUrl;
    private String defaultAiProvider;
}


