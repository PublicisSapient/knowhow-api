package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Refines kpi214 (Lead Time For Change) column config for existing installations:
 *
 * <ul>
 *   <li>Renames "Repo" → "Repository Name"
 *   <li>Renames "Branch" → "Source Branch"
 *   <li>Renames "Merge Request Url" → "Final Pull Request"
 *   <li>Renames "Job Name / Pipeline Name" → "Job/Pipeline Name"
 *   <li>Renames "Environment" → "Deployed Environment"
 *   <li>Reorders columns: Days/Weeks, Project, Repository Name, Source Branch, Author, Final Pull
 *       Request, Job/Pipeline Name, Deployed Environment, First Commit Date (UTC), Deployment Date
 *       (UTC), Lead Time (Hrs)
 * </ul>
 */
@ChangeUnit(
		id = "lead_time_for_change_slingshot_column_reorder",
		order = "17150",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class LeadTimeForChangeSlingshotColumnReorderChangeUnit {

	private static final String KPI_ID = "kpi214";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Document columnConfig =
				new Document()
						.append("basicProjectConfigId", null)
						.append(KPI_ID_FIELD, KPI_ID)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										new Document()
												.append(COLUMN_NAME, "Days/Weeks")
												.append(ORDER, 1)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Project")
												.append(ORDER, 2)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Repository Name")
												.append(ORDER, 3)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Source Branch")
												.append(ORDER, 4)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Author")
												.append(ORDER, 5)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Final Pull Request")
												.append(ORDER, 6)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Job/Pipeline Name")
												.append(ORDER, 7)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Deployed Environment")
												.append(ORDER, 8)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "First Commit Date (UTC)")
												.append(ORDER, 9)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Deployment Date (UTC)")
												.append(ORDER, 10)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Lead Time (Hrs)")
												.append(ORDER, 11)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI_ID).append("basicProjectConfigId", null),
						columnConfig,
						new com.mongodb.client.model.ReplaceOptions().upsert(true));
	}

	@RollbackExecution
	public void rollback() {
		// No-op: the previous state is restored by re-running
		// LeadTimeForChangeSlingshotColumnRefinementChangeUnit
	}
}
