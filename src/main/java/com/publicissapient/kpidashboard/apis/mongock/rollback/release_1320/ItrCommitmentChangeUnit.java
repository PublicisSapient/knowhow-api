/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1320;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * @author shunaray
 */
@ChangeUnit(
		id = "r_itr_commit_enhc",
		order = "013202",
		author = "shunaray",
		systemVersion = "13.2.0")
public class ItrCommitmentChangeUnit {

	public static final String JIRA_LABELS_KPI_120 = "jiraLabelsKPI120";
	public static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";
	public static final String FIELD_NAME = "fieldName";
	private final MongoTemplate mongoTemplate;

	public ItrCommitmentChangeUnit(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.deleteOne(new Document(FIELD_NAME, JIRA_LABELS_KPI_120));
	}

	@RollbackExecution
	public void rollback() {
		Document existingDocument =
				mongoTemplate
						.getCollection(FIELD_MAPPING_STRUCTURE)
						.find(new Document(FIELD_NAME, JIRA_LABELS_KPI_120))
						.first();

		if (existingDocument == null) {
			Document document =
					new Document()
							.append(FIELD_NAME, JIRA_LABELS_KPI_120)
							.append("fieldLabel", "Labels for Scope Change Identification")
							.append("fieldType", "chips")
							.append("section", "WorkFlow Status Mapping")
							.append(
									"tooltip",
									new Document(
											"definition",
											"Specify labels to detect and track scope changes effectively"));

			mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE).insertOne(document);
		}
	}
}
