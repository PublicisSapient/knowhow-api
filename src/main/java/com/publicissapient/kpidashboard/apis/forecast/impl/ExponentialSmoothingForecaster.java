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
 * Exponential Smoothing forecaster using Apache Commons Math.
 */
@Service
@Slf4j
public class ExponentialSmoothingForecaster extends AbstractForecastService {

	private static final double DEFAULT_ALPHA = 0.3;

	@Override
	public ForecastingModel getModelType() {
		return ForecastingModel.EXPONENTIAL_SMOOTHING;
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
			log.debug("Starting Exponential Smoothing forecast for KPI {} with {} data points", kpiId, values.size());

			DescriptiveStatistics stats = new DescriptiveStatistics();
			values.forEach(stats::addValue);

			double alpha = calculateAlpha(stats);
			double smoothedValue = applyExponentialSmoothing(values, alpha);

			log.debug("KPI {}: mean={}, std={}, cv={}, alpha={}", kpiId, String.format("%.2f", stats.getMean()),
					String.format("%.2f", stats.getStandardDeviation()),
					String.format("%.2f", stats.getStandardDeviation() / (stats.getMean() + 0.001)),
					String.format("%.2f", alpha));

			String projectName = historicalData.get(historicalData.size() - 1).getSProjectName();
			String kpiGroup = historicalData.get(historicalData.size() - 1).getKpiGroup();

			DataCount forecast = createForecastDataCount(smoothedValue, projectName, kpiGroup,
					getModelType().getName());
			forecasts.add(forecast);

			log.info("Generated forecast for KPI {}: value={} [Exponential Smoothing, alpha={}]", kpiId,
					String.format("%.2f", smoothedValue), String.format("%.2f", alpha));

		} catch (Exception e) {
			log.error("Error in Exponential Smoothing forecast for KPI {}: {}", kpiId, e.getMessage(), e);
		}

		return forecasts;
	}

	private double applyExponentialSmoothing(List<Double> values, double alpha) {
		double smoothed = values.get(0);
		log.debug("Smoothing: t=0, value={}, smoothed={}", String.format("%.2f", values.get(0)),
				String.format("%.2f", smoothed));

		for (int i = 1; i < values.size(); i++) {
			double prev = smoothed;
			smoothed = alpha * values.get(i) + (1 - alpha) * smoothed;
			log.debug("Smoothing: t={}, value={}, smoothed={} (prev={})", i, String.format("%.2f", values.get(i)),
					String.format("%.2f", smoothed), String.format("%.2f", prev));
		}

		return smoothed;
	}

	private double calculateAlpha(DescriptiveStatistics stats) {
		if (stats.getN() < 3) {
			return DEFAULT_ALPHA;
		}

		double cv = stats.getStandardDeviation() / (stats.getMean() + 0.001);

		if (cv > 0.5)
			return 0.5;
		if (cv > 0.3)
			return 0.4;
		return DEFAULT_ALPHA;
	}
}
