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
 * Linear Regression forecaster using Apache Commons Math library.
 * 
 * <p>
 * This forecaster implements simple linear regression to predict future KPI
 * values based on historical trends. It fits a line of best fit (y = mx + b)
 * through the historical data points and uses it to forecast the next value.
 * </p>
 * 
 * <h3>Algorithm Overview:</h3>
 * <ol>
 * <li>Extracts numeric values from historical DataCount objects</li>
 * <li>Fits a simple linear regression model: y = slope * x + intercept</li>
 * <li>Uses the fitted model to predict the next value in the sequence</li>
 * <li>Clamps negative predictions to 0 (since most KPIs are non-negative)</li>
 * </ol>
 * 
 * <h3>When to Use:</h3>
 * <ul>
 * <li>Data shows a linear trend (increasing or decreasing)</li>
 * <li>Relationship between time and KPI value is approximately linear</li>
 * <li>Need a simple, interpretable forecasting model</li>
 * <li>Minimum 2 data points required (enforced by parent class)</li>
 * </ul>
 * 
 * <h3>Key Metrics:</h3>
 * <ul>
 * <li><b>Slope</b>: Rate of change (how much KPI increases/decreases per time
 * unit)</li>
 * <li><b>Intercept</b>: Starting value when x=0</li>
 * <li><b>R²</b>: Coefficient of determination (0-1, higher = better fit)</li>
 * </ul>
 * 
 * <h3>Dependencies:</h3>
 * <ul>
 * <li>Apache Commons Math3 {@link SimpleRegression} for regression
 * calculations</li>
 * <li>Extends {@link AbstractForecastService} for common forecasting
 * utilities</li>
 * </ul>
 * 
 * <h3>Example:</h3>
 * 
 * <pre>
 * Historical data: [10, 15, 20, 25, 30]
 * Fitted line: y = 5x + 10 (R² = 1.0)
 * Next forecast: y = 5 * 5 + 10 = 35
 * </pre>
 * 
 * @see AbstractForecastService
 * @see ForecastingModel#LINEAR_REGRESSION
 * @author KnowHOW Development Team
 * @since 14.1.0
 */
@Service
@Slf4j
public class LinearRegressionForecaster extends AbstractForecastService {

	@Override
	public ForecastingModel getModelType() {
		return ForecastingModel.LINEAR_REGRESSION;
	}

	/**
	 * Generates a linear regression forecast for the next time period.
	 * 
	 * <p>
	 * The regression model is of the form: <b>y = mx + b</b> where:
	 * </p>
	 * <ul>
	 * <li>y = predicted KPI value</li>
	 * <li>m = slope (rate of change)</li>
	 * <li>x = time index (0, 1, 2, ...)</li>
	 * <li>b = intercept (baseline value)</li>
	 * </ul>
	 * 
	 * @param historicalData
	 *            List of historical DataCount objects containing past KPI values.
	 *            Must contain at least 2 valid numeric values.
	 * @param kpiId
	 *            The identifier of the KPI being forecasted (used for logging).
	 * @return List containing a single DataCount with the forecasted value, or
	 *         empty list if:
	 *         <ul>
	 *         <li>Insufficient data (less than minimum required)</li>
	 *         <li>No valid numeric values could be extracted</li>
	 *         <li>Regression model is invalid (NaN slope/intercept)</li>
	 *         <li>Exception occurs during calculation</li>
	 *         </ul>
	 * 
	 * @see AbstractForecastService#canForecast(List, String)
	 * @see AbstractForecastService#extractValues(List)
	 * @see AbstractForecastService#createForecastDataCount(Double, String, String,
	 *      String)
	 */
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

			// Step 1: Initialize SimpleRegression from Apache Commons Math3
			SimpleRegression regression = new SimpleRegression();

			// Step 2: Add historical data points to regression model
			for (int i = 0; i < values.size(); i++) {
				regression.addData(i, values.get(i));
				log.debug("Training: x={}, y={}", i, String.format("%.2f", values.get(i)));
			}

			// Step 3: Validate the regression model
			if (Double.isNaN(regression.getSlope()) || Double.isNaN(regression.getIntercept())) {
				log.warn("Invalid regression model for KPI {}", kpiId);
				return forecasts;
			}

			// Step 4: Extract regression statistics
			double rSquare = regression.getRSquare(); // Goodness of fit (0-1, closer to 1 is better)
			double slope = regression.getSlope(); // Rate of change per time unit
			double intercept = regression.getIntercept(); // Y-intercept (baseline value)

			log.debug("KPI {}: slope={}, intercept={}, R²={}", kpiId, String.format("%.4f", slope),
					String.format("%.2f", intercept), String.format("%.4f", rSquare));

			// Step 5: Predict the next value
			int nextIndex = values.size();
			// Use regression.predict() to calculate: y = slope * nextIndex + intercept
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
