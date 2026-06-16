package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "flow_load_field_mapping_update",
		order = "17116",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class FlowLoadKpiFieldMappingUpdate {

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Document workflowGroupDoc =
				new Document()
						.append("fieldName", "jiraIssueStatusGroupByCategoryKPI206")
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

		mongoTemplate.getCollection("field_mapping_structure").insertOne(workflowGroupDoc);

		mongoTemplate
				.getCollection("field_mapping_structure")
				.deleteMany(
						new Document(
								"fieldName",
								new Document(
										"$in",
										java.util.Arrays.asList(
												"storyFirstStatusKPI206",
												"jiraStatusForQaKPI206",
												"jiraStatusForInProgressKPI206"))));
	}

	@RollbackExecution
	public void rollback() {
		// not required
	}
}
