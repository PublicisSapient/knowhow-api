package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Adds the PR option to the {@code calculationStrategyKPI219} radiobutton in {@code
 * field_mapping_structure}. Strategy order: Build (default) → PR → Commit.
 */
@ChangeUnit(
		id = "mean_time_to_test_feedback_pr_strategy",
		order = "17181",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class MeanTimeToTestFeedbackPRStrategyChangeUnit {

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
														+ "<br><b>Build</b>: uses the CI build duration directly (default). Works for all branches. "
														+ "<br><b>PR</b>: measures from the earliest PR merged into the branch within the window to build completion. "
														+ "Best for teams using pull-request workflows on integration or QA branches. "
														+ "<br><b>Commit</b>: measures from the earliest commit in the window to build completion. "
														+ "Requires SCM commits to be synced for the selected branch. <hr>"))
						.append(
								"options",
								java.util.Arrays.asList(
										new Document().append("label", "Build").append("value", "BUILD"),
										new Document().append("label", "PR").append("value", "PR"),
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
		Document twoOption =
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
						twoOption,
						new com.mongodb.client.model.ReplaceOptions().upsert(true));
	}
}
