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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "flow_velocity_and_efficiency_status_column",
		order = "17137",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class FlowVelocityAndEfficiencyStatusColumnChangeUnit {

	private static final String KPI_ID = "kpiId";
	private static final String KPI_COLUMN_CONFIGS = "kpi_column_configs";
	private static final String BASIC_PROJECT_CONFIG_ID = "basicProjectConfigId";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Document kpi205Doc =
				new Document()
						.append(BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, "kpi205")
						.append(
								"kpiColumnDetails",
								List.of(
										columnDetail("Week", 1),
										columnDetail("Issue ID", 2),
										columnDetail("Issue Type", 3),
										columnDetail("Issue Description", 4),
										columnDetail("Squad", 5),
										columnDetail("Priority", 6),
										columnDetail("Story Points", 7),
										columnDetail("Status", 8),
										columnDetail("Original Time Estimate (in hours)", 9),
										columnDetail("Time Spent (in hours)", 10)));

		Document kpi203Doc =
				new Document()
						.append(BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, "kpi203")
						.append(
								"kpiColumnDetails",
								List.of(
										columnDetail("Issue ID", 1),
										columnDetail("Issue Type", 2),
										columnDetail("Issue Description", 3),
										columnDetail("Size (In Story Points)", 4),
										columnDetail("Status", 5),
										columnDetail("Wait Time", 6),
										columnDetail("Total Time", 7),
										columnDetail("Flow Efficiency", 8),
										columnDetail("Group Map", 9)));

		ReplaceOptions upsertOptions = new ReplaceOptions().upsert(true);
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document(BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, "kpi205"),
						kpi205Doc,
						upsertOptions);
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document(BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, "kpi203"),
						kpi203Doc,
						upsertOptions);
	}

	@RollbackExecution
	public void rollback() {
		Document kpi205Doc =
				new Document()
						.append(BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, "kpi205")
						.append(
								"kpiColumnDetails",
								List.of(
										columnDetail("Week", 1),
										columnDetail("Issue ID", 2),
										columnDetail("Issue Type", 3),
										columnDetail("Issue Description", 4),
										columnDetail("Squad", 5),
										columnDetail("Priority", 6),
										columnDetail("Story Points", 7),
										columnDetail("Original Time Estimate (in hours)", 8),
										columnDetail("Time Spent (in hours)", 9)));

		Document kpi203Doc =
				new Document()
						.append(BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, "kpi203")
						.append(
								"kpiColumnDetails",
								List.of(
										columnDetail("Issue ID", 1),
										columnDetail("Issue Type", 2),
										columnDetail("Issue Description", 3),
										columnDetail("Size (In Story Points)", 4),
										columnDetail("Wait Time", 5),
										columnDetail("Total Time", 6),
										columnDetail("Flow Efficiency", 7),
										columnDetail("Group Map", 8)));

		ReplaceOptions upsertOptions = new ReplaceOptions().upsert(true);
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document(BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, "kpi205"),
						kpi205Doc,
						upsertOptions);
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document(BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, "kpi203"),
						kpi203Doc,
						upsertOptions);
	}

	private Document columnDetail(String name, int order) {
		return new Document()
				.append("columnName", name)
				.append("order", order)
				.append("isShown", true)
				.append("isDefault", true);
	}
}
