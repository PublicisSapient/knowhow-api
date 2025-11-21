/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1410;

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
		id = "r_forecast_model_update",
		order = "14105",
		author = "shunaray",
		systemVersion = "14.1.0")
public class ForecastModelUpdateChangeUnit {

	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String FORECAST_MODEL_FIELD = "forecastModel";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		log.info("Starting rollback of forecast model updates");

		removeForecastModel(KPICode.SPRINT_VELOCITY.getKpiId());
		removeForecastModel(KPICode.SPRINT_PREDICTABILITY.getKpiId());
		removeForecastModel(KPICode.SPRINT_CAPACITY_UTILIZATION.getKpiId());
		removeForecastModel(KPICode.COMMITMENT_RELIABILITY.getKpiId());
		removeForecastModel(KPICode.SCOPE_CHURN.getKpiId());
		removeForecastModel(KPICode.COST_OF_DELAY.getKpiId());
		removeForecastModel(KPICode.COST_OF_DELAY_KANBAN.getKpiId());
		removeForecastModel(KPICode.HAPPINESS_INDEX_RATE.getKpiId());

		removeForecastModel(KPICode.DEFECT_DENSITY.getKpiId());
		removeForecastModel(KPICode.DEFECT_REMOVAL_EFFICIENCY.getKpiId());
		removeForecastModel(KPICode.DEFECT_INJECTION_RATE.getKpiId());
		removeForecastModel(KPICode.UNIT_TEST_COVERAGE.getKpiId());
		removeForecastModel(KPICode.CODE_VIOLATIONS.getKpiId());
		removeForecastModel(KPICode.CODE_VIOLATIONS_KANBAN.getKpiId());
		removeForecastModel(KPICode.TEST_EXECUTION_AND_PASS_PERCENTAGE.getKpiId());
		removeForecastModel(KPICode.TEST_EXECUTION_KANBAN.getKpiId());

		// KPIs for Iteration Board.
		removeForecastModel(KPICode.ITERATION_BURNUP.getKpiId());
		removeForecastModel(KPICode.LATE_REFINEMENT.getKpiId());

		log.info("Completed rollback of forecast model updates");
	}

	@RollbackExecution
	public void rollback() {

		log.info("Starting forecast model update for KPIs");

		updateForecastModel(
				KPICode.SPRINT_VELOCITY.getKpiId(), ForecastingModel.EXPONENTIAL_SMOOTHING.getName());
		updateForecastModel(
				KPICode.SPRINT_PREDICTABILITY.getKpiId(), ForecastingModel.EXPONENTIAL_SMOOTHING.getName());
		updateForecastModel(
				KPICode.SPRINT_CAPACITY_UTILIZATION.getKpiId(),
				ForecastingModel.EXPONENTIAL_SMOOTHING.getName());
		updateForecastModel(
				KPICode.COMMITMENT_RELIABILITY.getKpiId(),
				ForecastingModel.EXPONENTIAL_SMOOTHING.getName());
		updateForecastModel(
				KPICode.SCOPE_CHURN.getKpiId(), ForecastingModel.EXPONENTIAL_SMOOTHING.getName());

		updateForecastModel(
				KPICode.COST_OF_DELAY.getKpiId(), ForecastingModel.LINEAR_REGRESSION.getName());
		updateForecastModel(
				KPICode.COST_OF_DELAY_KANBAN.getKpiId(), ForecastingModel.LINEAR_REGRESSION.getName());
		updateForecastModel(
				KPICode.HAPPINESS_INDEX_RATE.getKpiId(), ForecastingModel.LINEAR_REGRESSION.getName());

		updateForecastModel(KPICode.DEFECT_DENSITY.getKpiId(), ForecastingModel.ARIMA.getName());
		updateForecastModel(
				KPICode.DEFECT_REMOVAL_EFFICIENCY.getKpiId(), ForecastingModel.ARIMA.getName());
		updateForecastModel(KPICode.DEFECT_INJECTION_RATE.getKpiId(), ForecastingModel.ARIMA.getName());
		updateForecastModel(KPICode.UNIT_TEST_COVERAGE.getKpiId(), ForecastingModel.ARIMA.getName());
		updateForecastModel(KPICode.CODE_VIOLATIONS.getKpiId(), ForecastingModel.ARIMA.getName());
		updateForecastModel(
				KPICode.CODE_VIOLATIONS_KANBAN.getKpiId(), ForecastingModel.ARIMA.getName());
		updateForecastModel(
				KPICode.TEST_EXECUTION_AND_PASS_PERCENTAGE.getKpiId(), ForecastingModel.ARIMA.getName());
		updateForecastModel(KPICode.TEST_EXECUTION_KANBAN.getKpiId(), ForecastingModel.ARIMA.getName());

		// KPIs for Iteration Board.
		updateForecastModel(
				KPICode.ITERATION_BURNUP.getKpiId(), ForecastingModel.EXPONENTIAL_SMOOTHING.getName());
		updateForecastModel(
				KPICode.LATE_REFINEMENT.getKpiId(), ForecastingModel.EXPONENTIAL_SMOOTHING.getName());

		log.info("Completed forecast model update for 3 KPIs");
	}

	private void updateForecastModel(String kpiId, String modelName) {
		Document filter = new Document(KPI_ID_FIELD, kpiId);
		Document update = new Document("$set", new Document(FORECAST_MODEL_FIELD, modelName));

		mongoTemplate.getCollection(KPI_MASTER_COLLECTION).updateOne(filter, update);

		log.debug("Updated KPI {} with forecast model: {}", kpiId, modelName);
	}

	private void removeForecastModel(String kpiId) {
		Document filter = new Document(KPI_ID_FIELD, kpiId);
		Document update = new Document("$unset", new Document(FORECAST_MODEL_FIELD, ""));

		mongoTemplate.getCollection(KPI_MASTER_COLLECTION).updateOne(filter, update);

		log.debug("Removed forecast model from KPI {}", kpiId);
	}
}
