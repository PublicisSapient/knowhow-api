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

import ai.djl.Model;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Block;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.nn.recurrent.LSTM;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.EasyTrain;
import ai.djl.training.Trainer;
import ai.djl.training.dataset.Batch;
import ai.djl.training.listener.TrainingListener;
import ai.djl.training.loss.Loss;
import ai.djl.training.optimizer.Optimizer;
import ai.djl.training.tracker.Tracker;
import lombok.extern.slf4j.Slf4j;

/**
 * LSTM Forecaster using Deep Java Library (DJL) with PyTorch backend.
 *
 * <p><b>What is LSTM?</b> LSTM (Long Short-Term Memory) is a type of recurrent neural network
 * designed to capture long-term dependencies and non-linear patterns in sequential data.
 *
 * <ul>
 *   <li><b>Memory Cells</b>: Retain information over long sequences
 *   <li><b>Gates</b>: Control information flow (forget, input, output gates)
 *   <li><b>Non-linear</b>: Captures complex patterns that linear models miss
 * </ul>
 *
 * <p><b>How It Works:</b>
 *
 * <ol>
 *   <li>Normalize data to [0,1] range for stable training
 *   <li>Create sequences of length 3 from historical data
 *   <li>Build LSTM network: LSTM layer (16 units) → Linear layer (1 output)
 *   <li>Train model for 10 epochs using Adam optimizer
 *   <li>Predict next value using last sequence
 *   <li>Denormalize prediction back to original scale
 * </ol>
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * Input:  [100, 105, 110, 115, 120, 125]
 * Sequences: [100,105,110]→115, [105,110,115]→120, [110,115,120]→125
 * Predict: [115,120,125]→130
 * </pre>
 *
 * <p><b>Best For:</b> Complex non-linear patterns, long-term dependencies, multi-variate KPIs
 *
 * <p><b>Requires:</b> Minimum 6 historical data points
 *
 * @see ai.djl.nn.recurrent.LSTM
 * @see AbstractForecastService
 */
@Service
@Slf4j
public class LSTMForecaster extends AbstractForecastService {

	private static final int SEQUENCE_LENGTH = 3;
	private static final int MIN_DATA_POINTS = 6;
	private static final int LSTM_HIDDEN_SIZE = 16;
	private static final int EPOCHS = 10;
	private static final int LOG_INTERVAL = 5;
	private static final float LEARNING_RATE = 0.01f;
	private static final double MIN_RANGE_THRESHOLD = 1e-10;
	private static final double DEFAULT_RANGE = 1.0;
	private static final int LSTM_NUM_LAYERS = 1;
	private static final int OUTPUT_UNITS = 1;
	private static final int NDLIST_MIN_SIZE = 2;
	private static final String MODEL_NAME = "lstm-forecast";
	private static final String LAST_TIMESTEP_SELECTOR = ":, -1, :";
	private static final String DECIMAL_FORMAT = "%.2f";
	private static final String LOSS_FORMAT = "%.4f";

	/**
	 * Returns the forecasting model type.
	 *
	 * @return the LSTM forecasting model type
	 */
	@Override
	public ForecastingModel getModelType() {
		return ForecastingModel.LSTM;
	}

	/**
	 * Determines if LSTM forecasting can be performed on the given historical data.
	 *
	 * @param historicalData the list of historical data points
	 * @param kpiId the KPI identifier for logging purposes
	 * @return true if forecasting is possible, false otherwise
	 */
	@Override
	public boolean canForecast(List<DataCount> historicalData, String kpiId) {
		if (!super.canForecast(historicalData, kpiId)) {
			return false;
		}

		List<Double> values = extractValues(historicalData);
		if (values.size() < MIN_DATA_POINTS) {
			log.debug(
					"KPI {}: LSTM requires at least {} data points, got {}",
					kpiId,
					MIN_DATA_POINTS,
					values.size());
			return false;
		}

		return true;
	}

