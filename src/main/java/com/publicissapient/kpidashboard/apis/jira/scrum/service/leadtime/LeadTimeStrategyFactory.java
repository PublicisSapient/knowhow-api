/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.apis.jira.scrum.service.leadtime;

import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.common.constant.CommonConstant;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class LeadTimeStrategyFactory {

	private JiraLeadTimeStrategy jiraLeadTimeStrategy;

	private RepoLeadTimeStrategy repoLeadTimeStrategy;

	private RepoDeploymentLeadTimeStrategy repoDeploymentLeadTimeStrategy;

	public LeadTimeCalculationStrategy getStrategy(
			String leadTimeConfigRepoTool, boolean hasDeployments) {
		if (CommonConstant.REPO.equals(leadTimeConfigRepoTool) && hasDeployments) {
			return repoDeploymentLeadTimeStrategy;
		} else if (CommonConstant.REPO.equals(leadTimeConfigRepoTool)) {
			return repoLeadTimeStrategy;
		} else {
			return jiraLeadTimeStrategy;
		}
	}
}
