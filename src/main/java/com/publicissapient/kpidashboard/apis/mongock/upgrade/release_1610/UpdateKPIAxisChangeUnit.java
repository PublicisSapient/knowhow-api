/*
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1610;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCollection;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * rollback kpiname and y-axis label
 *
 * @author aksshriv1
 */
@ChangeUnit(id = "label_X_axis", order = "16103", author = "aksshriv1", systemVersion = "16.1.0")
public class UpdateKPIAxisChangeUnit {

	private final MongoTemplate mongoTemplate;

	public UpdateKPIAxisChangeUnit(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		updatekpi197();
	}

	public void updatekpi197() {
		MongoCollection<Document> kpiMaster = mongoTemplate.getCollection("kpi_master");
		Document filter = new Document("kpiId", "kpi197");

		Document update = new Document("$set", new Document("xaxisLabel", "Weeks"));

		// Perform the update
		kpiMaster.updateOne(filter, update);
	}

	@RollbackExecution
	public void rollback() {
		rollbackkpi197();
	}

	public void rollbackkpi197() {
		MongoCollection<Document> kpiMaster = mongoTemplate.getCollection("kpi_master");
		Document filter = new Document("kpiId", "kpi197");

		Document update = new Document("$set", new Document("xaxisLabel", "Sprints"));

		// Perform the update
		kpiMaster.updateOne(filter, update);
	}
}
