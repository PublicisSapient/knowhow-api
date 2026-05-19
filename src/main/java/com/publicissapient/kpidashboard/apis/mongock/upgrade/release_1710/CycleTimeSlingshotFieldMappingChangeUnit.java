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

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "cycle_time_slingshot_field_mapping_insert",
		order = "17102",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class CycleTimeSlingshotFieldMappingChangeUnit {

	private static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";
	private static final String FIELD_NAME = "fieldName";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Document issueTypeDoc =
				new Document()
						.append(FIELD_NAME, "jiraIssueTypeKPI202")
						.append("fieldLabel", "Issue types to consider")
						.append("fieldType", "chips")
						.append("fieldCategory", "Issue_Type")
						.append("toggleLabel", null)
						.append("section", "Issue Types Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document("definition", "All issue types considered for Cycle calculation"))
						.append("options", null)
						.append("filterGroup", null)
						.append("nestedFields", null)
						.append("placeHolderText", null)
						.append("fieldDisplayOrder", 1)
						.append("toggleLabelLeft", null)
						.append("toggleLabelRight", null)
						.append("sectionOrder", 2)
						.append("mandatory", true)
						.append("readOnly", null)
						.append("nodeSpecific", false);

		Document workflowGroupDoc =
				new Document()
						.append(FIELD_NAME, "jiraIssueStatusGroupByCategoryKPI202")
						.append("fieldLabel", "Workfow groups")
						.append("fieldType", "chips")
						.append("fieldCategory", "workflow")
						.append("toggleLabel", null)
						.append("section", "WorkFlow Status Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document(
										"definition",
										"Workflow status/es that identify that a backlog item is ready to be taken in a sprint based on Definition of Ready (DOR)"))
						.append("options", null)
						.append("filterGroup", null)
						.append("nestedFields", null)
						.append("placeHolderText", null)
						.append("fieldDisplayOrder", 2)
						.append("toggleLabelLeft", null)
						.append("toggleLabelRight", null)
						.append("sectionOrder", null)
						.append("mandatory", true)
						.append("readOnly", null)
						.append("nodeSpecific", false);

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.insertMany(List.of(issueTypeDoc, workflowGroupDoc));
	}

	@RollbackExecution
	public void rollback() {
		Query query =
				new Query(
						Criteria.where(FIELD_NAME)
								.in("jiraIssueTypeKPI202", "jiraIssueStatusGroupByCategoryKPI202"));
		mongoTemplate.remove(query, FIELD_MAPPING_STRUCTURE);
	}
}
