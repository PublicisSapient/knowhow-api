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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1320;

import java.util.Arrays;
import java.util.Collections;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "insertMetadataIdentifier",
		order = "13208",
		author = "girpatha",
		systemVersion = "13.2.0")
public class MetadataIdentifierChangeUnit {

	private static final String METADATA_IDENTIFIER_COLLECTION = "metadata_identifier";
	private final MongoTemplate mongoTemplate;
	private static final String VALUE = "value";
	private static final String HIERARCHICAL_REQUIREMENT = "hierarchicalrequirement";

	public MetadataIdentifierChangeUnit(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		Document metadataIdentifier =
				new Document()
						.append("tool", "Rally")
						.append("templateName", "Standard Template")
						.append("templateCode", "13")
						.append("isKanban", false)
						.append("disabled", false)
						.append(
								"issues",
								Arrays.asList(
										new Document("type", "jiradefecttype")
												.append(VALUE, Arrays.asList("Defect", "Bug")),
										new Document("type", "jiraIssueTypeNames")
												.append(VALUE, Arrays.asList(HIERARCHICAL_REQUIREMENT, "defect", "task")),
										new Document("type", "jiraIssueEpicType")
												.append(VALUE, Collections.singletonList("Epic")),
										new Document("type", "jiraDefectInjectionIssueTypeKPI14")
												.append(VALUE, Arrays.asList(HIERARCHICAL_REQUIREMENT, "task")),
										new Document("type", "jiraIssueTypeKPI35")
												.append(VALUE, Arrays.asList(HIERARCHICAL_REQUIREMENT, "task"))))
						.append(
								"customfield",
								Arrays.asList(
										new Document("type", "jiraStoryPointsCustomField")
												.append(VALUE, Collections.singletonList("Story Points")),
										new Document("type", "epicCostOfDelay")
												.append(VALUE, Collections.singletonList("Story Points")),
										new Document("type", "epicRiskReduction")
												.append(
														VALUE,
														Collections.singletonList(
																"Risk Reduction-Opportunity Enablement Value"))))
						.append(
								"workflow",
								Arrays.asList(
										new Document("type", "storyFirstStatusKPI148")
												.append(VALUE, Collections.singletonList("Open")),
										new Document("type", "jiraStatusForQaKPI148")
												.append(VALUE, Collections.singletonList("In Testing")),
										new Document("type", "jiraStatusForDevelopmentKPI82")
												.append(
														VALUE,
														Arrays.asList("Implementing", "In Development", "In Analysis"))));

		mongoTemplate.getCollection(METADATA_IDENTIFIER_COLLECTION).insertOne(metadataIdentifier);
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate
				.getCollection(METADATA_IDENTIFIER_COLLECTION)
				.deleteOne(new Document("tool", "Rally").append("templateName", "Standard Template"));
	}
}
