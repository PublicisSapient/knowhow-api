package com.publicissapient.kpidashboard.apis.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class KpiRecommendationRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String[] ids;
    private List<String> kpiIdList;
    private String recommendationFor;
    private Map<String, List<String>> selectedMap;
    private int level;
    private String label;
    private List<String> sprintIncluded;
}
