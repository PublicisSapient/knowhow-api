package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.jira.AssigneeDetails;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.repository.jira.AssigneeDetailsRepository;
import com.publicissapient.kpidashboard.common.repository.scm.ScmMergeRequestRepositoryCustom;
import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper.MILLISECONDS_IN_A_DAY;

@Service
public class ScmKpiHelperServiceImpl implements ScmKpiHelperService {

    private final ConfigHelperService configHelperService;

    private final KpiHelperService kpiHelperService;

    private final ScmMergeRequestRepositoryCustom scmMergeRequestsRepository;

    private final AssigneeDetailsRepository assigneeDetailsRepository;

    @Autowired
    public ScmKpiHelperServiceImpl(
            ConfigHelperService configHelperService,
            KpiHelperService kpiHelperService,
            ScmMergeRequestRepositoryCustom scmMergeRequestsRepository,
            AssigneeDetailsRepository assigneeDetailsRepository) {

        this.configHelperService = configHelperService;
        this.kpiHelperService = kpiHelperService;
        this.scmMergeRequestsRepository = scmMergeRequestsRepository;
        this.assigneeDetailsRepository = assigneeDetailsRepository;
    }


    @Override
    public List<ScmCommits> getCommitDetails(ObjectId projectBasicConfigId, CustomDateRange dateRange) {
        return List.of();
    }

    @Override
    public List<ScmMergeRequests> getMergeRequests(ObjectId projectBasicConfigId, CustomDateRange dateRange) {

        List<ObjectId> toolIds = new ArrayList<>();
        Map<ObjectId, Map<String, List<Tool>>> toolMap = configHelperService.getToolItemMap();
        Map<String, List<Tool>> toolListMap = toolMap.getOrDefault(projectBasicConfigId, Map.of());
        List<Tool> scmTools = kpiHelperService.populateSCMToolsRepoList(toolListMap);
        BasicDBList mergeFilter = new BasicDBList();
        for (Tool tool : scmTools) {
            if (CollectionUtils.isEmpty(tool.getProcessorItemList()))
                continue;
            ObjectId processorItemId = tool.getProcessorItemList().get(0).getId();
            toolIds.add(processorItemId);
            mergeFilter.add(new BasicDBObject("processorItemId", processorItemId));
        }
        return scmMergeRequestsRepository.findMergeList(toolIds,
                new DateTime(dateRange.getStartDateTime(), DateTimeZone.UTC).withTimeAtStartOfDay().getMillis(),
                new DateTime(dateRange.getEndDateTime(), DateTimeZone.UTC).withTimeAtStartOfDay().plus(MILLISECONDS_IN_A_DAY).getMillis(),
                mergeFilter);
    }

    @Override
    public List<Assignee> getScmUsers(ObjectId projectBasicConfigId) {
        AssigneeDetails assigneeDetails = assigneeDetailsRepository
                .findByBasicProjectConfigId(projectBasicConfigId.toString());
        Set<Assignee> assignees = assigneeDetails != null ? assigneeDetails.getAssignee() : new HashSet<>();
        return assignees.stream().toList();
    }
}