	/**
	 * Generates LSTM-based forecast for the given historical data.
	 *
	 * @param historicalData the list of historical data points
	 * @param kpiId the KPI identifier for logging purposes
	 * @return list containing the forecast data point, or empty list if forecasting fails
	 */
	@Override
	public List<DataCount> generateForecast(List<DataCount> historicalData, String kpiId) {
		List<DataCount> forecasts = new ArrayList<>();

		if (!canForecast(historicalData, kpiId)) {
			return forecasts;
		}

		List<Double> values = extractValues(historicalData);

		try (NDManager manager = NDManager.newBaseManager()) {
			log.debug("KPI {}: Starting LSTM forecast with {} data points", kpiId, values.size());
			double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
			double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
			List<Double> normalized = normalizeData(values, min, max);

			NDList sequences = createSequences(manager, normalized);
			if (sequences.isEmpty() || sequences.size() < NDLIST_MIN_SIZE) {
				log.warn("KPI {}: Not enough sequences for LSTM training", kpiId);
				return forecasts;
			}

			Block lstmBlock = buildLSTMModel();

			try (Model model = Model.newInstance(MODEL_NAME)) {
				model.setBlock(lstmBlock);
				trainModel(model, manager, sequences.get(0), sequences.get(1), kpiId);

				double forecastValue =
						denormalizeValue(
								predictNextValue(model, manager, getLastSequence(manager, normalized)).getFloat(),
								min,
								max);
				forecastValue = Math.max(0, forecastValue);

				DataCount lastDataPoint = historicalData.get(historicalData.size() - 1);
				DataCount forecast =
						createForecastDataCount(
								forecastValue,
								lastDataPoint.getSProjectName(),
								lastDataPoint.getKpiGroup(),
								getModelType().getName());
				forecasts.add(forecast);
				log.info(
						"KPI {}: LSTM forecast = {} (last actual = {})",
						kpiId,
						String.format(DECIMAL_FORMAT, forecastValue),
						String.format(DECIMAL_FORMAT, values.get(values.size() - 1)));
			}

		} catch (Exception e) {
			log.error("KPI {}: Failed to generate LSTM forecast - {}", kpiId, e.getMessage(), e);
		}

		return forecasts;
	}

	/**
	 * Normalizes data to [0, 1] range using min-max normalization.
	 *
	 * @param values the list of values to normalize
	 * @param min the minimum value for normalization
	 * @param max the maximum value for normalization
	 * @return list of normalized values
	 */
	private List<Double> normalizeData(List<Double> values, double min, double max) {
		List<Double> normalized = new ArrayList<>();
		double range = max - min;
		if (range < MIN_RANGE_THRESHOLD) {
			range = DEFAULT_RANGE;
		}
		for (Double value : values) {
			normalized.add((value - min) / range);
		}
		return normalized;
	}

	/**
	 * Creates input sequences and targets for LSTM training.
	 *
	 * @param manager the NDManager for array creation
	 * @param normalized the normalized data values
	 * @return NDList containing [inputSequences, targetValues], or empty NDList if insufficient data
	 */
	private NDList createSequences(NDManager manager, List<Double> normalized) {
		int numSequences = normalized.size() - SEQUENCE_LENGTH;
		if (numSequences <= 0) {
			return new NDList();
		}

		float[][] inputs = new float[numSequences][SEQUENCE_LENGTH];
		float[] targets = new float[numSequences];

		for (int i = 0; i < numSequences; i++) {
			for (int j = 0; j < SEQUENCE_LENGTH; j++) {
				inputs[i][j] = normalized.get(i + j).floatValue();
			}
			targets[i] = normalized.get(i + SEQUENCE_LENGTH).floatValue();
		}
		try (NDArray inputArray = manager.create(inputs);
				NDArray targetArray = manager.create(targets)) {
			return new NDList(
					inputArray.reshape(new Shape(numSequences, SEQUENCE_LENGTH, 1)),
					targetArray.reshape(new Shape(numSequences, 1)));
		}
	}

