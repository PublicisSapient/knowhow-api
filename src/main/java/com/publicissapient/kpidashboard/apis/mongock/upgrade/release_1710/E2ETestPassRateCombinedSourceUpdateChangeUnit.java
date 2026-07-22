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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "e2e_test_pass_rate_combined_source_update",
		order = "17165",
		author = "knowhow",
		systemVersion = "17.1.0")
public class E2ETestPassRateCombinedSourceUpdateChangeUnit {

	private static final String KPI_ID = "kpi218";
	private static final String KPI_MASTER_COLLECTION = "kpi_master";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateOne(
						new Document("kpiId", KPI_ID),
						new Document(
								"$set",
								new Document(
										"combinedKpiSource", "Jenkins/Bamboo/GitHubAction/AzurePipeline/Teamcity")));
	}

	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateOne(
						new Document("kpiId", KPI_ID),
						new Document(
								"$set",
								new Document("combinedKpiSource", "Jenkins/Bamboo/AzurePipeline/Teamcity")));
	}
}
