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
		id = "flow_velocity_kpi205_column_config_update",
		order = "17138",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class FlowVelocityKpi205ColumnConfigUpdateChangeUnit {

	private static final String KPI_ID = "kpiId";
	private static final String KPI_205 = "kpi205";
	private static final String KPI_COLUMN_CONFIGS = "kpi_column_configs";
	private static final String BASIC_PROJECT_CONFIG_ID = "basicProjectConfigId";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Document filter = new Document(BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, KPI_205);

		Document replacement =
				new Document(BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, KPI_205)
						.append(
								"kpiColumnDetails",
								List.of(
										columnDetail("Days/Weeks", 1),
										columnDetail("Issue ID", 2),
										columnDetail("Issue Type", 3),
										columnDetail("Issue Description", 4),
										columnDetail("Sprint Name", 5),
										columnDetail("Squad", 6),
										columnDetail("Priority", 7),
										columnDetail("Story Points", 8),
										columnDetail("Status", 9),
										columnDetail("Original Time Estimate (in hours)", 10),
										columnDetail("Time Spent (in hours)", 11)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(filter, replacement, new ReplaceOptions().upsert(true));
	}

	@RollbackExecution
	public void rollback() {
		Document filter = new Document(BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, KPI_205);

		Document replacement =
				new Document(BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, KPI_205)
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

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(filter, replacement, new ReplaceOptions().upsert(true));
	}

	private Document columnDetail(String name, int order) {
		return new Document()
				.append("columnName", name)
				.append("order", order)
				.append("isShown", true)
				.append("isDefault", true);
	}
}
