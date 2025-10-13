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
package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1410;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(
		id = "test_execution_time_kpi_kanban",
		order = "14102",
		author = "rendk",
		systemVersion = "14.1.0")
public class TestExecutionTimeKpiKanban {

	public static final String KPI_ID = "kpiId";
	public static final String KPI_197 = "kpi197";
	public static final String COLUMN_NAME = "columnName";
	public static final String ORDER = "order";
	public static final String IS_DEFAULT = "isDefault";
	public static final String IS_SHOWN = "isShown";

	private final MongoTemplate mongoTemplate;

	public TestExecutionTimeKpiKanban(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		// Insert document in kpi_master
		Document kpiMasterDoc =
				new Document(KPI_ID, KPI_197)
						.append("kpiName", "Test Execution Time")
						.append("isDeleted", false)
						.append("defaultOrder", 1)
						.append("kpiSubCategory", "Quality")
						.append("kpiSource", "Zypher")
						.append("combinedKpiSource", "Zephyr/Zypher/JiraTest")
						.append("groupId", 9)
						.append("kpiUnit", "min")
						.append("kanban", true)
						.append("chartType", "stacked-bar-chart")
						.append(
								"kpiInfo",
								new Document(
												"definition",
												"The Test Execution Time metric measures the average duration between the start and end of test executions. It helps QA leads track how long it takes to run test cases and identify delays or inconsistencies in execution speed across teams, test types, or modules.")
										.append(
												"details",
												java.util.Arrays.asList(
														new Document("type", "link")
																.append(
																		"kpiLinkDetail",
																		new Document("text", "Detailed Information at")
																				.append(
																						"link",
																						"https://knowhow.suite.publicissapient.com/wiki/spaces/PS/pages/272465922/Test+Execution+Time")))))
						.append("xaxisLabel", "Sprints")
						.append("yaxisLabel", "Avg. Execution Time (in minutes)")
						.append("isAdditionalFilterSupport", false)
						.append("kpiFilter", "")
						.append("boxType", "chart")
						.append("calculateMaturity", false);

		mongoTemplate.getCollection("kpi_master").insertOne(kpiMasterDoc);

		// Insert document in kpi_category_mapping
		Document kpiCategoryMappingDoc =
				new Document(KPI_ID, KPI_197)
						.append("categoryId", "quality")
						.append("kpiOrder", 4)
						.append("kanban", true);

		mongoTemplate.getCollection("kpi_category_mapping").insertOne(kpiCategoryMappingDoc);

		Document kpiColumnConfigDoc =
				new Document("basicProjectConfigId", null)
						.append(KPI_ID, KPI_197)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										new Document(COLUMN_NAME, "Sprint Name")
												.append(ORDER, 1)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document(COLUMN_NAME, "Test Case ID")
												.append(ORDER, 2)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document(COLUMN_NAME, "Test case Type")
												.append(ORDER, 3)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document(COLUMN_NAME, "Test Case Status")
												.append(ORDER, 4)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document(COLUMN_NAME, "Execution Time")
												.append(ORDER, 5)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true)));

		mongoTemplate.getCollection("kpi_column_configs").insertOne(kpiColumnConfigDoc);
		log.info(
				"Inserted KPI: Test Execution Time Kanban (kpi197) into kpi_master and kpi_category_mapping");
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate.getCollection("kpi_master").deleteOne(new Document(KPI_ID, KPI_197));
		mongoTemplate.getCollection("kpi_category_mapping").deleteOne(new Document(KPI_ID, KPI_197));
		mongoTemplate.getCollection("kpi_column_configs").deleteOne(new Document(KPI_ID, KPI_197));
		log.info(
				"Deleted KPI: Test Execution Time Kanban (kpi197) from kpi_master and kpi_category_mapping");
	}
}
