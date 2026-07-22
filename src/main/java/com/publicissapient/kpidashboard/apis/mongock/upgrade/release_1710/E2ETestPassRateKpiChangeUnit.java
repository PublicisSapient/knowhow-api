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

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "e2e_test_pass_rate_kpi_insert",
		order = "17164",
		author = "knowhow",
		systemVersion = "17.1.0")
public class E2ETestPassRateKpiChangeUnit {

	private static final String KPI_ID = "kpi218";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String FIELD_MAPPING_STRUCTURE_COLLECTION = "field_mapping_structure";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";
	private static final String FIELD_NAME = "fieldName";
	private static final String DEFINITION = "definition";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		insertKpiMaster(mongoTemplate);
		insertKpiColumnConfig(mongoTemplate);
		insertFieldMappingStructure(mongoTemplate);
	}

	public void insertKpiMaster(MongoTemplate mongoTemplate) {
		Document kpiMaster =
				new Document()
						.append(KPI_ID_FIELD, KPI_ID)
						.append("kpiName", "E2E Test Pass Rate")
						.append("isDeleted", "False")
						.append("defaultOrder", 2)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Quality")
						.append("kpiUnit", "%")
						.append("chartType", "line")
						.append("xAxisLabel", "Weeks")
						.append("yAxisLabel", "Percentage")
						.append("showTrend", true)
						.append("isPositiveTrend", true)
						.append("calculateMaturity", true)
						.append("maturityRange", Arrays.asList("-60", "60-79", "80-89", "90-94", "95-"))
						.append("hideOverallFilter", true)
						.append("kpiSource", "Jenkins")
						.append("kanban", false)
						.append("groupId", 70)
						.append(
								"kpiInfo",
								new Document()
										.append(DEFINITION, "% of automated end-to-end tests passing on main branch."))
						.append("kpiFilter", "dropDown")
						.append("aggregationCriteria", "average")
						.append("isTrendCalculative", false)
						.append("isAdditionalFilterSupport", false)
						.append("combinedKpiSource", "Jenkins/Bamboo/AzurePipeline/Teamcity")
						.append("upperThresholdBG", "white")
						.append("lowerThresholdBG", "red")
						.append("forecastModel", "thetaMethod");

		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI_ID),
						kpiMaster,
						new com.mongodb.client.model.ReplaceOptions().upsert(true));
	}

	public void insertKpiColumnConfig(MongoTemplate mongoTemplate) {
		Document columnConfig =
				new Document()
						.append("basicProjectConfigId", null)
						.append(KPI_ID_FIELD, KPI_ID)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										new Document()
												.append(COLUMN_NAME, "Days/Weeks")
												.append(ORDER, 1)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Project")
												.append(ORDER, 2)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Suite Name")
												.append(ORDER, 3)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Total Tests")
												.append(ORDER, 4)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Passed")
												.append(ORDER, 5)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Failed")
												.append(ORDER, 6)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Pass Rate %")
												.append(ORDER, 7)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI_ID),
						columnConfig,
						new com.mongodb.client.model.ReplaceOptions().upsert(true));
	}

	public void insertFieldMappingStructure(MongoTemplate mongoTemplate) {
		Document jobNameMapping =
				new Document()
						.append(FIELD_NAME, "e2eTestJobNameKPI218")
						.append("fieldLabel", "E2E Test Job Name")
						.append("fieldType", "text")
						.append("section", "Custom Fields Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document()
										.append(
												DEFINITION,
												"Name of the CI job that runs your Selenium / E2E test suite. "
														+ "Only builds from this job are used to compute E2E Test Pass Rate. "
														+ "e.g. e2e-regression"))
						.append("fieldDisplayOrder", 1)
						.append("sectionOrder", 5)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		Document branchMapping =
				new Document()
						.append(FIELD_NAME, "e2eTestBranchKPI218")
						.append("fieldLabel", "E2E Test Branch")
						.append("fieldType", "text")
						.append("section", "Custom Fields Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document()
										.append(
												DEFINITION,
												"Branch name to filter E2E test builds on. "
														+ "Only builds on this branch are counted. e.g. main"))
						.append("fieldDisplayOrder", 2)
						.append("sectionOrder", 5)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		Document thresholdMapping =
				new Document()
						.append(FIELD_NAME, "thresholdValueKPI218")
						.append("fieldLabel", "Target KPI Value")
						.append("fieldType", "number")
						.append("section", "Project Level Threshold")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document()
										.append(
												DEFINITION,
												"Target pass rate (%). Shown as a reference line on the chart. "
														+ "Leave empty to use the default maturity line."))
						.append("fieldDisplayOrder", 1)
						.append("sectionOrder", 6)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION).insertOne(jobNameMapping);
		mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION).insertOne(branchMapping);
		mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION).insertOne(thresholdMapping);
	}

	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.deleteOne(new Document(KPI_ID_FIELD, KPI_ID));
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.deleteOne(new Document(KPI_ID_FIELD, KPI_ID));
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.deleteMany(
						new Document(
								FIELD_NAME,
								new Document(
										"$in",
										Arrays.asList(
												"e2eTestJobNameKPI218", "e2eTestBranchKPI218", "thresholdValueKPI218"))));
	}
}
