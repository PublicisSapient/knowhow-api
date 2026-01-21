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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.apis.forecast.service.ForecastService;
import com.publicissapient.kpidashboard.apis.model.IterationKpiValue;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manager service to coordinate forecasting operations. Routes forecast requests to appropriate
 * forecasting implementation based on KPI configuration.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ForecastingManager {

	private final ConfigHelperService configHelperService;
	private final List<ForecastService> forecastServices;

	private Map<ForecastingModel, ForecastService> forecasterMap;

	@PostConstruct
	public void init() {
		forecasterMap = new EnumMap<>(ForecastingModel.class);
		if (forecastServices != null) {
			forecastServices.forEach(service -> forecasterMap.put(service.getModelType(), service));
			log.info("Initialized forecasting manager with {} forecasters", forecasterMap.size());
		}
	}

	/**
	 * Generate forecasts for given data if KPI is configured for forecasting.
	 *
	 * @param dataCounts Historical data points
	 * @param kpiId KPI identifier
	 * @return List of forecast DataCount objects
	 */
	public List<DataCount> generateForecasts(List<DataCount> dataCounts, String kpiId) {
		List<DataCount> forecasts = new ArrayList<>();

		if (dataCounts == null || dataCounts.isEmpty()) {
			return forecasts;
		}

		try {
			// Get KPI master configuration
			KpiMaster kpiMaster = getKpiMaster(kpiId);
			if (kpiMaster == null || StringUtils.isEmpty(kpiMaster.getForecastModel())) {
				log.debug("No forecast model configured for KPI {}", kpiId);
				return forecasts;
			}

			// Get forecast model from configuration
			Optional<ForecastingModel> modelOpt = ForecastingModel.fromName(kpiMaster.getForecastModel());
			if (modelOpt.isEmpty()) {
				log.warn(
						"Invalid forecast model '{}' configured for KPI {}",
						kpiMaster.getForecastModel(),
						kpiId);
				return forecasts;
			}

			ForecastingModel model = modelOpt.get();
			ForecastService forecaster = forecasterMap.get(model);

			if (forecaster == null) {
				log.warn("No forecaster implementation found for model {}", model.getDisplayName());
				return forecasts;
			}

			// Generate forecasts
			if (forecaster.canForecast(dataCounts, kpiId)) {
				forecasts = forecaster.generateForecast(dataCounts, kpiId);
				log.debug(
						"Generated {} forecast(s) for KPI {} using {}",
						forecasts.size(),
						kpiId,
						model.getDisplayName());
			}

		} catch (Exception e) {
			log.error("Error generating forecasts for KPI {}", kpiId, e);
		}

		return forecasts;
	}

	/**
	 * Generate forecasts for given data for non KPI specific scenario.
	 *
	 * @param dataCounts Historical data points
	 * @param forecastingModel Forecasting model to use
	 * @return List of forecast DataCount objects
	 */
	public List<DataCount> generateForecastsForNonKPI(
			List<DataCount> dataCounts, ForecastingModel forecastingModel) {
		List<DataCount> forecasts = new ArrayList<>();

		if (dataCounts == null || dataCounts.isEmpty()) {
			return forecasts;
		}

		try {
			log.debug(
					"Generating forecast for non configured KPI with model {}",
					forecastingModel.getDisplayName());
			ForecastService forecaster = forecasterMap.get(forecastingModel);

			if (forecaster == null) {
				log.warn(
						"No forecaster implementation found for model {}", forecastingModel.getDisplayName());
				return forecasts;
			}

			if (forecaster.canForecast(dataCounts, null)) {
				forecasts = forecaster.generateForecast(dataCounts, null);
				log.debug(
						"Generated {} forecast(s) using {}",
						forecasts.size(),
						forecastingModel.getDisplayName());
			}
		} catch (Exception e) {
			log.error("Error generating forecasts for model {}", forecastingModel.getDisplayName(), e);
		}
		return forecasts;
	}

	/**
	 * Add forecasts to DataCount if forecasting is configured for the KPI.
	 *
	 * @param dataCount Target DataCount to add forecasts to
	 * @param historicalData Historical data points for forecasting
	 * @param kpiId KPI identifier Maturity KPIs
	 */
	public <T> void addForecastsToDataCount(
			T dataCount, List<DataCount> historicalData, String kpiId) {
		if (dataCount == null || historicalData == null || historicalData.isEmpty()) {
			return;
		}

		try {
			List<DataCount> forecasts = generateForecasts(historicalData, kpiId);
			if (!forecasts.isEmpty()) {
				if (dataCount instanceof DataCount dc) {
					dc.setForecasts(forecasts);
				} else if (dataCount instanceof IterationKpiValue ikv) ikv.setForecasts(forecasts);
				else if (dataCount instanceof DataCountGroup dcg) dcg.setForecasts(forecasts);
			}
		} catch (Exception e) {
			log.error("Error adding forecasts for KPI {}", kpiId, e);
		}
	}

	/**
	 * Add forecasts to DataCount for non KPI specific scenario.
	 *
	 * @param dataCount Target DataCount to add forecasts to
	 * @param historicalData Historical data points for forecasting
	 * @param model Forecasting model to use
	 */
	public <T> void addForecastsToDataCountForNonKPI(
			T dataCount, List<DataCount> historicalData, ForecastingModel model) {
		if (dataCount == null || historicalData == null || historicalData.isEmpty()) {
			return;
		}

		try {
			List<DataCount> forecasts = generateForecastsForNonKPI(historicalData, model);
			if (!forecasts.isEmpty() && dataCount instanceof DataCount dc) {
				dc.setForecasts(forecasts);
			}
		} catch (Exception e) {
			log.error("Error adding forecasts for forecasting model {}", model.getDisplayName(), e);
		}
	}

	/**
	 * Get KPI master configuration from cache.
	 *
	 * @param kpiId KPI identifier
	 * @return KpiMaster object or null
	 */
	private KpiMaster getKpiMaster(String kpiId) {
		try {
			List<KpiMaster> masterList = (List<KpiMaster>) configHelperService.loadKpiMaster();
			Map<String, KpiMaster> kpiMasterMap =
					masterList.stream().collect(Collectors.toMap(KpiMaster::getKpiId, Function.identity()));
			return MapUtils.isNotEmpty(kpiMasterMap) ? kpiMasterMap.get(kpiId) : null;
		} catch (Exception e) {
			log.error("Error loading KPI master for KPI {}", kpiId, e);
			return null;
		}
	}
}
