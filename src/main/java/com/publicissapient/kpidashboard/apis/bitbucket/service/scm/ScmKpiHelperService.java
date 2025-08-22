package com.publicissapient.kpidashboard.apis.bitbucket.service.scm;

import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import org.bson.types.ObjectId;

import java.util.List;

public interface ScmKpiHelperService {
    List<ScmCommits> getCommitDetails(ObjectId projectBasicConfigId, CustomDateRange dateRange);
    List<ScmMergeRequests> getMergeRequests(ObjectId projectBasicConfigId, CustomDateRange dateRange);
    List<Assignee> getScmUsers(ObjectId projectBasicConfigId);

}
