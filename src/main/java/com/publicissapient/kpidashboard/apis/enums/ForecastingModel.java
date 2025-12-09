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

package com.publicissapient.kpidashboard.apis.enums;

import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Enum representing different forecasting models available for KPI prediction. */
@Getter
@AllArgsConstructor
public enum ForecastingModel {

	/** Simple Exponential Smoothing: For stable trends with gradual variation */
	EXPONENTIAL_SMOOTHING(
			"exponentialSmoothing",
			"Exponential Smoothing",
			"Simple exponential smoothing for trend-based data"),

	/** Holt-Winters Exponential Smoothing: For data with trend and seasonality */
	HOLT_WINTERS(
			"holtWinters", "Holt-Winters", "Triple exponential smoothing for trend and seasonal data"),

	/** ARIMA (AutoRegressive Integrated Moving Average): For stationary time series */
	ARIMA("arima", "ARIMA", "AutoRegressive Integrated Moving Average for stationary series"),

	/** SARIMA (Seasonal ARIMA): For seasonal time series data */
	SARIMA("sarima", "SARIMA", "Seasonal ARIMA for data with seasonal components"),

	/** Linear Regression: For trending data with linear relationships */
	LINEAR_REGRESSION(
			"linearRegression", "Linear Regression", "Simple linear regression for trending data"),

	/** Multiple Linear Regression: For data with multiple predictors */
	MULTIVARIATE_REGRESSION(
			"multivariateRegression",
			"Multivariate Regression",
			"Multiple linear regression with multiple predictors"),

	/** Simple Moving Average: For smoothing noisy data */
	MOVING_AVERAGE("movingAverage", "Moving Average", "Simple moving average for noise reduction"),

	/** Random Forest Regression: For complex, nonlinear relationships */
	RANDOM_FOREST(
			"randomForest", "Random Forest", "Ensemble method for complex nonlinear relationships"),

	/** Gradient Boosting: For high-performance regression */
	GRADIENT_BOOSTING(
			"gradientBoosting",
			"Gradient Boosting",
			"Boosting algorithm for high-performance prediction"),

	/** LSTM Neural Network: For long-term dependencies and sequences */
	LSTM("lstm", "LSTM", "Long Short-Term Memory neural network for sequences"),

	/** Prophet: For data with multiple personalities and trends */
	PROPHET("prophet", "Prophet", "Facebook's forecasting tool for seasonal data"),

	/** Logistic Growth: For data with saturation/ceiling effects */
	LOGISTIC_GROWTH(
			"logisticGrowth", "Logistic Growth", "S-shaped growth model for saturation effects"),

	THETA_METHOD("thetaMethod", "Theta Method",
            "non-linear time-series forecasting technique that smooths and combines trend curves to predict future values accurately from small datasets.");

	private final String name;
	private final String displayName;
	private final String description;

	/**
	 * Find a ForecastingModel by its name.
	 *
	 * @param name The model name to search for
	 * @return Optional containing the ForecastingModel if found
	 */
	public static Optional<ForecastingModel> fromName(String name) {
		if (name == null || name.trim().isEmpty()) {
			return Optional.empty();
		}

		for (ForecastingModel model : values()) {
			if (model.getName().equalsIgnoreCase(name.trim())) {
				return Optional.of(model);
			}
		}
		return Optional.empty();
	}
}
