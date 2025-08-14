/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/
package com.publicissapient.kpidashboard.apis.executive.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketServiceKanbanR;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsServiceKanbanR;
import com.publicissapient.kpidashboard.apis.jira.service.JiraServiceKanbanR;
import com.publicissapient.kpidashboard.apis.kpiintegration.service.KpiIntegrationServiceImpl;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.sonar.service.SonarServiceKanbanR;
import com.publicissapient.kpidashboard.apis.zephyr.service.ZephyrServiceKanban;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class KanbanKpiMaturity implements ToolKpiMaturity {
	private final KpiIntegrationServiceImpl kpiIntegrationServiceImpl;
	private final JiraServiceKanbanR jiraService;
	private final SonarServiceKanbanR sonarService;
	private final ZephyrServiceKanban zephyrService;
	private final JenkinsServiceKanbanR jenkinsServiceR;
	private final BitBucketServiceKanbanR bitBucketServiceR;

	private static final String KPI_SOURCE_JIRA = "Jira";
	private static final String KPI_SOURCE_SONAR = "Sonar";
	private static final String KPI_SOURCE_ZEPHYR = "Zypher";
	private static final String KPI_SOURCE_JENKINS = "Jenkins";
	private static final String KPI_SOURCE_DEVELOPER = "BitBucket";

	@Override
	public List<KpiElement> getKpiElements(KpiRequest kpiRequest, Map<String, List<KpiMaster>> sourceWiseKpiList) {

		List<KpiElement> kpiElements = new ArrayList<>();
		sourceWiseKpiList.forEach((source, kpiList) -> {
			try {
				kpiRequest.setKpiList(sourceWiseKpiList.get(source).stream()
						.map(kpiIntegrationServiceImpl::mapKpiMasterToKpiElement).toList());
				switch (source) {
				case KPI_SOURCE_JIRA:
					kpiElements.addAll(jiraService.process(kpiRequest));
					break;
				case KPI_SOURCE_SONAR:
					kpiElements.addAll(sonarService.process(kpiRequest));
					break;
				case KPI_SOURCE_ZEPHYR:
					kpiElements.addAll(zephyrService.process(kpiRequest));
					break;
				case KPI_SOURCE_JENKINS:
					kpiElements.addAll(jenkinsServiceR.process(kpiRequest));
					break;
				case KPI_SOURCE_DEVELOPER:
					kpiElements.addAll(bitBucketServiceR.process(kpiRequest));
					break;
				default:
					log.error("Invalid Kpi");
				}
			} catch (Exception ex) {
				log.error("Error while fetching kpi maturity data", ex);
			}
		});

		kpiIntegrationServiceImpl.calculateOverallMaturity(kpiElements);
		return kpiElements;
	}
}
