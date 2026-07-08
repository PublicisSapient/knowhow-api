package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Refines the kpi214 (Lead Time For Change) column config for existing installations:
 *
 * <ul>
 *   <li>Removes the redundant "Merge Request Id" column (the URL already carries the PR number as
 *       its display label).
 *   <li>Renames "First Commit Date" → "First Commit Date (UTC)" and "Deployment Date" → "Deployment
 *       Date (UTC)" to make the timezone explicit.
 *   <li>Corrects capitalisation: "Lead Time (hrs)" → "Lead Time (Hrs)".
 * </ul>
 *
 * <p>The column config is replaced in full so order numbers stay contiguous (1–9).
 */
@ChangeUnit(
		id = "lead_time_for_change_slingshot_column_refinement",
		order = "17147",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class LeadTimeForChangeSlingshotColumnRefinementChangeUnit {

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
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.deleteOne(new Document(KPI_ID_FIELD, KPI_ID).append("basicProjectConfigId", null));

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
												.append(COLUMN_NAME, "Repo")
												.append(ORDER, 3)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Branch")
												.append(ORDER, 4)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Author")
												.append(ORDER, 5)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Merge Request Url")
												.append(ORDER, 6)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "First Commit Date (UTC)")
												.append(ORDER, 7)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Deployment Date (UTC)")
												.append(ORDER, 8)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Lead Time (Hrs)")
												.append(ORDER, 9)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true)));

		mongoTemplate.getCollection(KPI_COLUMN_CONFIGS_COLLECTION).insertOne(columnConfig);
	}

	@RollbackExecution
	public void rollback() {
		// No-op: the previous state is restored by re-running
		// LeadTimeForChangeSlingshotFixChangeUnit
	}
}
