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

import lombok.extern.slf4j.Slf4j;

/**
 * SARIMA Forecaster for seasonal time series data.
 *
 * <p>For non-trend KPIs with seasonal patterns (e.g., release cycles). Uses simplified seasonal
 * decomposition approach.
 */
@Service
@Slf4j
public class SARIMAForecaster extends AbstractForecastService {

	private static final int MIN_SARIMA_DATA_POINTS = 8; // Need 2 seasonal cycles minimum
	private static final int DEFAULT_SEASONAL_PERIOD = 4; // Quarterly releases

	@Override
	public ForecastingModel getModelType() {
		return ForecastingModel.SARIMA;
	}

	@Override
	public boolean canForecast(List<DataCount> historicalData, String kpiId) {
		if (!super.canForecast(historicalData, kpiId)) {
			return false;
		}

		List<Double> values = extractValues(historicalData);
		if (values.size() < MIN_SARIMA_DATA_POINTS) {
			log.debug(
					"KPI {}: SARIMA requires at least {} data points, got {}",
					kpiId,
					MIN_SARIMA_DATA_POINTS,
					values.size());
			return false;
		}

		return true;
	}

	@Override
	public List<DataCount> generateForecast(List<DataCount> historicalData, String kpiId) {
		List<DataCount> forecasts = new ArrayList<>();

		if (!canForecast(historicalData, kpiId)) {
			return forecasts;
		}

		List<Double> values = extractValues(historicalData);

		try {
			log.debug("KPI {}: Starting SARIMA forecast with {} data points", kpiId, values.size());

			// Detect seasonal period (for release KPIs, typically quarterly)
			int seasonalPeriod = detectSeasonalPeriod(values);

			// Simple seasonal decomposition for non-trend data
			double forecastValue = generateSeasonalForecast(values, seasonalPeriod);

			// Ensure non-negative
			forecastValue = Math.max(0, forecastValue);

			// Create forecast data object
			String projectName = historicalData.get(historicalData.size() - 1).getSProjectName();
			String kpiGroup = historicalData.get(historicalData.size() - 1).getKpiGroup();
			DataCount forecast =
					createForecastDataCount(forecastValue, projectName, kpiGroup, getModelType().getName());
			forecasts.add(forecast);

			log.info(
					"KPI {}: SARIMA forecast = {} (seasonal period = {})",
					kpiId,
					String.format("%.2f", forecastValue),
					seasonalPeriod);

		} catch (Exception e) {
			log.error("KPI {}: Failed to generate SARIMA forecast - {}", kpiId, e.getMessage(), e);
		}

		return forecasts;
	}

	/** Detect seasonal period for release KPIs. For non-trend data, look for repeating patterns. */
	private int detectSeasonalPeriod(List<Double> values) {
		int n = values.size();

		// For release KPIs, common periods are 3, 4, 6, 12
		int[] candidatePeriods = {3, 4, 6, 12};

		for (int period : candidatePeriods) {
			if (n >= 2 * period && hasSeasonalPattern(values, period)) {
				return period;
			}
		}

		return DEFAULT_SEASONAL_PERIOD; // Default to quarterly
	}

	/** Check if data has seasonal pattern for given period. */
	private boolean hasSeasonalPattern(List<Double> values, int period) {
		if (values.size() < 2 * period) return false;

		double correlation = calculateSeasonalCorrelation(values, period);
		return correlation > 0.3; // Threshold for seasonal pattern
	}

	/** Calculate correlation between seasonal lags. */
	private double calculateSeasonalCorrelation(List<Double> values, int period) {
		int n = values.size();
		List<Double> current = new ArrayList<>();
		List<Double> lagged = new ArrayList<>();

		for (int i = period; i < n; i++) {
			current.add(values.get(i));
			lagged.add(values.get(i - period));
		}

		return calculateCorrelation(current, lagged);
	}

	/** Calculate Pearson correlation coefficient. */
	private double calculateCorrelation(List<Double> x, List<Double> y) {
		if (x.size() != y.size() || x.isEmpty()) return 0.0;

		double meanX = x.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		double meanY = y.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

		double numerator = 0.0;
		double sumXX = 0.0;
		double sumYY = 0.0;

		for (int i = 0; i < x.size(); i++) {
			double dx = x.get(i) - meanX;
			double dy = y.get(i) - meanY;
			numerator += dx * dy;
			sumXX += dx * dx;
			sumYY += dy * dy;
		}

		double denominator = Math.sqrt(sumXX * sumYY);
		return denominator == 0 ? 0 : numerator / denominator;
	}

	/**
	 * Generate forecast using seasonal decomposition. For non-trend KPIs, use seasonal naive approach
	 * with smoothing.
	 */
	private double generateSeasonalForecast(List<Double> values, int seasonalPeriod) {
		int n = values.size();

		// Calculate seasonal indices
		double[] seasonalIndices = calculateSeasonalIndices(values, seasonalPeriod);

		// Get the seasonal index for next period
		int nextSeasonIndex = n % seasonalPeriod;
		double seasonalComponent = seasonalIndices[nextSeasonIndex];

		// Calculate recent trend-adjusted mean (for non-trend, this is just recent
		// mean)
		double recentMean = calculateRecentMean(values, Math.min(seasonalPeriod * 2, n));

		// Combine seasonal and level components
		return recentMean * seasonalComponent;
	}

	/** Calculate seasonal indices using classical decomposition. */
	private double[] calculateSeasonalIndices(List<Double> values, int period) {
		double[] indices = new double[period];
		double[] sums = new double[period];
		int[] counts = new int[period];

		// Calculate overall mean
		double overallMean = values.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
		if (overallMean == 0) overallMean = 1.0;

		// Calculate seasonal ratios
		for (int i = 0; i < values.size(); i++) {
			int seasonIndex = i % period;
			sums[seasonIndex] += values.get(i) / overallMean;
			counts[seasonIndex]++;
		}

		// Average the ratios for each season
		for (int i = 0; i < period; i++) {
			indices[i] = counts[i] > 0 ? sums[i] / counts[i] : 1.0;
		}

		// Normalize so seasonal indices sum to period
		double sumIndices = 0.0;
		for (double index : indices) {
			sumIndices += index;
		}

		if (sumIndices > 0) {
			for (int i = 0; i < period; i++) {
				indices[i] = indices[i] * period / sumIndices;
			}
		} else {
			// If all zeros, use neutral seasonal pattern
			for (int i = 0; i < period; i++) {
				indices[i] = 1.0;
			}
		}

		return indices;
	}

	/** Calculate recent mean for level component. */
	private double calculateRecentMean(List<Double> values, int recentPeriods) {
		int start = Math.max(0, values.size() - recentPeriods);
		return values.subList(start, values.size()).stream()
				.mapToDouble(Double::doubleValue)
				.average()
				.orElse(0.0);
	}
}
