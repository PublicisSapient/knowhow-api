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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1410;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@ChangeUnit(id = "sprint_analytics", order = "14100", author = "shunaray", systemVersion = "14.1.0")
public class SprintAnalyticsChangeUnit {
    private static final String KPI_ID = "kpiId";
	private static final String KPI_199 = "kpi199";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void rollback() {
		mongoTemplate.getCollection("kpi_master").insertOne(constructKpiMasterDocument());
	}

	@RollbackExecution
	public void execution() {
		mongoTemplate.getCollection("kpi_master").deleteOne(new Document(KPI_ID, KPI_199));
	}

	private static Document constructKpiMasterDocument() {
		return new Document()
				.append(KPI_ID, KPI_199)
				.append("kpiName", "Sprint Analytics")
				.append("isDeleted", "False")
				.append("kpiSource", "Jira");
	}
}
