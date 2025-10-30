package com.publicissapient.kpidashboard.apis.bitbucket.dto;

import lombok.Builder;
import lombok.Data;
import org.bson.types.ObjectId;

import java.util.List;

@Data
@Builder
public class ScmRepositoryDTO {
    private String repositoryName;
    private String repositoryUrl;
    private ObjectId connectionId;
    private long lastUpdatedTimestamp;
    private int order;
    private List<ScmBranchDTO> branchList;
}
