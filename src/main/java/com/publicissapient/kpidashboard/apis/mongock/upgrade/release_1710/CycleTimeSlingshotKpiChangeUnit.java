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
		id = "cycle_time_slingshot_kpi_insert",
		order = "17101",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class CycleTimeSlingshotKpiChangeUnit {

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Document kpiDocument =
				new Document()
						.append("kpiId", "kpi200")
						.append("kpiName", "Cycle Time")
						.append("maxValue", "")
						.append("kpiUnit", "Days")
						.append("isDeleted", "False")
						.append("defaultOrder", 1)
						.append("kpiCategory", "Slingshot")
						.append("kpiSource", "Jira")
						.append("groupId", 45)
						.append("thresholdValue", "")
						.append("kanban", false)
						.append("chartType", "table")
						.append("yAxisLabel", "")
						.append("xAxisLabel", "")
						.append("isAdditionalfFilterSupport", false)
						.append("kpiFilter", "multiSelectDropDown")
						.append("boxType", "3_column")
						.append("calculateMaturity", false)
						.append(
								"kpiInfo",
								new Document()
										.append(
												"definition",
												"Cycle time helps ascertain time spent on each step of the complete issue lifecycle. It is being depicted in the visualization as 3 core cycles - Intake to DOR, DOR to DOD, DOD to Live")
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
																						"https://knowhow.tools.publicis.sapient.com/wiki/kpi171-Cycle+Time")))))
						.append("combinedKpiSource", "Jira/Azure/Rally")
						.append("aggregationCriteria", "average");

		mongoTemplate.getCollection("kpi_master").insertOne(kpiDocument);
	}

	@RollbackExecution
	public void rollback() {
		Query query = new Query(Criteria.where("kpiId").is("kpi200"));
		mongoTemplate.remove(query, "kpi_master");
	}
}
