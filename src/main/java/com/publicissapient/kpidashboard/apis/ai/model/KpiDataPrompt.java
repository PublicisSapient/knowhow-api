package com.publicissapient.kpidashboard.apis.ai.model;

import lombok.Data;

@Data
public class KpiDataPrompt {
    private String data;
    private String sProjectName;
    private String sSprintName;
    private String date;
}
