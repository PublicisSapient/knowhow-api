package com.publicissapient.kpidashboard.apis.model;
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
}

