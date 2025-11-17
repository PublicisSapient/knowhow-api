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

import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.apis.forecast.AbstractForecastService;
import com.publicissapient.kpidashboard.common.model.application.DataCount;

import smile.timeseries.ARMA;

import lombok.extern.slf4j.Slf4j;

/**
 * ARIMA Forecaster using Smile Machine Learning Library.
 * 
 * <p>
 * <b>What is ARIMA?</b>
 * </p>
 * ARIMA (AutoRegressive Integrated Moving Average) forecasts future values
 * based on:
 * <ul>
 * <li><b>AR(p)</b>: Past values influence the future (e.g., last 2 sprints
 * predict next sprint)</li>
 * <li><b>I(d)</b>: Removes trends by "differencing" - subtracting consecutive
 * values</li>
 * <li><b>MA(q)</b>: Adjusts predictions based on past forecast errors</li>
 * </ul>
 * 
 * <p>
 * <b>How It Works:</b>
 * </p>
 * <ol>
 * <li>Check if data has trends (non-stationary) by comparing variance in first
 * vs second half</li>
 * <li>If trending, apply differencing: [100, 105, 110] → [5, 5] to remove
 * trend</li>
 * <li>Fit ARMA model using Smile library with orders p and q based on data
 * size</li>
 * <li>Forecast next value</li>
 * <li>If we differenced, add back the last value: 5 + 110 = 115</li>
 * <li>Clamp to non-negative (for count-based KPIs)</li>
 * </ol>
 * 
 * <p>
 * <b>Example:</b>
 * </p>
 * 
 * <pre>
 * Input:  [100, 105, 110, 115, 120]
 * Output: 125 (continuing the +5 trend)
 * </pre>
 * 
 * <p>
 * <b>Best For:</b> KPIs with trends like velocity, defect counts, story points
 * </p>
 * <p>
 * <b>Requires:</b> Minimum 5 historical data points
 * </p>
 * 
 * @see smile.timeseries.ARMA
 * @see AbstractForecastService
 */
@Service
@Slf4j
public class ARIMAForecaster extends AbstractForecastService {

	private static final int MIN_ARIMA_DATA_POINTS = 5;

	@Override
	public ForecastingModel getModelType() {
		return ForecastingModel.ARIMA;
	}

	/**
	 * ARIMA-specific validation requiring minimum 5 data points. After differencing
	 * (d=1), we need at least 4 points to fit ARMA(1,1). Therefore, original data
	 * must have at least 5 points.
	 */
	@Override
	public boolean canForecast(List<DataCount> historicalData, String kpiId) {
		if (!super.canForecast(historicalData, kpiId)) {
			return false;
		}

		List<Double> values = extractValues(historicalData);
		if (values.size() < MIN_ARIMA_DATA_POINTS) {
			log.debug("KPI {}: ARIMA requires at least {} data points, got {}", kpiId, MIN_ARIMA_DATA_POINTS,
					values.size());
			return false;
		}

		return true;
	}

	@Override
	public List<DataCount> generateForecast(List<DataCount> historicalData, String kpiId) {
		List<DataCount> forecasts = new ArrayList<>();

		// Step 1: Validate data
		if (!canForecast(historicalData, kpiId)) {
			return forecasts;
		}

		List<Double> values = extractValues(historicalData);

		try {
			log.debug("KPI {}: Starting ARIMA forecast with {} data points", kpiId, values.size());
			double[] data = values.stream().mapToDouble(Double::doubleValue).toArray();

			// Step 2: Check if data needs differencing (has trends)
			boolean hasVarianceShift = hasVarianceShift(values);
			int d = hasVarianceShift ? 1 : 0;

			double[] workingData = data;
			if (d == 1) {
				workingData = difference(data);
				log.debug("KPI {}: Applied differencing to remove trend", kpiId);
			}

			// Step 3: Determine ARMA orders based on data size
			// Smile library constraint: p + q must be < number of data points
			int n = workingData.length;
			int p = (n > 8) ? 2 : 1; // Use AR(2) for larger datasets, AR(1) for smaller
			int q = (n > 4) ? 1 : 0; // Use MA(1) if enough data, otherwise MA(0)

			if (p + q >= n) {
				log.warn("KPI {}: Not enough data points ({}) for ARMA({},{})", kpiId, n, p, q);
				return forecasts;
			}

			// Step 4: Fit ARMA model using Smile library
			log.debug("KPI {}: Fitting ARMA({},{}) on {} points", kpiId, p, q, n);
			ARMA arma = ARMA.fit(workingData, p, q);

			// Step 5: Generate forecast
			double[] forecastArray = arma.forecast(1);
			double forecastValue = forecastArray[0];

			// Step 6: If we differenced, add back the last actual value
			if (d == 1) {
				forecastValue = data[data.length - 1] + forecastValue;
				log.debug("KPI {}: Integrated forecast: {} + {} = {}", kpiId, data[data.length - 1], forecastArray[0],
						forecastValue);
			}

			// Step 7: Ensure non-negative (for count-based KPIs)
			forecastValue = Math.max(0, forecastValue);

			// Step 8: Create forecast data object
			String projectName = historicalData.get(historicalData.size() - 1).getSProjectName();
			String kpiGroup = historicalData.get(historicalData.size() - 1).getKpiGroup();
			DataCount forecast = createForecastDataCount(forecastValue, projectName, kpiGroup,
					getModelType().getName());
			forecasts.add(forecast);

			log.info("KPI {}: ARIMA({},{},{}) forecast = {} (last actual = {})", kpiId, p, d, q,
					String.format("%.2f", forecastValue), String.format("%.2f", values.get(values.size() - 1)));
		} catch (IllegalArgumentException e) {
			log.error("KPI {}: Invalid ARMA parameters - {}", kpiId, e.getMessage());
		} catch (Exception e) {
			log.error("KPI {}: Failed to generate ARIMA forecast - {}", kpiId, e.getMessage(), e);
		}

		return forecasts;
	}

	/**
	 * Simple variance shift detection to determine if differencing is needed.
	 * Compares variance in first half vs second half of the data. If variance is
	 * significantly different, data likely has a trend.
	 * 
	 * @param values
	 *            Historical data points
	 * @return true if variance shifts significantly (needs differencing), false
	 *         otherwise
	 */
	private boolean hasVarianceShift(List<Double> values) {
		if (values.size() < 4) {
			return false; // Too small to determine, assume stationary
		}

		int mid = values.size() / 2;
		double var1 = calculateVariance(values.subList(0, mid));
		double var2 = calculateVariance(values.subList(mid, values.size()));

		// Handle edge cases
		if (var1 < 1e-10 && var2 < 1e-10)
			return false; // Both constant
		if (var1 < 1e-10 || var2 < 1e-10)
			return true; // One varying, one constant

		// If one half has 2x+ the variance of the other, apply differencing
		double ratio = Math.max(var1, var2) / Math.min(var1, var2);
		return ratio >= 2.0;
	}

	/**
	 * Calculate variance of a list of values. Variance = average of squared
	 * differences from the mean.
	 */
	private double calculateVariance(List<Double> values) {
		double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		return values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0.0);
	}

	/**
	 * Apply first-order differencing: each value becomes (current - previous). This
	 * removes linear trends. Example: [100, 105, 110] → [5, 5]
	 */
	private double[] difference(double[] data) {
		if (data.length < 2) {
			return data;
		}
		double[] diff = new double[data.length - 1];
		for (int i = 1; i < data.length; i++) {
			diff[i - 1] = data[i] - data[i - 1];
		}
		return diff;
	}
}