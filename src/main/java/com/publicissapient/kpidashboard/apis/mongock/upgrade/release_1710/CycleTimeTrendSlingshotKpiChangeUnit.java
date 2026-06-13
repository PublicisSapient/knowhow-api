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

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "cycle_time_trend_slingshot_kpi_insert",
		order = "17105",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class CycleTimeTrendSlingshotKpiChangeUnit {

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Document kpiDocument =
				new Document()
						.append("kpiId", "kpi204")
						.append("kpiName", "Flow Time Trend")
						.append("maxValue", "")
						.append("kpiUnit", "Days")
						.append("isDeleted", "False")
						.append("defaultOrder", 2)
						.append("kpiCategory", "Slingshot")
						.append("kpiSource", "Jira")
						.append("groupId", 34)
						.append("thresholdValue", "")
						.append("kanban", false)
						.append("chartType", "line")
						.append("yAxisLabel", "Days")
						.append("xAxisLabel", "Duration")
						.append("isAdditionalfFilterSupport", false)
						.append("kpiFilter", "dropDown")
						.append("calculateMaturity", false)
						.append(
								"kpiInfo",
								new Document()
										.append(
												"definition",
												"Elapsed wall-clock time from when work started (entered an active state) to when it reached Done.")
										.append(
												"details",
												java.util.List.of(
														new Document()
																.append("type", "link")
																.append(
																		"kpiLinkDetail",
																		new Document()
																				.append("text", "Detailed Information at")
																				.append(
																						"link",
																						"https://knowhow.tools.publicis.sapient.com/wiki/kpi202-Cycle+Time")))))
						.append("combinedKpiSource", "Jira/Azure/Rally")
						.append("aggregationCriteria", "sum");

		mongoTemplate.getCollection("kpi_master").insertOne(kpiDocument);
	}

	@RollbackExecution
	public void rollback() {
		Query query = new Query(Criteria.where("kpiId").is("kpi204"));
		mongoTemplate.remove(query, "kpi_master");
	}
}
