package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Adds the {@code calculationStrategyKPI219} field to Mean Time to Test Feedback (kpi219): inserts
 * a radiobutton entry in {@code field_mapping_structure} so the Build / Commit strategy selector
 * appears in the project settings UI.
 */
@ChangeUnit(
		id = "mean_time_to_test_feedback_strategy",
		order = "17179",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class MeanTimeToTestFeedbackStrategyChangeUnit {

	private static final String FIELD_MAPPING_STRUCTURE_COLLECTION = "field_mapping_structure";
	private static final String FIELD_NAME = "fieldName";
	private static final String STRATEGY_FIELD = "calculationStrategyKPI219";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Document doc =
				new Document()
						.append(FIELD_NAME, STRATEGY_FIELD)
						.append("fieldLabel", "Calculation Strategy")
						.append("fieldType", "radiobutton")
						.append("section", "Custom Fields Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document()
										.append(
												"definition",
												"Choose how Mean Time to Test Feedback is calculated. "
														+ "<br><b>Build</b>: uses the CI build duration directly (default). "
														+ "<br><b>Commit</b>: measures from the earliest commit in the window before a build to the build completion time, "
														+ "showing true feedback lag from code push to test result. <hr>"))
						.append(
								"options",
								java.util.Arrays.asList(
										new Document().append("label", "Build").append("value", "BUILD"),
										new Document().append("label", "Commit").append("value", "COMMIT")))
						.append("fieldDisplayOrder", 3)
						.append("sectionOrder", 5)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, STRATEGY_FIELD),
						doc,
						new com.mongodb.client.model.ReplaceOptions().upsert(true));
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.deleteOne(new Document(FIELD_NAME, STRATEGY_FIELD));
	}
}
