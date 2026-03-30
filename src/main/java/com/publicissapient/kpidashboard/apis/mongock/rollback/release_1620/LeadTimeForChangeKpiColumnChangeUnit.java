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

package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1620;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "r_lead_time_for_change_kpi_column_update",
		order = "016201",
		author = "kunkambl",
		systemVersion = "16.2.0")
@RequiredArgsConstructor
public class LeadTimeForChangeKpiColumnChangeUnit {

	private final MongoTemplate mongoTemplate;

	@RollbackExecution
	public void execute() {
		Query query = new Query(Criteria.where("kpiId").is("kpi156").and("columnName").is("Story ID"));
		Update update = new Update().set("kpiColumnDetails.$.columnName", "ID");
		mongoTemplate.updateMulti(query, update, "kpi_column_configs");
	}

	@Execution
	public void rollback() {
		Query query = new Query(Criteria.where("kpiId").is("kpi156").and("columnName").is("ID"));
		Update update = new Update().set("kpiColumnDetails.$.columnName", "Story ID");
		mongoTemplate.updateMulti(query, update, "kpi_column_configs");
	}
}
