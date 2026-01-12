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

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.apis.forecast.AbstractForecastService;
import com.publicissapient.kpidashboard.common.model.application.DataCount;

import lombok.extern.slf4j.Slf4j;

/**
 * Theta Method forecaster for time series forecasting.
 *
 * <p>This implementation follows the simplified practical Theta Method by combining: 1) Simple
 * Exponential Smoothing (SES) 2) Linear trend projection
 *
 * <p>and averaging their results.
 *
 * <h3>Algorithm:</h3>
 *
 * <pre>
 * 1. Simple Exponential Smoothing (SES):
 *    S(t) = α * Y(t) + (1 - α) * S(t-1)
 *
 * 2. Linear Trend:
 *    Trend = (Y(n) - Y(1)) / (n - 1)
 *    TrendForecast(h) = Y(n) + h * Trend
 *
 * 3. Final Forecast:
 *    F(h) = ( S(n) + TrendForecast(h) ) / 2
 *
 * Where:
 *   α = 0.2 (fixed smoothing factor)
 *   h = forecast horizon (default = 1)
 * </pre>
 *
 * <h3>Example:</h3>
 *
 * <pre>
 * Input Series:
 *   Y = [10, 12, 11, 13, 15]
 *
 * Step 1 – Exponential Smoothing (α = 0.2):
 *   S(1) = 10
 *   S(2) = 0.2 * 12 + 0.8 * 10   = 10.40
 *   S(3) = 0.2 * 11 + 0.8 * 10.40 = 10.52
 *   S(4) = 0.2 * 13 + 0.8 * 10.52 = 11.02
 *   S(5) = 0.2 * 15 + 0.8 * 11.02 = 11.82
 *
 * Step 2 – Linear Trend:
 *   Trend = (15 - 10) / (5 - 1) = 1.25
 *   TrendForecast(1) = 15 + 1.25 = 16.25
 *
 * Step 3 – Final Forecast:
 *   F(1) = (11.82 + 16.25) / 2 = 14.04
 * </pre>
 *
 * <h3>Characteristics:</h3>
 *
 * <ul>
 *   <li>Works well with small datasets
 *   <li>Captures both short-term smoothing and long-term trend
 *   <li>Open-source friendly and easy to implement in Java
 * </ul>
 */
@Slf4j
@Service
public class ThetaMethodForecaster extends AbstractForecastService {

	private static final int MIN_DATA_POINTS = 2;

	/**
	 * Generates forecast using Theta Method algorithm.
	 *
	 * <p>Flow: Validate data → Extract values → Apply Theta algorithm → Create forecast object
	 *
	 * @param historicalData List of historical DataCount objects containing KPI values
	 * @param kpiId KPI identifier for logging and tracking
	 * @return List containing single forecast DataCount, or empty list if forecasting not possible
	 */
	@Override
	public List<DataCount> generateForecast(List<DataCount> historicalData, String kpiId) {
		List<DataCount> forecasts = new ArrayList<>();

		// Step 1: Validate input data availability
		if (!canForecast(historicalData, kpiId)) {
			return forecasts;
		}

		// Step 2: Extract numerical values from DataCount objects
		List<Double> values = extractValues(historicalData);
		if (values.size() < 2) {
			return forecasts;
		}

		// Step 3: Convert to array for algorithm processing
		double[] data = values.stream().mapToDouble(Double::doubleValue).toArray();

		// Step 4: Apply Theta method forecasting
		double prediction = thetaForecastNext(data);

		// Step 5: Extract metadata from last historical entry
		String projectName = historicalData.get(historicalData.size() - 1).getSProjectName();
		String kpiGroup = historicalData.get(historicalData.size() - 1).getKpiGroup();

		// Step 6: Create forecast DataCount object
		DataCount forecast =
				createForecastDataCount(prediction, projectName, kpiGroup, getModelType().getName());

		log.info(
				"Generated forecast for KPI {}: value={} [Theta Method]",
				kpiId,
				String.format("%.2f", prediction));

		forecasts.add(forecast);
		return forecasts;
	}

	/**
	 * Theta Method implementation: Combines exponential smoothing with linear trend.
	 *
	 * <p>Flow: Smooth data (Θ=0) → Calculate trend (Θ=2) → Average both components
	 *
	 * @param data Array of historical values to forecast from
	 * @return Forecasted value for next time period
	 */
	private double thetaForecastNext(double[] data) {
		int n = data.length;

		// Phase 1: Simple Exponential Smoothing (Θ=0 component)
		double alpha = 0.2; // smoothing parameter
		double level = data[0]; // initialize with first value

		// Apply exponential smoothing across all data points
		for (int i = 1; i < n; i++) {
			level = alpha * data[i] + (1 - alpha) * level;
		}

		// Phase 2: Linear Trend Component (Θ=2 component)
		double slope = (data[n - 1] - data[0]) / Math.max(1, (n - 1));
		double trendForecast = data[n - 1] + slope; // project one step ahead

		// Phase 3: Combine both components (weighted average)
		double finalForecast = (level + trendForecast) / 2.0;

		return finalForecast;
	}

	/**
	 * Extracts numerical values from DataCount objects.
	 *
	 * <p>Attempts to extract from bubble points first, falls back to direct value extraction.
	 *
	 * @param dataCounts List of DataCount objects to extract values from
	 * @return List of extracted numerical values
	 */
	@Override
	protected List<Double> extractValues(List<DataCount> dataCounts) {
		List<Double> values = new ArrayList<>();
		for (DataCount dataCount : dataCounts) {
			// Try bubble points first
			if (dataCount.getBubblePoints() != null && !dataCount.getBubblePoints().isEmpty()) {

				List<Double> bubbleSizes =
						dataCount.getBubblePoints().stream()
								.map(bp -> Double.parseDouble(bp.getSize()))
								.toList();

				values.addAll(bubbleSizes);
			} else {
				// Fallback to direct value extraction
				Double directValue = extractNumericValue(dataCount);
				if (directValue != null) {
					values.add(directValue);
				}
			}
		}
		return values;
	}

	/**
	 * Returns the forecasting model type identifier for this implementation.
	 *
	 * @return {@link ForecastingModel#THETA_METHOD} indicating this uses Theta Method algorithm
	 */
	@Override
	public ForecastingModel getModelType() {
		return ForecastingModel.THETA_METHOD;
	}

	/**
	 * Validates if forecasting is possible with given historical data.
	 *
	 * <p>Checks for:
	 *
	 * <ul>
	 *   <li>Non-null and non-empty historical data
	 *   <li>Minimum required data points (> 2)
	 * </ul>
	 *
	 * @param historicalData List of historical DataCount objects
	 * @param kpiId KPI identifier for logging
	 * @return true if forecasting is possible, false otherwise
	 */
	@Override
	public boolean canForecast(List<DataCount> historicalData, String kpiId) {
		if (CollectionUtils.isEmpty(historicalData)) {
			log.debug("Cannot forecast for KPI {}: No historical data", kpiId);
			return false;
		}

		List<Double> values = extractValues(historicalData);
		if (values.size() <= MIN_DATA_POINTS) {
			log.debug(
					"KPI {}: Theta method requires at least {} data points, got {}",
					kpiId,
					MIN_DATA_POINTS,
					values.size());
			return false;
		}

		return true;
	}
}
