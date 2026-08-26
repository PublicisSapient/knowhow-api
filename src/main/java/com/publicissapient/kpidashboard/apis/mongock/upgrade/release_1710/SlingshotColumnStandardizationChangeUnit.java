package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Standardises Excel column header names across all Slingshot KPIs in kpi_column_configs.
 *
 * <ul>
 *   <li>"Workflow" → "Job / Pipeline / Workflow" for kpi212, kpi218, kpi219, kpi220, kpi221
 *   <li>"Job Name / Pipeline Name" → "Job / Pipeline / Workflow" for the same Slingshot CI KPIs
 *       (catches any environment where the DB already has "Job Name / Pipeline Name" rather than
 *       "Workflow")
 *   <li>"Job/Pipeline Name" → "Job / Pipeline / Workflow" for kpi213, kpi214 (seeded by prior
 *       release_1710 replaceOne migrations that cannot re-run on dev DBs)
 *   <li>"Repo" → "Repository" for kpi208, kpi209, kpi210, kpi211, kpi215
 *   <li>"Repository Name" → "Repository" for kpi213, kpi214, kpi223
 *   <li>"Project Name" → "Project" for all Slingshot KPIs (kpi208–kpi223)
 * </ul>
 *
 * <p>Non-Slingshot KPIs are not touched — they retain "Job Name / Pipeline Name". All renames are
 * idempotent: updateMany only matches rows that still carry the old name.
 */
@ChangeUnit(
		id = "slingshot_column_standardization",
		order = "17189",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class SlingshotColumnStandardizationChangeUnit {

	private static final List<String> CI_SLINGSHOT_KPI_IDS =
			List.of("kpi212", "kpi218", "kpi219", "kpi220", "kpi221");

	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_COLUMN_CONFIGS = "kpi_column_configs";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		// "Workflow" → "Job / Pipeline / Workflow" (CI KPIs seeded with "Workflow" by
		// prior migrations)
		rename(CI_SLINGSHOT_KPI_IDS, "Workflow", "Job / Pipeline / Workflow");

		// "Job Name / Pipeline Name" → "Job / Pipeline / Workflow" (Slingshot CI KPIs
		// only)
		// Guards environments whose DB already has "Job Name / Pipeline Name" for these
		// KPIs.
		rename(CI_SLINGSHOT_KPI_IDS, "Job Name / Pipeline Name", "Job / Pipeline / Workflow");

		// "Job/Pipeline Name" → "Job / Pipeline / Workflow" (kpi213/kpi214 deployment
		// KPIs)
		rename(List.of("kpi213", "kpi214"), "Job/Pipeline Name", "Job / Pipeline / Workflow");

		// "Repo" → "Repository" for SCM PR KPIs
		rename(List.of("kpi208", "kpi209", "kpi210", "kpi211", "kpi215"), "Repo", "Repository");

		// "Repository Name" → "Repository" for deployment and security KPIs
		rename(List.of("kpi213", "kpi214", "kpi223"), "Repository Name", "Repository");

		// "Project Name" → "Project" for all Slingshot KPIs
		rename(
				List.of(
						"kpi208", "kpi209", "kpi210", "kpi211", "kpi212", "kpi213", "kpi214", "kpi215",
						"kpi216", "kpi217", "kpi218", "kpi219", "kpi220", "kpi221", "kpi222", "kpi223"),
				"Project Name",
				"Project");
	}

	@RollbackExecution
	public void rollback() {
		rename(CI_SLINGSHOT_KPI_IDS, "Job / Pipeline / Workflow", "Workflow");

		rename(List.of("kpi213", "kpi214"), "Job / Pipeline / Workflow", "Job/Pipeline Name");

		rename(List.of("kpi208", "kpi209", "kpi210", "kpi211", "kpi215"), "Repository", "Repo");

		rename(List.of("kpi213", "kpi214", "kpi223"), "Repository", "Repository Name");

		rename(
				List.of(
						"kpi208", "kpi209", "kpi210", "kpi211", "kpi212", "kpi213", "kpi214", "kpi215",
						"kpi216", "kpi217", "kpi218", "kpi219", "kpi220", "kpi221", "kpi222", "kpi223"),
				"Project",
				"Project Name");
	}

	private void rename(List<String> kpiIds, String oldName, String newName) {
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.updateMany(
						new Document(KPI_ID_FIELD, new Document("$in", kpiIds))
								.append("kpiColumnDetails.columnName", oldName),
						new Document("$set", new Document("kpiColumnDetails.$.columnName", newName)));
	}
}
