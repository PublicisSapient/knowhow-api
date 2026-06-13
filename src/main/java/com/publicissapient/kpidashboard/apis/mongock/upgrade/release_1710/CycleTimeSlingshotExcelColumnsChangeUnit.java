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
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "cycle_time_trend_slingshot_excel_insert",
		order = "17106",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class CycleTimeSlingshotExcelColumnsChangeUnit {

	private static final String KPI_ID = "kpiId";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Document doc =
				new Document()
						.append("basicProjectConfigId", null)
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
						.append("basicProjectConfigId", null)
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
				.getCollection("kpi_column_configs")
				.replaceOne(
						new Document("basicProjectConfigId", null).append(KPI_ID, "kpi202"),
						doc,
						upsertOptions);
		mongoTemplate
				.getCollection("kpi_column_configs")
				.replaceOne(
						new Document("basicProjectConfigId", null).append(KPI_ID, "kpi204"),
						doc2,
						upsertOptions);
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate.remove(
				new Query(Criteria.where(KPI_ID).in("kpi204", "kpi202")), "kpi_column_configs");
	}

	private Document columnDetail(String name, int order) {
		return new Document()
				.append("columnName", name)
				.append("order", order)
				.append("isShown", true)
				.append("isDefault", true);
	}
}
