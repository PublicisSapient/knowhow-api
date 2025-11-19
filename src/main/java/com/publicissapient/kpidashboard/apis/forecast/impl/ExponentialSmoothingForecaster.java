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

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.apis.forecast.AbstractForecastService;
import com.publicissapient.kpidashboard.common.model.application.DataCount;

import lombok.extern.slf4j.Slf4j;

/**
 * Exponential Smoothing forecaster using Apache Commons Math library.
 *
 * <p>This forecaster implements Simple Exponential Smoothing (SES), a time series forecasting
 * method that applies exponentially decreasing weights to past observations. More recent data
 * points have higher weights, making this method responsive to recent trends while smoothing out
 * noise.
 *
 * <h3>Smoothing Formula:</h3>
 *
 * <pre>
 * S(t) = α * Y(t) + (1 - α) * S(t-1)
 *
 * Where:
 *   S(t)   = Smoothed value at time t
 *   Y(t)   = Actual value at time t
 *   S(t-1) = Previous smoothed value
 *   α      = Smoothing parameter (0 < α ≤ 1)
 * </pre>
 *
 * <h3>Alpha (α) Parameter:</h3>
 *
 * <ul>
 *   <li><b>High α (0.5)</b>: More weight to recent data, responsive to changes (high volatility)
 *   <li><b>Medium α (0.4)</b>: Balanced responsiveness (moderate volatility)
 *   <li><b>Low α (0.3)</b>: More smoothing, less reactive (stable data)
 * </ul>
 *
 * <p>Alpha is automatically calculated based on Coefficient of Variation (CV = std/mean):
 *
 * <ul>
 *   <li>CV > 0.5 → α = 0.5 (high volatility)
 *   <li>CV > 0.3 → α = 0.4 (moderate volatility)
 *   <li>CV ≤ 0.3 → α = 0.3 (low volatility)
 * </ul>
 *
 * <h3>Dependencies:</h3>
 *
 * <ul>
 *   <li>Apache Commons Math3 {@link DescriptiveStatistics} for statistical calculations
 *   <li>Extends {@link AbstractForecastService} for common forecasting utilities
 * </ul>
 *
 * <h3>Example:</h3>
 *
 * <pre>
 * Historical data: [10, 12, 11, 13, 12]
 * CV = 0.25 (stable) → α = 0.3
 *
 * S(1) = 0.3 * 12 + 0.7 * 10 = 10.6
 * S(2) = 0.3 * 11 + 0.7 * 10.6 = 10.72
 * S(3) = 0.3 * 13 + 0.7 * 10.72 = 11.40
 * S(4) = 0.3 * 12 + 0.7 * 11.40 = 11.58 ← Forecast
 * </pre>
 *
 * @see AbstractForecastService
 * @see ForecastingModel#EXPONENTIAL_SMOOTHING
 */
@Service
@Slf4j
public class ExponentialSmoothingForecaster extends AbstractForecastService {

	private static final double DEFAULT_ALPHA = 0.3;

	@Override
	public ForecastingModel getModelType() {
		return ForecastingModel.EXPONENTIAL_SMOOTHING;
	}

