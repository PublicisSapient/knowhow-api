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

import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.apis.forecast.AbstractForecastService;
import com.publicissapient.kpidashboard.common.model.application.DataCount;

import lombok.extern.slf4j.Slf4j;

/**
 * LSTM Neural Network Forecaster using DeepLearning4J for time series prediction.
 *
 * <p>Implements deep learning to capture complex patterns, long-term dependencies, and non-linear
 * relationships in KPI data using LSTM networks.
 *
 * <h3>Architecture:</h3>
 *
 * <pre>
 * Input[t-2,t-1,t] → LSTM[50 neurons] → RnnOutput[1] → Prediction
 * </pre>
 *
 * <h3>Features:</h3>
 *
 * DeepLearning4J framework, 3-step sequences, Adam optimizer, min-max normalization, memory gates
 *
 * <h3>Best For:</h3>
 *
 * Non-linear patterns, long-term dependencies, seasonal behavior, sufficient data (6+ points)
 *
 * <h3>Implementation:</h3>
 *
 * 3D tensors, single epoch training, MSE loss, Xavier weights, real-time forecasting
 *
 * @see AbstractForecastService
 * @see ForecastingModel#LSTM
 * @author KnowHOW Development Team
 * @since 14.1.0
 */
@Service
@Slf4j
public class LSTMForecaster extends AbstractForecastService {

	/**
	 * Number of time steps in input sequences for LSTM training and prediction.
	 *
	 * <p>Lookback window: 3 means model learns from [t-2, t-1, t] to predict t+1.
	 */
	private static final int SEQUENCE_LENGTH = 3;

	/**
	 * Minimum number of historical data points required for LSTM forecasting.
	 *
	 * <p><b>Calculation:</b> With SEQUENCE_LENGTH=3, minimum 6 points creates 3 training sequences:
	 * [0,1,2]→3, [1,2,3]→4, [2,3,4]→5
	 */
	private static final int MIN_DATA_POINTS = 6;

	/**
	 * First LSTM layer size optimized for small datasets (8-14 points).
	 *
	 * <p>16 neurons provide sufficient capacity without overfitting on sparse data.
	 */
	private static final int LSTM_LAYER_1_SIZE = 16;

	/**
	 * L2 regularization strength for preventing overfitting on small datasets.
	 *
	 * <p>0.01 provides strong regularization for 8-14 data points with volatile patterns.
	 */
	private static final double L2_REGULARIZATION = 0.01;

	/**
	 * Dropout rate for preventing overfitting on sparse data.
	 *
	 * <p>0.3 (30%) dropout forces robust feature learning with limited samples.
	 */
	private static final double DROPOUT_RATE = 0.3;

	/**
	 * Higher learning rate optimized for small datasets.
	 *
	 * <p>0.01 enables faster convergence with limited training data.
	 */
	private static final double OPTIMIZED_LEARNING_RATE = 0.01;

	@Override
	public ForecastingModel getModelType() {
		return ForecastingModel.LSTM;
	}

