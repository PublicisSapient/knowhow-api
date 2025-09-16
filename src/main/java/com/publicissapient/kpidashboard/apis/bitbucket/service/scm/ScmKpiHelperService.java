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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm;

import java.util.List;

import com.publicissapient.kpidashboard.common.model.scm.User;
import org.bson.types.ObjectId;

import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;

public interface ScmKpiHelperService {
	List<ScmCommits> getCommitDetails(ObjectId projectBasicConfigId, CustomDateRange dateRange);

	List<ScmMergeRequests> getMergeRequests(ObjectId projectBasicConfigId, CustomDateRange dateRange);

	List<Assignee> getJiraAssigneeForScmUsers(ObjectId projectBasicConfigId);

    List<User> getScmUser(ObjectId projectBasicConfigId);

}
