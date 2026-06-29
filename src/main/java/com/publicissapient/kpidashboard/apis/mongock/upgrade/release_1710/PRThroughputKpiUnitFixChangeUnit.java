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

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "pr_throughput_kpi_unit_fix",
		order = "17139",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class PRThroughputKpiUnitFixChangeUnit {

	private static final String KPI_ID = "kpiId";
	private static final String KPI_208 = "kpi208";
	private static final String KPI_MASTER = "kpi_master";
	private static final String KPI_UNIT = "kpiUnit";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		mongoTemplate
				.getCollection(KPI_MASTER)
				.updateOne(
						new Document(KPI_ID, KPI_208), new Document("$set", new Document(KPI_UNIT, "PRs")));
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate
				.getCollection(KPI_MASTER)
				.updateOne(
						new Document(KPI_ID, KPI_208), new Document("$set", new Document(KPI_UNIT, "MRs")));
	}
}
