package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "deployment_frequency_slingshot_group_id_update",
		order = "17143",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class DeploymentFrequencySlingshotGroupIdUpdateChangeUnit {

	private static final String KPI_ID = "kpiId";
	private static final String KPI_213 = "kpi213";
	private static final String KPI_MASTER = "kpi_master";
	private static final String GROUP_ID = "groupId";
	private static final String FORECAST_MODEL = "forecastModel";
	private static final String KPI_FILTER = "kpiFilter";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		mongoTemplate
				.getCollection(KPI_MASTER)
				.updateOne(
						new Document(KPI_ID, KPI_213),
						new Document(
								"$set",
								new Document(GROUP_ID, 47)
										.append(FORECAST_MODEL, ForecastingModel.THETA_METHOD.getName())
										.append(KPI_FILTER, "dropDown")));
	}

	@RollbackExecution
	public void rollback() {
		Document rollbackUpdate = new Document();
		rollbackUpdate.append(
				"$set", new Document(GROUP_ID, 8).append(KPI_FILTER, "multiSelectDropDown"));
		rollbackUpdate.append("$unset", new Document(FORECAST_MODEL, ""));
		mongoTemplate
				.getCollection(KPI_MASTER)
				.updateOne(new Document(KPI_ID, KPI_213), rollbackUpdate);
	}
}