	/**
	 * Generates LSTM-based forecast for the next time period using deep learning.
	 *
	 * <h4>Deep Learning Process:</h4>
	 *
	 * <pre>
	 * Historical Data → Normalization → Sequences → LSTM Training → Prediction
	 * </pre>
	 *
	 * @param historicalData List of historical KPI values in chronological order.
	 * @param kpiId Unique identifier for the KPI being forecasted.
	 * @return List containing a single DataCount with the forecasted value, or empty list.
	 */
	@Override
	public List<DataCount> generateForecast(List<DataCount> historicalData, String kpiId) {
		List<DataCount> forecasts = new ArrayList<>();

		if (historicalData == null
				|| !canForecast(historicalData, kpiId)
				|| historicalData.size() < MIN_DATA_POINTS) {
			log.warn(
					"Insufficient data for LSTM forecast. Required: {}, Available: {}",
					MIN_DATA_POINTS,
					historicalData.size());
			return forecasts;
		}

		List<Double> values = extractValues(historicalData);
		if (values.size() < MIN_DATA_POINTS) {
			return forecasts;
		}

		MultiLayerNetwork model = null;
		INDArray[] sequences = null;
		INDArray lastSequence = null;
		INDArray prediction = null;
		DataSet dataSet = null;

		try {
			log.debug("Starting LSTM forecast for KPI {} with {} data points", kpiId, values.size());

			/**
			 * Step 1: Data Normalization Scales data to [0,1] range for stable neural network training.
			 * Prevents gradient vanishing/exploding and ensures all features contribute equally.
			 */
			double[] normalizedData = normalizeData(values);
			double minValue = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
			double maxValue = values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

			/**
			 * Step 2: Sequence Creation Converts time series to supervised learning format with
			 * input-output pairs. Creates 3D tensors [batch_size, features, sequence_length] for RNN
			 * compatibility.
			 */
			sequences = createSequences(normalizedData);
			if (sequences[0].size(0) == 0) {
				log.warn("No valid sequences created for KPI {}", kpiId);
				return forecasts;
			}

			/**
			 * Step 3: Model Building Constructs LSTM network with optimized architecture: - LSTM layer
			 * (50 neurons, tanh activation) - RNN output layer (1 neuron, linear activation, MSE loss) -
			 * Adam optimizer with 0.001 learning rate
			 */
			model = buildLSTMModel();

			/**
			 * Step 4: Training Multiple epochs for volatile data patterns with early stopping. Increased
			 * training for better pattern recognition on sparse data.
			 */
			dataSet = new DataSet(sequences[0], sequences[1]);
			for (int epoch = 0; epoch < 10; epoch++) {
				model.fit(dataSet);
			}

			/**
			 * Step 5: Prediction Generates forecast using trained model with last sequence as input.
			 * Extracts most recent SEQUENCE_LENGTH values for context.
			 */
			lastSequence = getLastSequence(normalizedData);
			prediction = model.output(lastSequence);

			/**
			 * Step 6: Denormalization Converts prediction back to original KPI scale using inverse
			 * min-max scaling. Ensures non-negative values for KPI metrics.
			 */
			double forecastValue = denormalizeValue(prediction.getDouble(0, 0, 0), minValue, maxValue);
			forecastValue = Math.max(0, forecastValue); // Ensure non-negative KPI values

			/**
			 * Step 7: Result Packaging Creates DataCount object with forecast value and metadata.
			 * Preserves project context and KPI grouping information.
			 */
			String projectName = historicalData.get(historicalData.size() - 1).getSProjectName();
			String kpiGroup = historicalData.get(historicalData.size() - 1).getKpiGroup();

			DataCount forecast =
					createForecastDataCount(forecastValue, projectName, kpiGroup, getModelType().getName());
			forecasts.add(forecast);

			log.info(
					"Generated LSTM forecast for KPI {}: value={}",
					kpiId,
					String.format("%.2f", forecastValue));

		} catch (Exception e) {
			log.error("Error in LSTM forecast for KPI {}: {}", kpiId, e.getMessage(), e);
		} finally {
			// Clean up resources to prevent memory leaks
			cleanupResources(model, sequences, lastSequence, prediction, dataSet);
		}

		return forecasts;
	}

	/**
	 * Normalizes data to [0,1] range using min-max scaling.
	 *
	 * <p>Formula: (value - min) / (max - min). Returns 0.5 for constant values.
	 *
	 * @param values Raw KPI values to normalize
	 * @return Normalized values in [0,1] range
	 */
	private double[] normalizeData(List<Double> values) {
		double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
		double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
		double range = max - min;

		if (range == 0) {
			// All values are identical - return neutral normalized values
			return values.stream().mapToDouble(v -> 0.5).toArray();
		}

		return values.stream().mapToDouble(v -> (v - min) / range).toArray();
	}

	/**
	 * Converts time series to supervised learning sequences for LSTM training.
	 *
	 * <p>Creates input-output pairs: [t-2,t-1,t] → t+1 format
	 *
	 * <p>Returns 3D tensors [num_sequences, 1, SEQUENCE_LENGTH] for RNN compatibility
	 *
	 * @param data Normalized time series data
	 * @return Array of [input_sequences, target_sequences] INDArrays
	 */
	private INDArray[] createSequences(double[] data) {
		if (data.length <= SEQUENCE_LENGTH) {
			// Insufficient data - return empty tensors with correct dimensions
			return new INDArray[] {Nd4j.zeros(0, 1, SEQUENCE_LENGTH), Nd4j.zeros(0, 1, SEQUENCE_LENGTH)};
		}

		int numSequences = data.length - SEQUENCE_LENGTH;

		// Create 3D tensors for RNN input/output format
		INDArray input = Nd4j.zeros(numSequences, 1, SEQUENCE_LENGTH);
		INDArray output = Nd4j.zeros(numSequences, 1, SEQUENCE_LENGTH);

		for (int i = 0; i < numSequences; i++) {
			// Fill input sequence with SEQUENCE_LENGTH consecutive values
			for (int j = 0; j < SEQUENCE_LENGTH; j++) {
				input.putScalar(new int[] {i, 0, j}, data[i + j]);
			}
			// Set target value at the last time step of output sequence
			output.putScalar(new int[] {i, 0, SEQUENCE_LENGTH - 1}, data[i + SEQUENCE_LENGTH]);
		}

		return new INDArray[] {input, output};
	}

