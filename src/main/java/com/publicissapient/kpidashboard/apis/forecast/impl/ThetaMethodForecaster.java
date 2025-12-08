/*
 * Sample Usage:
 * Input: [10, 12, 11, 13, 15] (historical KPI values)
 * Process: Exponential smoothing + Linear trend
 * Output: 16.2 (next forecasted value)
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

@Slf4j
@Service
public class ThetaMethodForecaster extends AbstractForecastService {

	private static final int MIN_DATA_POINTS = 2;

	/**
	 * Generates forecast using Theta Method algorithm Flow: Validate data -> Extract values -> Apply
	 * Theta algorithm -> Create forecast object
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
	 * Theta Method implementation: Combines exponential smoothing with linear trend Flow: Smooth data
	 * (Θ=0) -> Calculate trend (Θ=2) -> Average both components
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


	/** Extract values from bubble points or direct DataCount value */
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
	 * @return ForecastingModel.THETA_METHOD indicating this uses Theta Method algorithm
	 */
	@Override
	public ForecastingModel getModelType() {
		return ForecastingModel.THETA_METHOD;
	}

	/** Data count validation */
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
