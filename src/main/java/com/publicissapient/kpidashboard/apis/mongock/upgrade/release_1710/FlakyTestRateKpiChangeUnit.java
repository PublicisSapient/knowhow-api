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
		id = "flaky_test_rate_kpi_insert",
		order = "17178",
		author = "knowhow",
		systemVersion = "17.1.0")
public class FlakyTestRateKpiChangeUnit {

	private static final String KPI_ID = "kpi220";
	private static final String KPI_MASTER = "kpi_master";
	private static final String KPI_COLUMN_CONFIGS = "kpi_column_configs";
	private static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		insertKpiMaster(mongoTemplate);
		insertKpiColumnConfig(mongoTemplate);
		insertFieldMappingStructure(mongoTemplate);
	}

	private void insertKpiMaster(MongoTemplate mongoTemplate) {
		Document doc =
				new Document()
						.append("kpiId", KPI_ID)
						.append("kpiName", "Flaky Test Rate")
						.append("isDeleted", "False")
						.append("defaultOrder", 6)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Quality")
						.append("kpiUnit", "%")
						.append("chartType", "line")
						.append("xAxisLabel", "Weeks")
						.append("yAxisLabel", "Percentage")
						.append("showTrend", true)
						.append("isPositiveTrend", false)
						.append("calculateMaturity", true)
						.append("maturityRange", Arrays.asList("-20", "10-20", "5-10", "2-5", "2-"))
						.append("hideOverallFilter", true)
						.append("kpiSource", "Jenkins")
						.append("combinedKpiSource", "Jenkins/Bamboo/GitHubAction/AzurePipeline/Teamcity")
						.append("kanban", false)
						.append("groupId", 72)
						.append(
								"kpiInfo",
								new Document()
										.append(
												"definition",
												"Percentage of test suites that both passed and failed in the same week. "
														+ "Healthy: <2%. Above 5% silently undermines other metrics. Lower is better."))
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

	private void insertKpiColumnConfig(MongoTemplate mongoTemplate) {
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
										col("Suite Name", 4),
										col("Total Builds", 5),
										col("Passing Runs", 6),
										col("Failing Runs", 7),
										col("Flaky", 8),
										col("Flaky Rate %", 9)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document("basicProjectConfigId", null).append("kpiId", KPI_ID),
						doc,
						new ReplaceOptions().upsert(true));
	}

	private void insertFieldMappingStructure(MongoTemplate mongoTemplate) {
		Document branchMapping =
				new Document()
						.append("fieldName", "e2eTestBranchKPI220")
						.append("fieldLabel", "Test Branch")
						.append("fieldType", "chips")
						.append("section", "Custom Fields Mapping")
						.append("processorCommon", true)
						.append(
								"tooltip",
								new Document()
										.append(
												"definition",
												"Branch name(s) to filter test suite executions on. "
														+ "Only builds on these branches are evaluated for flakiness. "
														+ "Leave blank to auto-detect from SCM tool connections. e.g. main"))
						.append("fieldDisplayOrder", 2)
						.append("sectionOrder", 5)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		Document thresholdMapping =
				new Document()
						.append("fieldName", "thresholdValueKPI220")
						.append("fieldLabel", "Target KPI Value")
						.append("fieldType", "number")
						.append("section", "Project Level Threshold")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document()
										.append(
												"definition",
												"Target flaky test rate (%). Shown as a reference line on the chart. "
														+ "Leave empty to use the default maturity line."))
						.append("fieldDisplayOrder", 1)
						.append("sectionOrder", 6)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.replaceOne(
						new Document("fieldName", "e2eTestBranchKPI220"),
						branchMapping,
						new ReplaceOptions().upsert(true));
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.replaceOne(
						new Document("fieldName", "thresholdValueKPI220"),
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
								new Document("$in", Arrays.asList("e2eTestBranchKPI220", "thresholdValueKPI220"))));
	}
}
