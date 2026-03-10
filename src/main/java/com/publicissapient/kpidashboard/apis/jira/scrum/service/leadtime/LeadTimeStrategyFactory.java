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
