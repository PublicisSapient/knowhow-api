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

package com.publicissapient.kpidashboard.apis.forecast.service;

import java.util.List;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.common.model.application.DataCount;

/**
 * Interface for forecasting service that predicts future KPI values.
 */
public interface ForecastService {

	/**
	 * Generate forecast data points based on historical data.
	 *
	 * @param historicalData
	 *            List of historical data points
	 * @param kpiId
	 *            KPI identifier
	 * @return List of forecasted DataCount objects
	 */
	List<DataCount> generateForecast(List<DataCount> historicalData, String kpiId);

	/**
	 * Get the forecasting model type supported by this implementation.
	 *
	 * @return ForecastingModel enum
	 */
	ForecastingModel getModelType();

	/**
	 * Check if this forecaster supports the given KPI and has sufficient data.
	 *
	 * @param historicalData
	 *            Historical data points
	 * @param kpiId
	 *            KPI identifier
	 * @return true if forecasting is supported
	 */
	boolean canForecast(List<DataCount> historicalData, String kpiId);
}