	/**
	 * Builds LSTM model architecture: LSTM layer → Extract last timestep → Linear output layer.
	 *
	 * @return the constructed LSTM model block
	 */
	private Block buildLSTMModel() {
		return new SequentialBlock()
				.add(
						LSTM.builder()
								.setNumLayers(LSTM_NUM_LAYERS)
								.setStateSize(LSTM_HIDDEN_SIZE)
								.optReturnState(false)
								.optBatchFirst(true)
								.build())
				.add(this::extractLastTimestep)
				.add(Linear.builder().setUnits(OUTPUT_UNITS).build());
	}

	/**
	 * Extracts the last timestep from LSTM output.
	 *
	 * @param ndList the NDList containing LSTM output
	 * @return NDList with extracted last timestep
	 */
	private NDList extractLastTimestep(NDList ndList) {
		NDArray output = ndList.singletonOrThrow();
		NDArray lastStep = output.get(LAST_TIMESTEP_SELECTOR);
		return new NDList(lastStep);
	}

	/**
	 * Trains the LSTM model using Adam optimizer and L2 loss.
	 *
	 * @param model the LSTM model to train
	 * @param manager the NDManager for resource management
	 * @param inputData the input training data
	 * @param targetData the target training data
	 * @param kpiId the KPI identifier for logging purposes
	 */
	private void trainModel(
			Model model, NDManager manager, NDArray inputData, NDArray targetData, String kpiId) {
		DefaultTrainingConfig config =
				new DefaultTrainingConfig(Loss.l2Loss())
						.optOptimizer(
								Optimizer.adam().optLearningRateTracker(Tracker.fixed(LEARNING_RATE)).build())
						.addTrainingListeners(TrainingListener.Defaults.logging());

		try (Trainer trainer = model.newTrainer(config)) {
			trainer.initialize(new Shape(1, SEQUENCE_LENGTH, 1));

			int batchSize = (int) inputData.getShape().get(0);
			for (int epoch = 0; epoch < EPOCHS; epoch++) {
				// Creates a training batch from input and target data.
				Batch batch =
						new Batch(
								manager,
								new NDList(inputData),
								new NDList(targetData),
								batchSize,
								null,
								null,
								0L,
								batchSize);
				EasyTrain.trainBatch(trainer, batch);
				trainer.step();

				if (epoch % LOG_INTERVAL == 0) {
					Float loss = trainer.getTrainingResult().getTrainLoss();
					if (loss != null) {
						log.debug(
								"KPI {}: Epoch {}/{}, Loss: {}",
								kpiId,
								epoch,
								EPOCHS,
								String.format(LOSS_FORMAT, loss));
					}
				}
			}
		}
	}

	/**
	 * Retrieves the last sequence from normalized data for prediction.
	 *
	 * @param manager the NDManager for array creation
	 * @param normalized the normalized data values
	 * @return NDArray containing the last sequence
	 */
	private NDArray getLastSequence(NDManager manager, List<Double> normalized) {
		int start = normalized.size() - SEQUENCE_LENGTH;
		float[] sequence = new float[SEQUENCE_LENGTH];
		for (int i = 0; i < SEQUENCE_LENGTH; i++) {
			sequence[i] = normalized.get(start + i).floatValue();
		}
		try (NDArray sequenceArray = manager.create(sequence)) {
			return sequenceArray.reshape(new Shape(1, SEQUENCE_LENGTH, 1));
		}
	}

	/**
	 * Denormalizes a value from [0, 1] back to original scale.
	 *
	 * @param normalized the normalized value
	 * @param min the minimum value for denormalization
	 * @param max the maximum value for denormalization
	 * @return the denormalized value
	 */
	private double denormalizeValue(float normalized, double min, double max) {
		return normalized * (max - min) + min;
	}

	/**
	 * Predicts the next value using the trained model.
	 *
	 * @param model the trained LSTM model
	 * @param manager the NDManager for resource management
	 * @param lastSequence the last sequence for prediction
	 * @return NDArray containing the prediction
	 */
	private NDArray predictNextValue(Model model, NDManager manager, NDArray lastSequence) {
		return model
				.getBlock()
				.forward(
						new ai.djl.training.ParameterStore(manager, false), new NDList(lastSequence), false)
				.singletonOrThrow();
	}
}
