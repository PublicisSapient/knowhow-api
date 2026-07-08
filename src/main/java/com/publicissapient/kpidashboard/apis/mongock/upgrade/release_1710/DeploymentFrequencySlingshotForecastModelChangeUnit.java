package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.apis.enums.KPICode;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "deployment_frequency_slingshot_forecast_model",
		order = "17145",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class DeploymentFrequencySlingshotForecastModelChangeUnit {

	private static final String KPI_MASTER = "kpi_master";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String FORECAST_MODEL = "forecastModel";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		mongoTemplate
				.getCollection(KPI_MASTER)
				.updateOne(
						new Document(KPI_ID_FIELD, KPICode.DEPLOYMENT_FREQUENCY_SLINGSHOT.getKpiId()),
						new Document(
								"$set", new Document(FORECAST_MODEL, ForecastingModel.THETA_METHOD.getName())));
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate
				.getCollection(KPI_MASTER)
				.updateOne(
						new Document(KPI_ID_FIELD, KPICode.DEPLOYMENT_FREQUENCY_SLINGSHOT.getKpiId()),
						new Document("$unset", new Document(FORECAST_MODEL, "")));
	}
}
