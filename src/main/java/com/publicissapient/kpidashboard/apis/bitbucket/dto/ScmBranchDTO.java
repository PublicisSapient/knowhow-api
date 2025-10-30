package com.publicissapient.kpidashboard.apis.bitbucket.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScmBranchDTO {
    private String branchName;
    private long lastUpdatedTimestamp;
    private int order;
}
