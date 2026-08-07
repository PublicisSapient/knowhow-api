package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Expands the kpi219 column config to include all strategy-specific columns: Successful Builds,
 * Failed Builds (BUILD mode), Builds Skipped, PRs in Window (COMMIT mode). All four are added as
 * non-default (appear in the column picker, shown by default) so that users see all relevant data
 * regardless of strategy while retaining the ability to hide columns.
 */
@ChangeUnit(
		id = "mean_time_to_test_feedback_commit_columns",
		order = "17180",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class MeanTimeToTestFeedbackCommitColumnsChangeUnit {

	private static final String KPI_ID = "kpi219";
	private static final String KPI_COLUMN_CONFIGS = "kpi_column_configs";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Document fullConfig =
				new Document()
						.append("basicProjectConfigId", null)
						.append("kpiId", KPI_ID)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										col("Days/Weeks", 1, true, true),
										col("Workflow", 2, true, true),
										col("Branch", 3, true, true),
										col("Total Builds", 4, true, true),
										col("Successful Builds", 5, true, false),
										col("Failed Builds", 6, true, false),
										col("Builds Skipped", 7, true, false),
										col("PRs in Window", 8, true, false),
										col("Avg Duration (Hours)", 9, true, true)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document("basicProjectConfigId", null).append("kpiId", KPI_ID),
						fullConfig,
						new com.mongodb.client.model.ReplaceOptions().upsert(true));
	}

	@RollbackExecution
	public void rollback() {
		Document original =
				new Document()
						.append("basicProjectConfigId", null)
						.append("kpiId", KPI_ID)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										col("Days/Weeks", 1, true, true),
										col("Workflow", 2, true, true),
										col("Branch", 3, true, true),
										col("Total Builds", 4, true, true),
										col("Successful Builds", 5, true, true),
										col("Failed Builds", 6, true, true),
										col("Avg Duration (Hours)", 7, true, true)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document("basicProjectConfigId", null).append("kpiId", KPI_ID),
						original,
						new com.mongodb.client.model.ReplaceOptions().upsert(true));
	}

	private Document col(String name, int order, boolean isShown, boolean isDefault) {
		return new Document()
				.append("columnName", name)
				.append("order", order)
				.append("isShown", isShown)
				.append("isDefault", isDefault);
	}
}
