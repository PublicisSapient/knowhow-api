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
package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1330;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCollection;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/** author@aksshriv1 */
@ChangeUnit(
		id = "yaxis_sonar_label",
		order = "13303",
		author = "aksshriv1",
		systemVersion = "13.3.0")
public class KpiMasterChangeUnit {

	private final MongoTemplate mongoTemplate;

	public KpiMasterChangeUnit(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		updatekpi168();
	}

	public void updatekpi168() {
		MongoCollection<Document> kpiMaster = mongoTemplate.getCollection("kpi_master");
		Document filter = new Document("kpiId", "kpi168");

		Document update = new Document("$set", new Document("yAxisLabel", "Code Quality"));

		// Perform the update
		kpiMaster.updateOne(filter, update);
	}

	@RollbackExecution
	public void rollback() {
		rollbackkpi168();
	}

	public void rollbackkpi168() {
		MongoCollection<Document> kpiMaster = mongoTemplate.getCollection("kpi_master");
		Document filter = new Document("kpiId", "kpi168");
		Document update = new Document("$set", new Document("yAxisLabel", null));

		// Perform the update
		kpiMaster.updateOne(filter, update);
	}
}
