/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

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
import com.publicissapient.kpidashboard.common.util.DateUtil;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Service
@AllArgsConstructor
public class ScmKpiHelperServiceImpl implements ScmKpiHelperService {

    private final ConfigHelperService configHelperService;

    private final KpiHelperService kpiHelperService;

    private final ScmMergeRequestRepositoryCustom scmMergeRequestsRepository;

    private final AssigneeDetailsRepository assigneeDetailsRepository;
    

	@Override
	public List<ScmCommits> getCommitDetails(ObjectId projectBasicConfigId, CustomDateRange dateRange) {
		return List.of();
	}

	@Override
	public List<ScmMergeRequests> getMergeRequests(ObjectId projectBasicConfigId, CustomDateRange dateRange) {

		Map<ObjectId, Map<String, List<Tool>>> toolMap = configHelperService.getToolItemMap();
		Map<String, List<Tool>> toolListMap = toolMap.getOrDefault(projectBasicConfigId, Map.of());
		List<Tool> scmTools = kpiHelperService.populateSCMToolsRepoList(toolListMap);
		BasicDBList mergeFilter = new BasicDBList();
		for (Tool tool : scmTools) {
			if (CollectionUtils.isEmpty(tool.getProcessorItemList()))
				continue;
			ObjectId processorItemId = tool.getProcessorItemList().get(0).getId();
			mergeFilter.add(new BasicDBObject("processorItemId", processorItemId));
		}
		return scmMergeRequestsRepository.findMergeList(
				DateUtil.localDateTimeToUTC(dateRange.getStartDate().atStartOfDay()).toInstant(ZoneOffset.UTC)
						.toEpochMilli(),
				DateUtil.localDateTimeToUTC(dateRange.getEndDate().atStartOfDay().plusDays(1)).toInstant(ZoneOffset.UTC)
						.toEpochMilli(),
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
