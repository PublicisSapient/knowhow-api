package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.apis.enums.KPICode;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Patches the kpi214 (Lead Time For Change) master and column-config records inserted by {@link
 * LeadTimeForChangeSlingshotKpiChangeUnit}:
 *
 * <ul>
 *   <li>Adds {@code forecastModel: "thetaMethod"} to kpi_master (parity with kpi208/kpi213).
 *   <li>Replaces the kpi_column_configs entry with corrected column names ("Days/Weeks" instead of
 *       "Weeks", "Project" instead of "Project Name") and adds commit-context columns (Repo,
 *       Branch, Author) for correlation.
 * </ul>
 */
@ChangeUnit(
		id = "lead_time_for_change_slingshot_kpi_fix",
		order = "17146",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class LeadTimeForChangeSlingshotFixChangeUnit {

	private static final String KPI_ID = "kpi214";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";
	private static final String FORECAST_MODEL = "forecastModel";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		addForecastModel();
		replaceColumnConfig();
	}

	private void addForecastModel() {
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateOne(
						new Document(KPI_ID_FIELD, KPI_ID),
						new Document(
								"$set", new Document(FORECAST_MODEL, ForecastingModel.THETA_METHOD.getName())));
	}

	private void replaceColumnConfig() {
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
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateOne(
						new Document(KPI_ID_FIELD, KPICode.LEAD_TIME_FOR_CHANGE_SLINGSHOT.getKpiId()),
						new Document("$unset", new Document(FORECAST_MODEL, "")));
	}
}