	/**
	 * Generates an exponential smoothing forecast for the next time period.
	 *
	 * <p>The smoothing process uses the formula: <b>S(t) = α * Y(t) + (1-α) * S(t-1)</b>
	 *
	 * <ul>
	 *   <li>Higher α values respond more to recent changes (volatile data)
	 *   <li>Lower α values produce smoother forecasts (stable data)
	 *   <li>Alpha is automatically selected based on Coefficient of Variation
	 * </ul>
	 *
	 * @param historicalData List of historical DataCount objects containing past KPI values. Must
	 *     contain at least 2 valid numeric values.
	 * @param kpiId The identifier of the KPI being forecasted (used for logging).
	 * @return List containing a single DataCount with the forecasted value, or empty list if:
	 *     <ul>
	 *       <li>Insufficient data (less than minimum required)
	 *       <li>No valid numeric values could be extracted
	 *       <li>Exception occurs during calculation
	 *     </ul>
	 *
	 * @see #applyExponentialSmoothing(List, double)
	 * @see #calculateAlpha(DescriptiveStatistics)
	 * @see AbstractForecastService#canForecast(List, String)
	 * @see AbstractForecastService#extractValues(List)
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
			log.debug(
					"Starting Exponential Smoothing forecast for KPI {} with {} data points",
					kpiId,
					values.size());

			// Step 1: Calculate descriptive statistics for volatility assessment
			DescriptiveStatistics stats = new DescriptiveStatistics();
			values.forEach(stats::addValue);

			// Step 2: Determine optimal smoothing parameter (alpha) based on data
			// volatility
			// Higher volatility (high CV) → higher alpha → more responsive to recent
			// changes
			double alpha = calculateAlpha(stats);

			// Step 3: Apply exponential smoothing iteratively through the time series
			// Each value is smoothed using: S(t) = α * Y(t) + (1-α) * S(t-1)
			double smoothedValue = applyExponentialSmoothing(values, alpha);

			log.debug(
					"KPI {}: mean={}, std={}, cv={}, alpha={}",
					kpiId,
					String.format("%.2f", stats.getMean()),
					String.format("%.2f", stats.getStandardDeviation()),
					String.format("%.2f", stats.getStandardDeviation() / (stats.getMean() + 0.001)),
					String.format("%.2f", alpha));

			String projectName = historicalData.get(historicalData.size() - 1).getSProjectName();
			String kpiGroup = historicalData.get(historicalData.size() - 1).getKpiGroup();

			DataCount forecast =
					createForecastDataCount(smoothedValue, projectName, kpiGroup, getModelType().getName());
			forecasts.add(forecast);

			log.info(
					"Generated forecast for KPI {}: value={} [Exponential Smoothing, alpha={}]",
					kpiId,
					String.format("%.2f", smoothedValue),
					String.format("%.2f", alpha));

		} catch (Exception e) {
			log.error("Error in Exponential Smoothing forecast for KPI {}: {}", kpiId, e.getMessage(), e);
		}

		return forecasts;
	}

	/**
	 * Applies exponential smoothing formula iteratively through the time series.
	 *
	 * <p>Uses the formula: <b>S(t) = α * Y(t) + (1 - α) * S(t-1)</b>
	 *
	 * <ul>
	 *   <li>S(t): Smoothed value at time t
	 *   <li>Y(t): Actual observed value at time t
	 *   <li>S(t-1): Previous smoothed value
	 *   <li>α: Smoothing parameter (0 < α ≤ 1)
	 * </ul>
	 *
	 * <p>The process starts with S(0) = Y(0) (first value is used as initial smoothed value), then
	 * iteratively applies the formula for each subsequent observation.
	 *
	 * @param values List of historical values to smooth
	 * @param alpha Smoothing parameter (weight given to most recent observation)
	 * @return Final smoothed value which serves as the forecast
	 */
	private double applyExponentialSmoothing(List<Double> values, double alpha) {
		// Initialize: First smoothed value equals first observed value
		double smoothed = values.get(0);
		log.debug(
				"Smoothing: t=0, value={}, smoothed={}",
				String.format("%.2f", values.get(0)),
				String.format("%.2f", smoothed));

		// Iteratively apply: S(t) = α * Y(t) + (1-α) * S(t-1)
		for (int i = 1; i < values.size(); i++) {
			double prev = smoothed;
			// Core exponential smoothing formula
			// alpha weight to new observation, (1-alpha) weight to previous smoothed value
			smoothed = alpha * values.get(i) + (1 - alpha) * smoothed;
			log.debug(
					"Smoothing: t={}, value={}, smoothed={} (prev={})",
					i,
					String.format("%.2f", values.get(i)),
					String.format("%.2f", smoothed),
					String.format("%.2f", prev));
		}

		return smoothed;
	}

	/**
	 * Calculates the optimal smoothing parameter (alpha) based on data volatility.
	 *
	 * <p>Uses Coefficient of Variation (CV = std/mean) to assess data volatility:
	 *
	 * <ul>
	 *   <li><b>High volatility (CV > 0.5)</b>: Returns α = 0.5 <br>
	 *       More weight to recent observations for responsive forecasts
	 *   <li><b>Moderate volatility (0.3 < CV ≤ 0.5)</b>: Returns α = 0.4 <br>
	 *       Balanced approach between responsiveness and smoothing
	 *   <li><b>Low volatility (CV ≤ 0.3)</b>: Returns α = 0.3 <br>
	 *       More smoothing to filter out noise in stable data
	 *   <li><b>Insufficient data (n < 3)</b>: Returns default α = 0.3 <br>
	 *       Conservative default when statistics are unreliable
	 * </ul>
	 *
	 * <p><b>Coefficient of Variation (CV):</b> A standardized measure of dispersion. CV = σ / μ where
	 * σ is standard deviation and μ is mean. Higher CV indicates more variability relative to the
	 * mean.
	 *
	 * @param stats DescriptiveStatistics object containing mean and standard deviation
	 * @return Alpha value between 0.3 and 0.5 based on data volatility
	 */
	private double calculateAlpha(DescriptiveStatistics stats) {
		// Require at least 3 data points for reliable statistics
		if (stats.getN() < 3) {
			return DEFAULT_ALPHA;
		}

		// Calculate Coefficient of Variation: CV = std / mean
		// Add small epsilon (0.001) to avoid division by zero
		double cv = stats.getStandardDeviation() / (stats.getMean() + 0.001);

		// High volatility: Use higher alpha for more responsiveness
		if (cv > 0.5) return 0.5;
		// Moderate volatility: Use balanced alpha
		if (cv > 0.3) return 0.4;
		// Low volatility: Use lower alpha for more smoothing
		return DEFAULT_ALPHA;
	}
}
