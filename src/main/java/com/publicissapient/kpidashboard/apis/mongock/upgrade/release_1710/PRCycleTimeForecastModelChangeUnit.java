package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.apis.enums.KPICode;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@ChangeUnit(
		id = "pr_cycle_time_forecast_model_change_unit",
		order = "17132",
		author = "aksshriv1",
		systemVersion = "17.1.0")
public class PRCycleTimeForecastModelChangeUnit {

	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String FORECAST_MODEL_FIELD = "forecastModel";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		log.info("Adding forecastModel to PR Cycle Time KPI (kpi209)");
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateOne(
						new Document(KPI_ID_FIELD, KPICode.PR_CYCLE_TIME_SLINGSHOT.getKpiId()),
						new Document(
								"$set",
								new Document(FORECAST_MODEL_FIELD, ForecastingModel.THETA_METHOD.getName())));
		log.info("Completed adding forecastModel to PR Cycle Time KPI");
	}

	@RollbackExecution
	public void rollback() {
		log.info("Rolling back forecastModel from PR Cycle Time KPI (kpi209)");
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateOne(
						new Document(KPI_ID_FIELD, KPICode.PR_CYCLE_TIME_SLINGSHOT.getKpiId()),
						new Document("$unset", new Document(FORECAST_MODEL_FIELD, "")));
		log.info("Completed rollback of forecastModel from PR Cycle Time KPI");
	}
}
