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

package com.publicissapient.kpidashboard.apis.kpiintegration.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.request.chat.ChatGenerationRequest;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.model.KpiDataPrompt;
import com.publicissapient.kpidashboard.apis.ai.parser.ParserStategy;
import com.publicissapient.kpidashboard.apis.ai.service.PromptGenerator;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.errors.AiGatewayServiceException;
import com.publicissapient.kpidashboard.apis.kpiintegration.service.KpiIntegrationServiceImpl;
import com.publicissapient.kpidashboard.apis.kpiintegration.service.KpiRecommendationProviderService;
import com.publicissapient.kpidashboard.apis.model.GenericKpiRecommendation;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.ProjectWiseKpiRecommendation;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.ws.rs.InternalServerErrorException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiKpiRecommendationProviderServiceImpl implements KpiRecommendationProviderService {

	@Autowired
	CustomApiConfig customApiConfig;

	@Autowired
	PromptGenerator promptGenerator;

	@Autowired
	AiGatewayClient aiGatewayClient;

	@Autowired
	KpiIntegrationServiceImpl kpiIntegrationService;

	@Autowired
    @Qualifier("AiRecommendation")
	ParserStategy<Object> parserStategy;

	private static final List<String> FILTER_LIST = Arrays.asList("Final Scope (Story Points)", "Average Coverage",
			"Story Points", "Overall");

	/**
	 * Retrieves project-wise KPI recommendations based on the provided KPI request
	 * and persona.
	 *
	 * @param kpiRequest
	 *            The request object containing details such as selected map and KPI
	 *            IDs.
	 * @param promptPersona
	 *            The persona to be used for generating recommendations.
	 * @return A list of {@link ProjectWiseKpiRecommendation} objects containing
	 *         recommendations for each project.
	 * @throws AiGatewayServiceException
	 *             if an error occurs while retrieving AI recommendations.
	 */
	@Override
	public List<ProjectWiseKpiRecommendation> getProjectWiseKpiRecommendations(KpiRequest kpiRequest,
			String promptPersona) {
		List<ProjectWiseKpiRecommendation> projectWiseKpiRecommendations;
		kpiRequest.setKpiIdList(customApiConfig.getAiRecommendationKpiList());
		kpiRequest.getSelectedMap().put(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT, new ArrayList<>());
		try {
			Map<String, Object> kpiDataMap = extractKpiData(kpiRequest);
			String prompt = promptGenerator.getKpiRecommendationPrompt(kpiDataMap, promptPersona);
			ChatGenerationRequest chatGenerationRequest = ChatGenerationRequest.builder().prompt(prompt).build();
			ChatGenerationResponseDTO chatResponse = aiGatewayClient.generate(chatGenerationRequest);
			Object responseObject = parserStategy.parse(chatResponse.content());
			projectWiseKpiRecommendations = buildProjectWiseRecommendations(kpiRequest, responseObject);
		} catch (Exception ex) {
			log.error("Exception hitting AI Gateway", ex);
			throw new AiGatewayServiceException("Could not retrieve AI recommendations");
		}

		return projectWiseKpiRecommendations;
	}

	/**
	 * Extracts KPI data from the provided KPI request and organizes it into a map.
	 *
	 * @param kpiRequest
	 *            The request object containing KPI elements and their associated
	 *            data.
	 * @return A map where the key is the KPI name and the value is a list of
	 *         formatted KPI data prompts.
	 */
	private Map<String, Object> extractKpiData(KpiRequest kpiRequest) {
		List<KpiElement> kpiElements = kpiIntegrationService.getKpiResponses(kpiRequest);
		Map<String, Object> kpiDataMap = new HashMap<>();

		kpiElements.forEach(kpiElement -> {
			List<String> kpiDataPromptList = new ArrayList<>();
			List<?> trendValueList = (List<?>) kpiElement.getTrendValueList();

			if (CollectionUtils.isNotEmpty(trendValueList)) {
				DataCount dataCount = trendValueList.get(0) instanceof DataCountGroup
						? ((List<DataCountGroup>) trendValueList).stream()
								.filter(trend -> FILTER_LIST.contains(trend.getFilter())
										|| (FILTER_LIST.contains(trend.getFilter1())
												&& FILTER_LIST.contains(trend.getFilter2())))
								.map(DataCountGroup::getValue).flatMap(List::stream).findFirst().orElse(null)
						: ((List<DataCount>) trendValueList).get(0);

				if (dataCount != null && dataCount.getValue() instanceof List) {
					((List<DataCount>) dataCount.getValue()).forEach(dataCountItem -> {
						KpiDataPrompt kpiDataPrompt = new KpiDataPrompt();
						kpiDataPrompt.setData(dataCountItem.getData());
						kpiDataPrompt.setSProjectName(dataCountItem.getSProjectName());
						kpiDataPrompt.setSSprintName(dataCountItem.getsSprintName());
						kpiDataPrompt.setDate(dataCountItem.getDate());
						kpiDataPromptList.add(kpiDataPrompt.toString());
					});
				}
				kpiDataMap.put(kpiElement.getKpiName(), kpiDataPromptList);
			}
		});

		return kpiDataMap;
	}

	/**
	 * Builds a list of project-wise KPI recommendations based on the provided KPI
	 * request and response object.
	 *
	 * @param kpiRequest
	 *            The request object containing project and KPI details.
	 * @param responseObject
	 *            The response object containing project health and recommendations
	 *            data.
	 * @return A list containing a single {@link ProjectWiseKpiRecommendation}
	 *         object with project details and recommendations.
	 */
	private List<ProjectWiseKpiRecommendation> buildProjectWiseRecommendations(KpiRequest kpiRequest,
			Object responseObject) {
		ProjectWiseKpiRecommendation recommendation = new ProjectWiseKpiRecommendation();
		List<GenericKpiRecommendation> genericRecommendations = new ArrayList<>();
		recommendation.setProjectId(kpiRequest.getIds()[0]);
		recommendation.setProjectScore(((ObjectNode) responseObject).get("project_health_value").asDouble());
		JsonNode jsonArray = ((ObjectNode) responseObject).get("project_recommendations");
		jsonArray.forEach(jsonElement -> {
			GenericKpiRecommendation genericRecommendationItem = new GenericKpiRecommendation();
			genericRecommendationItem.setObservation(String.valueOf(jsonElement.get("observation").asText()));
			genericRecommendationItem.setRecommendationDetails(String.valueOf(jsonElement.get("recommendation").asText()));
			genericRecommendationItem.setKpiName(String.valueOf(jsonElement.get("kpi").asText()));
			genericRecommendationItem.setObservation(String.valueOf(jsonElement.get("observation").asText()));
			genericRecommendationItem.setRecommendationType(String.valueOf(jsonElement.get("severity").asText()));
			List<String> correlatedKpis = new ArrayList<>();
			for (JsonNode correlatedKpi : jsonElement.withArray("correlated_kpis")) {
				correlatedKpis.add(correlatedKpi.asText());
			}
			genericRecommendationItem.setCorrelatedKpis(correlatedKpis);
			genericRecommendations.add(genericRecommendationItem);

		});
		recommendation.setRecommendations(genericRecommendations);

		return Collections.singletonList(recommendation);
	}

}
