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

package com.publicissapient.kpidashboard.apis.ai.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class PromptGenerator {
    private static final String ANALYSIS_REPORT_PATH = "templates/prompts/analysis-report.txt";
    private static final String KPI_RECOMMENDATION_PROMPT_PATH = "templates/prompts/kpi-recommendation-prompt.txt";

    private String loadResource(String path) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + path);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }


	public String getKpiRecommendationPrompt(Map<String, Object> kpiDataByProject, String userRole) throws IOException {
		String analysisReport = loadResource(ANALYSIS_REPORT_PATH);
		String kpiRecommendationPrompt = loadResource(KPI_RECOMMENDATION_PROMPT_PATH);
		return String.format(kpiRecommendationPrompt, analysisReport, kpiDataByProject, userRole);
	}
}
