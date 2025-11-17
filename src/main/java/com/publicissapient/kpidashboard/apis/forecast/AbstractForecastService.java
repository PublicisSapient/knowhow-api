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

package com.publicissapient.kpidashboard.apis.forecast;

import static com.publicissapient.kpidashboard.apis.util.KPIExcelUtility.roundingOff;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.forecast.service.ForecastService;
import com.publicissapient.kpidashboard.common.model.application.DataCount;

import lombok.extern.slf4j.Slf4j;

/** Abstract base class for forecast service implementations. */
@Slf4j
@Component
public abstract class AbstractForecastService implements ForecastService {

	protected static final int MIN_DATA_POINTS = 2;

	/**
	 * Validate if historical data is sufficient for forecasting.
	 *
	 * @param historicalData Historical data points
	 * @return true if data is valid
	 */
	@Override
	public boolean canForecast(List<DataCount> historicalData, String kpiId) {
		if (CollectionUtils.isEmpty(historicalData)) {
			log.debug("Cannot forecast for KPI {}: No historical data", kpiId);
			return false;
		}

		List<Double> values = extractValues(historicalData);
		if (values.size() < MIN_DATA_POINTS) {
			log.debug(
					"Cannot forecast for KPI {}: Insufficient data points (need at least {}, got {})",
					kpiId,
					MIN_DATA_POINTS,
					values.size());
			return false;
		}

		return true;
	}

	/**
	 * Extract numeric values from DataCount objects.
	 *
	 * @param dataCounts List of DataCount objects
	 * @return List of Double values
	 */
	protected List<Double> extractValues(List<DataCount> dataCounts) {
		if (CollectionUtils.isEmpty(dataCounts)) {
			return new ArrayList<>();
		}

		return dataCounts.stream()
				.map(this::extractNumericValue)
				.filter(val -> val != null && !val.isNaN() && !val.isInfinite())
				.collect(Collectors.toList());
	}

	/**
	 * Extract numeric value from a DataCount object.
	 *
	 * @param dataCount DataCount object
	 * @return Double value or null
	 */
	protected Double extractNumericValue(DataCount dataCount) {
		if (dataCount == null) {
			return null;
		}

		Object value = dataCount.getValue();
		if (value == null) {
			return null;
		}

		try {
			if (value instanceof Number) {
				return ((Number) value).doubleValue();
			} else if (value instanceof String strValue && NumberUtils.isCreatable(strValue)) {
				return Double.parseDouble(strValue);
			}
		} catch (Exception e) {
			log.warn("Failed to extract numeric value from DataCount: {}", value, e);
		}

		return null;
	}

	/**
	 * Create a forecast DataCount object.
	 *
	 * @param forecastValue Forecasted value
	 * @param projectName Project name
	 * @param kpiGroup KPI group identifier
	 * @param forecastingModel The forecasting model used
	 * @return DataCount object with forecast
	 */
	protected DataCount createForecastDataCount(
			Double forecastValue, String projectName, String kpiGroup, String forecastingModel) {
		DataCount forecast = new DataCount();
		forecast.setData(String.valueOf(roundingOff(forecastValue)));
		forecast.setValue(roundingOff(forecastValue));
		forecast.setSProjectName(projectName);
		forecast.setKpiGroup(kpiGroup);
		forecast.setForecastingModel(forecastingModel);
		return forecast;
	}
}