	/**
	 * Constructs LSTM model optimized for volatile KPI data with limited samples.
	 *
	 * <p>Architecture: Input[1] → LSTM[16] → RnnOutput[1] → Prediction
	 *
	 * <p>Optimized for: 8-14 data points, volatile patterns, sudden changes, sparse data
	 *
	 * @return Initialized MultiLayerNetwork ready for training
	 */
	private MultiLayerNetwork buildLSTMModel() {
		MultiLayerConfiguration config =
				new NeuralNetConfiguration.Builder()
						.optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
						.updater(new Adam(OPTIMIZED_LEARNING_RATE))
						.weightInit(WeightInit.XAVIER)
						.l2(L2_REGULARIZATION)
						.list()
						// Single LSTM layer for sparse data - prevents overfitting
						.layer(
								0,
								new LSTM.Builder()
										.nIn(1)
										.nOut(LSTM_LAYER_1_SIZE)
										.activation(Activation.TANH)
										.dropOut(DROPOUT_RATE)
										.build())
						// Output layer with identity activation for regression
						.layer(
								1,
								new RnnOutputLayer.Builder(LossFunctions.LossFunction.MSE)
										.nIn(LSTM_LAYER_1_SIZE)
										.nOut(1)
										.activation(Activation.IDENTITY)
										.build())
						.build();

		MultiLayerNetwork model = new MultiLayerNetwork(config);
		model.init();
		return model;
	}

	/**
	 * Extracts last sequence from data for prediction.
	 *
	 * <p>Takes most recent SEQUENCE_LENGTH values as LSTM input context.
	 *
	 * <p>Returns tensor [1, 1, SEQUENCE_LENGTH] with zero-padding if needed.
	 *
	 * @param data Normalized historical data
	 * @return Input tensor for prediction
	 */
	private INDArray getLastSequence(double[] data) {
		// Create tensor for single prediction batch
		INDArray sequence = Nd4j.zeros(1, 1, SEQUENCE_LENGTH);

		// Calculate starting index for sequence extraction
		int startIdx = Math.max(0, data.length - SEQUENCE_LENGTH);

		// Fill sequence tensor with most recent values
		for (int i = 0; i < SEQUENCE_LENGTH; i++) {
			int dataIdx = startIdx + i;
			if (dataIdx < data.length) {
				// Use actual historical value
				sequence.putScalar(new int[] {0, 0, i}, data[dataIdx]);
			}
			// Missing values remain as zeros (padding for insufficient data)
		}

		return sequence;
	}

	/**
	 * Converts normalized prediction back to original KPI scale.
	 *
	 * <p>Formula: normalized_value × (max - min) + min
	 *
	 * @param normalizedValue Prediction in [0,1] range
	 * @param min Original data minimum
	 * @param max Original data maximum
	 * @return Denormalized value in original scale
	 */
	private double denormalizeValue(double normalizedValue, double min, double max) {
		return normalizedValue * (max - min) + min;
	}

	/**
	 * Cleans up resources to prevent memory leaks.
	 *
	 * <p>Properly disposes of INDArray objects and clears model memory.
	 *
	 * @param model LSTM model to clear
	 * @param sequences Input/output sequence arrays
	 * @param lastSequence Last sequence array
	 * @param prediction Prediction array
	 * @param dataSet Training dataset
	 */
	private void cleanupResources(
			MultiLayerNetwork model,
			INDArray[] sequences,
			INDArray lastSequence,
			INDArray prediction,
			DataSet dataSet) {
		try {
			// Clear INDArray objects
			if (sequences != null) {
				for (INDArray array : sequences) {
					if (array != null) {
						array.close();
					}
				}
			}

			if (lastSequence != null) {
				lastSequence.close();
			}

			if (prediction != null) {
				prediction.close();
			}

			// Clear dataset
			if (dataSet != null) {
				if (dataSet.getFeatures() != null) {
					dataSet.getFeatures().close();
				}
				if (dataSet.getLabels() != null) {
					dataSet.getLabels().close();
				}
			}

			// Clear model parameters and gradients
			if (model != null) {
				model.clear();
			}

		} catch (Exception e) {
			log.warn("Error during resource cleanup: {}", e.getMessage());
		}
	}
}
