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
		id = "cycle_time_slingshot_excel_status_column",
		order = "17136",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class CycleTimeSlingshotStatusColumnChangeUnit {

	private static final String KPI_ID = "kpiId";
	private static final String KPI_COLUMN_CONFIGS = "kpi_column_configs";
	private static final String BASIC_PROJECT_CONFIG_ID = "basicProjectConfigId";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Document doc =
				new Document()
						.append(BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, "kpi202")
						.append(
								"kpiColumnDetails",
								List.of(
										columnDetail("Issue ID", 1),
										columnDetail("Issue Type", 2),
										columnDetail("Issue Description", 3),
										columnDetail("Status", 4),
										columnDetail("Group Map", 5)));

		Document doc2 =
				new Document()
						.append(BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, "kpi204")
						.append(
								"kpiColumnDetails",
								List.of(
										columnDetail("Issue ID", 1),
										columnDetail("Issue Type", 2),
										columnDetail("Issue Description", 3),
										columnDetail("Sprint Name", 4),
										columnDetail("Status", 5),
										columnDetail("Group Map", 6)));

		ReplaceOptions upsertOptions = new ReplaceOptions().upsert(true);
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document(BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, "kpi202"),
						doc,
						upsertOptions);
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document(BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, "kpi204"),
						doc2,
						upsertOptions);
	}

	@RollbackExecution
	public void rollback() {
		Document doc =
				new Document()
						.append(BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, "kpi202")
						.append(
								"kpiColumnDetails",
								List.of(
										columnDetail("Issue ID", 1),
										columnDetail("Issue Type", 2),
										columnDetail("Issue Description", 3),
										columnDetail("Group Map", 4)));

		Document doc2 =
				new Document()
						.append(BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, "kpi204")
						.append(
								"kpiColumnDetails",
								List.of(
										columnDetail("Issue ID", 1),
										columnDetail("Issue Type", 2),
										columnDetail("Issue Description", 3),
										columnDetail("Sprint Name", 4),
										columnDetail("Group Map", 5)));

		ReplaceOptions upsertOptions = new ReplaceOptions().upsert(true);
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document(BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, "kpi202"),
						doc,
						upsertOptions);
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document(BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, "kpi204"),
						doc2,
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
