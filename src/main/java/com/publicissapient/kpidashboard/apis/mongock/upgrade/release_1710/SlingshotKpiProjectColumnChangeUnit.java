package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Adds "Project" as the second column to kpi215, kpi218, kpi219, kpi220, kpi221, aligning them with
 * the rest of the Slingshot KPIs that already include a Project column.
 */
@ChangeUnit(
		id = "slingshot_kpi_project_column",
		order = "17192",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class SlingshotKpiProjectColumnChangeUnit {

	private static final String KPI_COLUMN_CONFIGS = "kpi_column_configs";
	private static final String KPI_ID_FIELD = "kpiId";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		replace(
				"kpi215",
				cols(
						col("Days/Weeks", 1, true),
						col("Project", 2, true),
						col("Repository", 3, true),
						col("Branch", 4, true),
						col("Developer", 5, true),
						col("Email/Username", 6, false),
						col("No. of PR", 7, true),
						col("No. of Revert PR", 8, true),
						col("Revert PR URL", 9, true),
						col("Revert Rate", 10, true)));

		replace(
				"kpi218",
				cols(
						col("Days/Weeks", 1, true),
						col("Project", 2, true),
						col("Job / Pipeline / Workflow", 3, true),
						col("Branch", 4, true),
						col("Suite Name", 5, true),
						col("Total Builds", 6, true),
						col("Avg Tests/Build", 7, true),
						col("Avg Passed", 8, true),
						col("Avg Failed", 9, true),
						col("Avg Skipped", 10, true),
						col("Pass Rate %", 11, true)));

		replace(
				"kpi219",
				cols(
						col("Days/Weeks", 1, true),
						col("Project", 2, true),
						col("Job / Pipeline / Workflow", 3, true),
						col("Branch", 4, true),
						col("Total Builds", 5, true),
						colOptional("Successful Builds", 6),
						colOptional("Failed Builds", 7),
						colOptional("Builds Skipped", 8),
						colOptional("PRs in Window", 9),
						col("Avg Duration (Hours)", 10, true)));

		replace(
				"kpi220",
				cols(
						col("Days/Weeks", 1, true),
						col("Project", 2, true),
						col("Job / Pipeline / Workflow", 3, true),
						col("Branch", 4, true),
						col("Suite Name", 5, true),
						col("Total Builds", 6, true),
						col("Passing Runs", 7, true),
						col("Failing Runs", 8, true),
						col("Flaky", 9, true),
						col("Flaky Rate %", 10, true)));

		replace(
				"kpi221",
				cols(
						col("Days/Weeks", 1, true),
						col("Project", 2, true),
						col("Job / Pipeline / Workflow", 3, true),
						col("Branch", 4, true),
						col("Total Builds", 5, true),
						col("Successful Builds", 6, true),
						col("Failed Builds", 7, true),
						col("Change Failure Rate %", 8, true)));
	}

	@RollbackExecution
	public void rollback() {
		replace(
				"kpi215",
				cols(
						col("Days/Weeks", 1, true),
						col("Repository", 2, true),
						col("Branch", 3, true),
						col("Developer", 4, true),
						col("Email/Username", 5, false),
						col("No. of PR", 6, true),
						col("No. of Revert PR", 7, true),
						col("Revert PR URL", 8, true),
						col("Revert Rate", 9, true)));

		replace(
				"kpi218",
				cols(
						col("Days/Weeks", 1, true),
						col("Job / Pipeline / Workflow", 2, true),
						col("Branch", 3, true),
						col("Suite Name", 4, true),
						col("Total Builds", 5, true),
						col("Avg Tests/Build", 6, true),
						col("Avg Passed", 7, true),
						col("Avg Failed", 8, true),
						col("Avg Skipped", 9, true),
						col("Pass Rate %", 10, true)));

		replace(
				"kpi219",
				cols(
						col("Days/Weeks", 1, true),
						col("Job / Pipeline / Workflow", 2, true),
						col("Branch", 3, true),
						col("Total Builds", 4, true),
						colOptional("Successful Builds", 5),
						colOptional("Failed Builds", 6),
						colOptional("Builds Skipped", 7),
						colOptional("PRs in Window", 8),
						col("Avg Duration (Hours)", 9, true)));

		replace(
				"kpi220",
				cols(
						col("Days/Weeks", 1, true),
						col("Job / Pipeline / Workflow", 2, true),
						col("Branch", 3, true),
						col("Suite Name", 4, true),
						col("Total Builds", 5, true),
						col("Passing Runs", 6, true),
						col("Failing Runs", 7, true),
						col("Flaky", 8, true),
						col("Flaky Rate %", 9, true)));

		replace(
				"kpi221",
				cols(
						col("Days/Weeks", 1, true),
						col("Job / Pipeline / Workflow", 2, true),
						col("Branch", 3, true),
						col("Total Builds", 4, true),
						col("Successful Builds", 5, true),
						col("Failed Builds", 6, true),
						col("Change Failure Rate %", 7, true)));
	}

	private void replace(String kpiId, List<Document> columns) {
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document(KPI_ID_FIELD, kpiId).append("basicProjectConfigId", null),
						new Document("basicProjectConfigId", null)
								.append(KPI_ID_FIELD, kpiId)
								.append("kpiColumnDetails", columns),
						new ReplaceOptions().upsert(true));
	}

	private static Document col(String name, int order, boolean shown) {
		return new Document("columnName", name)
				.append("order", order)
				.append("isShown", shown)
				.append("isDefault", shown);
	}

	private static Document colOptional(String name, int order) {
		return new Document("columnName", name)
				.append("order", order)
				.append("isShown", true)
				.append("isDefault", false);
	}

	private static List<Document> cols(Document... docs) {
		return Arrays.asList(docs);
	}
}
