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
		id = "e2e_test_pass_rate_job_name_to_chips",
		order = "17168",
		author = "knowhow",
		systemVersion = "17.1.0")
public class E2ETestPassRateJobNameToChipsChangeUnit {

	private static final String FIELD_MAPPING_STRUCTURE_COLLECTION = "field_mapping_structure";
	private static final String FIELD_NAME = "fieldName";
	private static final String DEFINITION = "definition";
	private static final String FIELD_NAME_VALUE = "e2eTestJobNameKPI218";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.updateOne(
						new Document(FIELD_NAME, FIELD_NAME_VALUE),
						new Document(
								"$set",
								new Document("fieldType", "chips")
										.append("fieldLabel", "E2E Test Workflow Names")
										.append(
												"tooltip",
												new Document(
														DEFINITION,
														"Names of the CI workflows whose test results contribute to E2E Test Pass Rate. "
																+ "Add one or more workflow names (case-insensitive). "
																+ "e.g. API_CI_CD_Prod_Workflow, Processors_CI_CD_Prod_Workflow"))));
	}

	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.updateOne(
						new Document(FIELD_NAME, FIELD_NAME_VALUE),
						new Document(
								"$set",
								new Document("fieldType", "text")
										.append("fieldLabel", "E2E Test Job Name")
										.append(
												"tooltip",
												new Document(
														DEFINITION,
														"Name of the CI job that runs your Selenium / E2E test suite. "
																+ "Only builds from this job are used to compute E2E Test Pass Rate. "
																+ "e.g. e2e-regression"))));
	}
}
