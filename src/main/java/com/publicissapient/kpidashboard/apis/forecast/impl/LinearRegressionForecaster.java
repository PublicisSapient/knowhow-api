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

package com.publicissapient.kpidashboard.apis.forecast.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.apis.forecast.AbstractForecastService;
import com.publicissapient.kpidashboard.common.model.application.DataCount;

import lombok.extern.slf4j.Slf4j;

/**
 * Linear Regression forecaster using Apache Commons Math.
 */
@Service
@Slf4j
public class LinearRegressionForecaster extends AbstractForecastService {

	@Override
	public ForecastingModel getModelType() {
		return ForecastingModel.LINEAR_REGRESSION;
	}

	@Override
	public List<DataCount> generateForecast(List<DataCount> historicalData, String kpiId) {

		List<DataCount> forecasts = new ArrayList<>();

		if (!canForecast(historicalData, kpiId)) {
			return forecasts;
		}

		List<Double> values = extractValues(historicalData);
		if (values.isEmpty()) {
			return forecasts;
		}

		try {
			log.debug("Starting Linear Regression forecast for KPI {} with {} data points", kpiId, values.size());

			SimpleRegression regression = new SimpleRegression();

			for (int i = 0; i < values.size(); i++) {
				regression.addData(i, values.get(i));
				log.debug("Training: x={}, y={}", i, String.format("%.2f", values.get(i)));
			}

			if (Double.isNaN(regression.getSlope()) || Double.isNaN(regression.getIntercept())) {
				log.warn("Invalid regression model for KPI {}", kpiId);
				return forecasts;
			}

			double rSquare = regression.getRSquare();
			double slope = regression.getSlope();
			double intercept = regression.getIntercept();

			log.debug("KPI {}: slope={}, intercept={}, R²={}", kpiId, String.format("%.4f", slope),
					String.format("%.2f", intercept), String.format("%.4f", rSquare));

			int nextIndex = values.size();
			double forecastValue = Math.max(0, regression.predict(nextIndex));

			String projectName = historicalData.get(historicalData.size() - 1).getSProjectName();
			String kpiGroup = historicalData.get(historicalData.size() - 1).getKpiGroup();

			DataCount forecast = createForecastDataCount(forecastValue, projectName, kpiGroup,
					getModelType().getName());
			forecasts.add(forecast);

			log.info("Generated forecast for KPI {}: value={} [Linear Regression, y={}x+{}, R²={}]", kpiId,
					String.format("%.2f", forecastValue), String.format("%.4f", slope),
					String.format("%.2f", intercept), String.format("%.4f", rSquare));

		} catch (Exception e) {
			log.error("Error in Linear Regression forecast for KPI {}: {}", kpiId, e.getMessage(), e);
		}

		return forecasts;
	}
}
