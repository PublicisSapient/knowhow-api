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

package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1400;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(id = "r_update_kpi_recommendation_correlation_prompt", order = "014006", author = "kunkambl", systemVersion = "14.0.0")
public class UpdateKpiRecommendationCorrelationPrompt {

    private static final String KEY = "key";
    private static final String KEY_VALUE = "kpi-recommendation";
    private static final String OUTPUT_FORMAT_KEY = "outputFormat";
    private static final String OUTPUT_FORMAT_UPDATE = "Provide a comprehensive report with:\n  1. The project health score as a percentage\n 2. Observations on significant deviations between the project and benchmark values\n 3. Provide any recommendations to address observed discrepancies and how can the project health be improved,\n  do not mention the size of dataset in this section\n 4. Translate deviations into understandable, business-friendly language.\n5.  No explanation or inline comments are required else than the required json format. \n  Format the comprehensive report in parsable json format,\n  the recommendations should be actionable and specific to the KPIs analyzed and specifically tailored\n  for USER_ROLE_PLACEHOLDER role\n  the key\n  for project health should be titled 'project_health_value'\n  and recommendation key should be 'project_recommendations',\n  in the project_recommendations key,\n  provide a list of recommendations with each recommendation should have keys kpi,correlated_kpis as a string and list of string respectively where kpi is main kpi and correlated kpis are the most dependent kpis, each element in the list will include the kpi name : correlation coefficient as percentage, recommendation and observation are value as a string and another key severity and value as a string,\n  the severity can be one of the following: low,\n  medium,\n  high,\n  critical\n the recommendation should be ordered in descending order od the severity {\n\"project_health_value\": 0,\n\"project_recommendations\": [{\n\"kpi\": \"\",\n\"observation\": \"\",\n\"correlated_kpis\": [\"\"],\n\"recommendation\": \"\",\n\"severity\": \"\"\n}],\n\"observations\": []\n}";
    private static final String OUTPUT_FORMAT_ROLLBACK = "Provide a comprehensive report with:\n  1. The project health score as a percentage\n 2. Observations on significant deviations between the project and benchmark values\n 3. Provide any recommendations to address observed discrepancies and how can the project health be improved,\n  do not mention the size of dataset in this section\n 4. Translate deviations into understandable, business-friendly language.\n5.  No explanation or inline comments are required else than the required json format. \n  Format the comprehensive report in parsable json format,\n  the recommendations should be actionable and specific to the KPIs analyzed and specifically tailored\n  for USER_ROLE_PLACEHOLDER role\n  the key\n  for project health should be titled 'project_health_value'\n  and recommendation key should be 'project_recommendations',\n  in the project_recommendations key,\n  provide a list of recommendations with each recommendation should have keys kpi,correlated_kpis as a string and list of string respectively where kpi is main kpi and correlated kpis are the most dependent kpis, recommendation and observation are value as a string and another key severity and value as a string,\n  the severity can be one of the following: low,\n  medium,\n  high,\n  critical {\n\"project_health_value\": 0,\n\"project_recommendations\": [{\n\"kpi\": \"\",\n\"observation\": \"\",\n\"correlated_kpis\": [\"\"],\n\"recommendation\": \"\",\n\"severity\": \"\"\n}],\n\"observations\": []\n}";

    private final MongoTemplate mongoTemplate;

    public UpdateKpiRecommendationCorrelationPrompt(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void execute() {
        Document updateFields = new Document("$set", new Document(OUTPUT_FORMAT_KEY, OUTPUT_FORMAT_ROLLBACK));
        mongoTemplate.getCollection("prompt_details").updateOne(new Document(KEY, KEY_VALUE), updateFields);
    }

    @RollbackExecution
    public void rollback() {
        Document updateFields = new Document("$set", new Document(OUTPUT_FORMAT_KEY, OUTPUT_FORMAT_UPDATE));
        mongoTemplate.getCollection("prompt_details").updateOne(new Document(KEY, KEY_VALUE), updateFields);
    }

}
