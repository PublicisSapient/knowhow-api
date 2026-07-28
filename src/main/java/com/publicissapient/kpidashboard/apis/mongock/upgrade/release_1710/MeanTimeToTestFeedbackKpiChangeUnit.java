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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "mean_time_to_test_feedback_kpi",
		order = "17172",
		author = "knowhow",
		systemVersion = "17.1.0")
public class MeanTimeToTestFeedbackKpiChangeUnit {

	private static final String KPI_ID = "kpi219";
	private static final String KPI_MASTER = "kpi_master";
	private static final String KPI_COLUMN_CONFIGS = "kpi_column_configs";
	private static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		upsertKpiMaster(mongoTemplate);
		upsertKpiColumnConfig(mongoTemplate);
		upsertFieldMappingStructure(mongoTemplate);
	}

	private void upsertKpiMaster(MongoTemplate mongoTemplate) {
		Document doc =
				new Document()
						.append("kpiId", KPI_ID)
						.append("kpiName", "Mean Time to Test Feedback")
						.append("isDeleted", "False")
						.append("defaultOrder", 5)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Quality")
						.append("kpiUnit", "Hours")
						.append("chartType", "line")
						.append("xAxisLabel", "Weeks")
						.append("yAxisLabel", "Hours")
						.append("showTrend", true)
						.append("isPositiveTrend", false)
						.append("calculateMaturity", true)
						.append(
								"maturityRange", Arrays.asList("-1.5", "0.75-1.5", "0.5-0.75", "0.25-0.5", "0.25-"))
						.append("hideOverallFilter", true)
						.append("kpiSource", "Jenkins")
						.append("combinedKpiSource", "Jenkins/Bamboo/GitHubAction/AzurePipeline/Teamcity")
						.append("kanban", false)
						.append("groupId", 71)
						.append(
								"kpiInfo",
								new Document()
										.append(
												"definition",
												"Time from PR push to all CI / Selenium results posted back to the PR."))
						.append("kpiFilter", "dropDown")
						.append("aggregationCriteria", "average")
						.append("isTrendCalculative", false)
						.append("isAdditionalFilterSupport", false)
						.append("upperThresholdBG", "white")
						.append("lowerThresholdBG", "red")
						.append("forecastModel", "thetaMethod");

		mongoTemplate
				.getCollection(KPI_MASTER)
				.replaceOne(new Document("kpiId", KPI_ID), doc, new ReplaceOptions().upsert(true));
	}

	private void upsertKpiColumnConfig(MongoTemplate mongoTemplate) {
		Document doc =
				new Document()
						.append("basicProjectConfigId", null)
						.append("kpiId", KPI_ID)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										col("Days/Weeks", 1),
										col("Workflow", 2),
										col("Branch", 3),
										col("Total Builds", 4),
										col("Successful Builds", 5),
										col("Failed Builds", 6),
										col("Avg Duration (Hours)", 7)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document("basicProjectConfigId", null).append("kpiId", KPI_ID),
						doc,
						new ReplaceOptions().upsert(true));
	}

	private void upsertFieldMappingStructure(MongoTemplate mongoTemplate) {
		Document branchMapping =
				new Document()
						.append("fieldName", "e2eTestBranchKPI219")
						.append("fieldLabel", "CI Branch")
						.append("fieldType", "chips")
						.append("section", "Custom Fields Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document()
										.append(
												"definition",
												"Branch name(s) to filter CI builds on. Only builds on these branches are"
														+ " counted. Leave blank to auto-detect from SCM tool connections. e.g. main"))
						.append("fieldDisplayOrder", 2)
						.append("sectionOrder", 5)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		Document thresholdMapping =
				new Document()
						.append("fieldName", "thresholdValueKPI219")
						.append("fieldLabel", "Target KPI Value")
						.append("fieldType", "number")
						.append("section", "Project Level Threshold")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document()
										.append(
												"definition",
												"Target average CI pipeline duration in hours."
														+ " Shown as a reference line on the chart."
														+ " Leave empty to use the default maturity line."))
						.append("fieldDisplayOrder", 1)
						.append("sectionOrder", 6)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.replaceOne(
						new Document("fieldName", "e2eTestBranchKPI219"),
						branchMapping,
						new ReplaceOptions().upsert(true));
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.replaceOne(
						new Document("fieldName", "thresholdValueKPI219"),
						thresholdMapping,
						new ReplaceOptions().upsert(true));
	}

	private Document col(String name, int order) {
		return new Document()
				.append("columnName", name)
				.append("order", order)
				.append("isShown", true)
				.append("isDefault", true);
	}

	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		mongoTemplate.getCollection(KPI_MASTER).deleteOne(new Document("kpiId", KPI_ID));
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.deleteOne(new Document("basicProjectConfigId", null).append("kpiId", KPI_ID));
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.deleteMany(
						new Document(
								"fieldName",
								new Document("$in", Arrays.asList("e2eTestBranchKPI219", "thresholdValueKPI219"))));
	}
}
