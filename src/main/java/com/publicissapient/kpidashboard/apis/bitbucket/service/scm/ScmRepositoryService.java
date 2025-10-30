package com.publicissapient.kpidashboard.apis.bitbucket.service.scm;

import com.publicissapient.kpidashboard.apis.bitbucket.dto.ScmRepositoryDTO;
import org.bson.types.ObjectId;

import java.util.List;

public interface ScmRepositoryService {
    List<ScmRepositoryDTO> getScmRepositoryListByConnectionId(ObjectId connectionId);
    boolean triggerScmReposFetcher(String connectionId);
}
