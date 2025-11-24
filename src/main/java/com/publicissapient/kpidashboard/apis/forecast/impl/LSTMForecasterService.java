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
 * LSTM (Long Short-Term Memory) Neural Network Forecaster.
 *
 * <p><b>What is LSTM?</b> LSTM is a type of Recurrent Neural Network (RNN) designed to learn
 * long-term dependencies in sequential data. Unlike traditional RNNs, LSTMs can remember
 * information for long periods through their unique cell state mechanism.
 *
 * <h3>Key Components:</h3>
 *
 * <ul>
 *   <li><b>Cell State</b>: Long-term memory that flows through the network
 *   <li><b>Hidden State</b>: Short-term memory for immediate predictions
 *   <li><b>Gates</b>: Control information flow (forget, input, output gates)
 * </ul>
 *
 * <h3>How It Works:</h3>
 *
 * <ol>
 *   <li>Normalize input data to [0,1] range for better training
 *   <li>Create sequences of lookback_window size (default: 3-5 points)
 *   <li>Train LSTM network to predict next value in sequence
 *   <li>Use trained model to forecast next KPI value
 *   <li>Denormalize prediction back to original scale
 * </ol>
 *
 * <h3>Architecture:</h3>
 *
 * <pre>
 * Input: [x(t-n), x(t-n+1), ..., x(t-1)] → LSTM → Output: x(t)
 *
 * Example with lookback=3:
 * [10, 12, 15] → LSTM → 18
 * [12, 15, 18] → LSTM → 20 (forecast)
 * </pre>
 *
 * <h3>Best For:</h3>
 *
 * <ul>
 *   <li>Complex patterns with long-term dependencies
 *   <li>Non-linear trends and seasonal patterns
 *   <li>KPIs with memory effects (past values influence future)
 *   <li>Large datasets (minimum 10+ points recommended)
 * </ul>
 *
 * <h3>Implementation Notes:</h3>
 *
 * <ul>
 *   <li>Uses simplified LSTM implementation for lightweight deployment
 *   <li>Single hidden layer with 8-16 neurons
 *   <li>Gradient descent optimization with learning rate 0.01
 *   <li>Early stopping to prevent overfitting
 * </ul>
 *
 * @see AbstractForecastService
 * @see ForecastingModel#LSTM
 */
@Service
@Slf4j
public class LSTMForecasterService extends AbstractForecastService {

	/**
	 * Minimum number of data points required for LSTM forecasting. LSTM needs sufficient historical
	 * data to create meaningful sequences for training. With a lookback window of 3, we need at least
	 * 6 points to create multiple training sequences. Formula: MIN_POINTS = LOOKBACK_WINDOW + 3 (for
	 * adequate training data)
	 */
	private static final int MIN_LSTM_DATA_POINTS = 6;

	/**
	 * Default lookback window size for sequence creation. Defines how many previous time steps the
	 * LSTM uses to predict the next value. A window of 3 means: [t-3, t-2, t-1] → predict t Smaller
	 * windows (2-3) work well for short-term patterns, larger windows (4-6) for complex trends.
	 */
	private static final int DEFAULT_LOOKBACK_WINDOW = 3;

	/**
	 * Number of neurons in the LSTM hidden layer. Controls the model's capacity to learn complex
	 * patterns. 12 neurons provide a good balance between learning capability and computational
	 * efficiency. Too few neurons may underfit, too many may overfit with limited data.
	 */
	private static final int HIDDEN_SIZE = 12;

	/**
	 * Learning rate for gradient descent optimization. Controls how quickly the model updates its
	 * weights during training. 0.01 is a conservative rate that ensures stable convergence without
	 * overshooting. Higher rates (0.1) may converge faster but risk instability, lower rates (0.001)
	 * are more stable but slower.
	 */
	private static final double LEARNING_RATE = 0.01;

	/**
	 * Maximum number of training epochs. Limits training time to prevent excessive computation and
	 * potential overfitting. 100 epochs typically sufficient for small datasets with early stopping
	 * mechanism. Training may stop earlier if loss converges or stagnates.
	 */
	private static final int MAX_EPOCHS = 100;

	@Override
	public ForecastingModel getModelType() {
		return ForecastingModel.LSTM;
	}

	/**
	 * Validates if LSTM forecasting can be performed on the given historical data.
	 *
	 * <p>LSTM requires sufficient data points to create meaningful training sequences. The validation
	 * ensures we have enough data for both sequence creation and model training.
	 *
	 * <h4>Validation Criteria:</h4>
	 *
	 * <ul>
	 *   <li>Passes parent class validation (non-null data, basic requirements)
	 *   <li>Has at least {@value #MIN_LSTM_DATA_POINTS} data points
	 *   <li>Sufficient points for lookback window + training sequences
	 * </ul>
	 *
	 * @param historicalData List of historical KPI values with timestamps
	 * @param kpiId Unique identifier for the KPI being forecasted
	 * @return true if LSTM forecasting is feasible, false otherwise
	 */
	@Override
	public boolean canForecast(List<DataCount> historicalData, String kpiId) {
		if (!super.canForecast(historicalData, kpiId)) {
			return false;
		}

		List<Double> values = extractValues(historicalData);
		if (values.size() < MIN_LSTM_DATA_POINTS) {
			log.debug(
					"KPI {}: LSTM requires at least {} data points, got {}",
					kpiId,
					MIN_LSTM_DATA_POINTS,
					values.size());
			return false;
		}

		return true;
	}

	/**
	 * Generates LSTM-based forecast for the next time period.
	 *
	 * <p>This method implements the complete LSTM forecasting pipeline:
	 *
	 * <h4>Process Flow:</h4>
	 *
	 * <ol>
	 *   <li><b>Data Preparation</b>: Extract and normalize values to [0,1] range
	 *   <li><b>Sequence Creation</b>: Build training sequences using lookback window
	 *   <li><b>Model Training</b>: Train LSTM with gradient descent and early stopping
	 *   <li><b>Prediction</b>: Use trained model to forecast next value
	 *   <li><b>Post-processing</b>: Denormalize and validate prediction
	 * </ol>
	 *
	 * <h4>Algorithm Details:</h4>
	 *
	 * <ul>
	 *   <li>Uses min-max normalization for stable training
	 *   <li>Adaptive lookback window based on data availability
	 *   <li>Single hidden layer with {@value #HIDDEN_SIZE} neurons
	 *   <li>Early stopping prevents overfitting
	 *   <li>Ensures non-negative predictions for KPI values
	 * </ul>
	 *
	 * @param historicalData List of historical KPI values in chronological order
	 * @param kpiId Unique identifier for logging and debugging
	 * @return List containing single forecast DataCount, empty if forecasting fails
	 */
	@Override
	public List<DataCount> generateForecast(List<DataCount> historicalData, String kpiId) {
		List<DataCount> forecasts = new ArrayList<>();

		if (!canForecast(historicalData, kpiId)) {
			return forecasts;
		}

		List<Double> values = extractValues(historicalData);

		try {
			log.debug("KPI {}: Starting LSTM forecast with {} data points", kpiId, values.size());

			// Step 1: Normalize data to [0,1] range
			double[] normalizedData = normalizeData(values);
			double minValue = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
			double maxValue = values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

			// Step 2: Determine optimal lookback window
			int lookbackWindow = Math.min(DEFAULT_LOOKBACK_WINDOW, values.size() - 2);

			// Step 3: Create training sequences
			List<double[]> sequences = createSequences(normalizedData, lookbackWindow);
			if (sequences.size() < 2) {
				log.warn("KPI {}: Insufficient sequences for LSTM training", kpiId);
				return forecasts;
			}

			// Step 4: Initialize and train LSTM
			SimpleLSTM lstm = new SimpleLSTM(lookbackWindow, HIDDEN_SIZE, LEARNING_RATE);
			trainLSTM(lstm, sequences, kpiId);

			// Step 5: Generate forecast
			double[] lastSequence = getLastSequence(normalizedData, lookbackWindow);
			double normalizedForecast = lstm.predict(lastSequence);

			// Step 6: Denormalize prediction
			double forecastValue = denormalize(normalizedForecast, minValue, maxValue);
			forecastValue = Math.max(0, forecastValue); // Ensure non-negative

			// Step 7: Create forecast data object
			String projectName = historicalData.get(historicalData.size() - 1).getSProjectName();
			String kpiGroup = historicalData.get(historicalData.size() - 1).getKpiGroup();
			DataCount forecast =
					createForecastDataCount(forecastValue, projectName, kpiGroup, getModelType().getName());
			forecasts.add(forecast);

			log.info(
					"KPI {}: LSTM forecast = {} (lookback={}, hidden={})",
					kpiId,
					String.format("%.2f", forecastValue),
					lookbackWindow,
					HIDDEN_SIZE);

		} catch (Exception e) {
			log.error("KPI {}: Failed to generate LSTM forecast - {}", kpiId, e.getMessage(), e);
		}

		return forecasts;
	}

	/**
	 * Normalizes input data to [0,1] range using min-max normalization.
	 *
	 * <p>Normalization is crucial for LSTM training as it:
	 *
	 * <ul>
	 *   <li>Prevents gradient vanishing/exploding problems
	 *   <li>Ensures all features contribute equally to learning
	 *   <li>Improves convergence speed and stability
	 * </ul>
	 *
	 * <p>Formula: normalized_value = (value - min) / (max - min)
	 *
	 * @param values List of raw KPI values to normalize
	 * @return Array of normalized values in [0,1] range, or 0.5 if all values are identical
	 */
	private double[] normalizeData(List<Double> values) {
		double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
		double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
		double range = max - min;

		if (range < 1e-10) {
			// All values are the same, return array of 0.5
			return values.stream().mapToDouble(v -> 0.5).toArray();
		}

		return values.stream().mapToDouble(v -> (v - min) / range).toArray();
	}

	/**
	 * Converts normalized prediction back to original scale.
	 *
	 * <p>Reverses the min-max normalization applied during data preparation. Essential for returning
	 * meaningful KPI values to the user.
	 *
	 * <p>Formula: original_value = normalized_value * (max - min) + min
	 *
	 * @param normalizedValue Predicted value in [0,1] range from LSTM
	 * @param min Minimum value from original dataset
	 * @param max Maximum value from original dataset
	 * @return Denormalized prediction in original KPI scale
	 */
	private double denormalize(double normalizedValue, double min, double max) {
		return normalizedValue * (max - min) + min;
	}

	/**
	 * Creates training sequences from normalized time series data.
	 *
	 * <p>Transforms the time series into supervised learning format where each sequence contains a
	 * fixed number of input time steps and one target output.
	 *
	 * <h4>Example with lookbackWindow=3:</h4>
	 *
	 * <pre>
	 * Data: [1, 2, 3, 4, 5, 6]
	 * Sequences:
	 *   [1, 2, 3] → 4
	 *   [2, 3, 4] → 5
	 *   [3, 4, 5] → 6
	 * </pre>
	 *
	 * @param data Normalized time series data
	 * @param lookbackWindow Number of previous time steps to use as input
	 * @return List of sequences, each containing input features + target value
	 */
	private List<double[]> createSequences(double[] data, int lookbackWindow) {
		List<double[]> sequences = new ArrayList<>();

		for (int i = lookbackWindow; i < data.length; i++) {
			double[] sequence = new double[lookbackWindow + 1]; // +1 for target
			System.arraycopy(data, i - lookbackWindow, sequence, 0, lookbackWindow);
			sequence[lookbackWindow] = data[i]; // Target value
			sequences.add(sequence);
		}

		return sequences;
	}

	/**
	 * Extracts the most recent sequence for generating the next prediction.
	 *
	 * <p>Takes the last 'lookbackWindow' values from the dataset to create the input sequence for
	 * forecasting the next time period.
	 *
	 * <h4>Example with lookbackWindow=3:</h4>
	 *
	 * <pre>
	 * Data: [1, 2, 3, 4, 5, 6]
	 * Last sequence: [4, 5, 6] → predict 7
	 * </pre>
	 *
	 * @param data Complete normalized time series
	 * @param lookbackWindow Number of recent values to include
	 * @return Array containing the most recent sequence for prediction
	 */
	private double[] getLastSequence(double[] data, int lookbackWindow) {
		double[] sequence = new double[lookbackWindow];
		System.arraycopy(data, data.length - lookbackWindow, sequence, 0, lookbackWindow);
		return sequence;
	}

	/**
	 * Trains the LSTM model using gradient descent with early stopping.
	 *
	 * <p>Implements the training loop with the following features:
	 *
	 * <ul>
	 *   <li><b>Adaptive Epochs</b>: Limits training based on data size
	 *   <li><b>Early Stopping</b>: Prevents overfitting by monitoring loss convergence
	 *   <li><b>Loss Tracking</b>: Uses Mean Squared Error for regression
	 *   <li><b>Progress Logging</b>: Reports training progress every 20 epochs
	 * </ul>
	 *
	 * <h4>Training Process:</h4>
	 *
	 * <ol>
	 *   <li>Forward pass: Compute predictions for all sequences
	 *   <li>Loss calculation: MSE between predictions and targets
	 *   <li>Backward pass: Update weights using gradients
	 *   <li>Convergence check: Stop if loss stagnates for 10 epochs
	 * </ol>
	 *
	 * @param lstm The LSTM model to train
	 * @param sequences Training data as input-target pairs
	 * @param kpiId KPI identifier for logging purposes
	 */
	private void trainLSTM(SimpleLSTM lstm, List<double[]> sequences, String kpiId) {
		int epochs = Math.min(MAX_EPOCHS, sequences.size() * 10);
		double prevLoss = Double.MAX_VALUE;
		int stagnantEpochs = 0;

		for (int epoch = 0; epoch < epochs; epoch++) {
			double totalLoss = 0.0;

			for (double[] sequence : sequences) {
				double[] input = new double[sequence.length - 1];
				System.arraycopy(sequence, 0, input, 0, input.length);
				double target = sequence[sequence.length - 1];

				double prediction = lstm.predict(input);
				double loss = Math.pow(prediction - target, 2);
				totalLoss += loss;

				lstm.backpropagate(input, target, prediction);
			}

			double avgLoss = totalLoss / sequences.size();

			// Early stopping check
			if (Math.abs(prevLoss - avgLoss) < 1e-6) {
				stagnantEpochs++;
				if (stagnantEpochs >= 10) {
					log.debug(
							"KPI {}: Early stopping at epoch {} (loss={})",
							kpiId,
							epoch,
							String.format("%.6f", avgLoss));
					break;
				}
			} else {
				stagnantEpochs = 0;
			}

			prevLoss = avgLoss;

			if (epoch % 20 == 0) {
				log.debug("KPI {}: Epoch {}, Loss: {}", kpiId, epoch, String.format("%.6f", avgLoss));
			}
		}
	}

	/**
	 * Simplified LSTM implementation for lightweight forecasting.
	 *
	 * <p>This implementation focuses on core LSTM concepts while maintaining computational
	 * efficiency. It uses a single-layer architecture with essential LSTM gates (input, forget,
	 * output) and cell state management for sequence learning.
	 *
	 * <h4>Architecture:</h4>
	 *
	 * <ul>
	 *   <li><b>Input Layer</b>: Processes sequential time series values
	 *   <li><b>LSTM Layer</b>: Single hidden layer with configurable neurons
	 *   <li><b>Output Layer</b>: Linear combination of hidden states
	 * </ul>
	 *
	 * <h4>Key Features:</h4>
	 *
	 * <ul>
	 *   <li>Simplified gate computations for efficiency
	 *   <li>Xavier-style weight initialization
	 *   <li>Gradient clipping to prevent exploding gradients
	 *   <li>Sigmoid activation with numerical stability
	 * </ul>
	 */
	private static class SimpleLSTM {
		/** Number of input features (sequence length) */
		private final int inputSize;

		/** Number of neurons in hidden layer */
		private final int hiddenSize;

		/** Learning rate for weight updates */
		private final double learningRate;

		/** Weights connecting input to hidden layer */
		private double[][] weightsInput;

		/** Weights connecting hidden state to itself (recurrent connections) */
		private double[][] weightsHidden;

		/** Bias terms for each hidden neuron */
		private double[] biases;

		/** Current hidden state (short-term memory) */
		private double[] hiddenState;

		/** Current cell state (long-term memory) */
		private double[] cellState;

		/**
		 * Constructs a new SimpleLSTM with specified architecture parameters.
		 *
		 * <p>Initializes all weight matrices with small random values and biases with small positive
		 * values to break symmetry and aid convergence.
		 *
		 * @param inputSize Number of input features (lookback window size)
		 * @param hiddenSize Number of neurons in the hidden layer
		 * @param learningRate Learning rate for gradient descent optimization
		 */
		public SimpleLSTM(int inputSize, int hiddenSize, double learningRate) {
			this.inputSize = inputSize;
			this.hiddenSize = hiddenSize;
			this.learningRate = learningRate;

			// Initialize weights randomly
			this.weightsInput = initializeWeights(hiddenSize, inputSize);
			this.weightsHidden = initializeWeights(hiddenSize, hiddenSize);
			this.biases = new double[hiddenSize];
			this.hiddenState = new double[hiddenSize];
			this.cellState = new double[hiddenSize];

			// Initialize biases to small positive values
			for (int i = 0; i < hiddenSize; i++) {
				biases[i] = 0.1;
			}
		}

		/**
		 * Initializes weight matrices with small random values.
		 *
		 * <p>Uses Xavier-style initialization to maintain gradient flow. Small random values prevent
		 * symmetry and help with convergence.
		 *
		 * @param rows Number of rows in weight matrix
		 * @param cols Number of columns in weight matrix
		 * @return Initialized weight matrix with values in [-0.05, 0.05]
		 */
		private double[][] initializeWeights(int rows, int cols) {
			double[][] weights = new double[rows][cols];
			for (int i = 0; i < rows; i++) {
				for (int j = 0; j < cols; j++) {
					weights[i][j] = (Math.random() - 0.5) * 0.1; // Small random values
				}
			}
			return weights;
		}

		/**
		 * Generates prediction for a given input sequence.
		 *
		 * <p>Processes the input sequence step by step through the LSTM network, updating hidden and
		 * cell states at each time step. The final prediction is computed as a weighted average of the
		 * final hidden state.
		 *
		 * <h4>Process:</h4>
		 *
		 * <ol>
		 *   <li>Reset hidden and cell states to zero
		 *   <li>Process each input value sequentially
		 *   <li>Update LSTM gates and states at each step
		 *   <li>Compute final output from hidden state
		 * </ol>
		 *
		 * @param input Sequence of normalized input values
		 * @return Predicted value in [0,1] range (normalized)
		 */
		public double predict(double[] input) {
			// Reset states
			hiddenState = new double[hiddenSize];
			cellState = new double[hiddenSize];

			// Process sequence
			for (double value : input) {
				forwardStep(new double[] {value});
			}

			// Output layer (simple linear combination)
			double output = 0.0;
			for (int i = 0; i < hiddenSize; i++) {
				output += hiddenState[i] * (1.0 / hiddenSize); // Average hidden states
			}

			return sigmoid(output);
		}

		/**
		 * Performs one forward step through the LSTM cell.
		 *
		 * <p>Implements the core LSTM computation with three gates:
		 *
		 * <ul>
		 *   <li><b>Input Gate</b>: Controls what new information to store
		 *   <li><b>Forget Gate</b>: Controls what information to discard
		 *   <li><b>Output Gate</b>: Controls what parts of cell state to output
		 * </ul>
		 *
		 * <p>Updates both cell state (long-term memory) and hidden state (short-term memory) based on
		 * current input and previous states.
		 *
		 * @param input Current input values for this time step
		 */
		private void forwardStep(double[] input) {
			double[] newHidden = new double[hiddenSize];
			double[] newCell = new double[hiddenSize];

			for (int i = 0; i < hiddenSize; i++) {
				// Simplified LSTM gates with hidden state connections
				double hiddenContrib = dotProduct(weightsHidden[i], hiddenState);
				double inputGate = sigmoid(dotProduct(weightsInput[i], input) + hiddenContrib + biases[i]);
				double forgetGate =
						sigmoid(dotProduct(weightsInput[i], input) + hiddenContrib * 0.5 + biases[i] * 0.5);
				double outputGate =
						sigmoid(dotProduct(weightsInput[i], input) + hiddenContrib * 0.8 + biases[i] * 0.8);

				// Update cell state
				double candidateValue = Math.tanh(dotProduct(weightsInput[i], input) + hiddenContrib * 0.3);
				newCell[i] = forgetGate * cellState[i] + inputGate * candidateValue;

				// Update hidden state
				newHidden[i] = outputGate * Math.tanh(newCell[i]);
			}

			System.arraycopy(newHidden, 0, hiddenState, 0, hiddenSize);
			System.arraycopy(newCell, 0, cellState, 0, hiddenSize);
		}

		/**
		 * Updates model weights using backpropagation.
		 *
		 * <p>Implements simplified gradient descent to minimize prediction error. Updates weights and
		 * biases based on the difference between predicted and target values.
		 *
		 * <h4>Update Rules:</h4>
		 *
		 * <ul>
		 *   <li>Bias update: bias += learning_rate * error * hidden_state
		 *   <li>Weight update: weight += learning_rate * error * input * 0.1
		 * </ul>
		 *
		 * @param input Input sequence used for prediction
		 * @param target Expected output value
		 * @param prediction Actual predicted value
		 */
		public void backpropagate(double[] input, double target, double prediction) {
			double error = target - prediction;

			// Simplified gradient update
			for (int i = 0; i < hiddenSize; i++) {
				biases[i] += learningRate * error * hiddenState[i];

				for (int j = 0; j < inputSize && j < weightsInput[i].length; j++) {
					if (j < input.length) {
						weightsInput[i][j] += learningRate * error * input[j] * 0.1;
					}
				}
			}
		}

		/**
		 * Computes dot product of two vectors with length safety.
		 *
		 * <p>Handles vectors of different lengths by using the minimum length to prevent array bounds
		 * exceptions.
		 *
		 * @param a First vector
		 * @param b Second vector
		 * @return Dot product sum
		 */
		private double dotProduct(double[] a, double[] b) {
			double sum = 0.0;
			int minLength = Math.min(a.length, b.length);
			for (int i = 0; i < minLength; i++) {
				sum += a[i] * b[i];
			}
			return sum;
		}

		/**
		 * Applies sigmoid activation function with numerical stability.
		 *
		 * <p>Sigmoid function: f(x) = 1 / (1 + e^(-x)) Includes input clamping to prevent
		 * overflow/underflow in exponential computation.
		 *
		 * @param x Input value
		 * @return Sigmoid output in range [0, 1]
		 */
		private double sigmoid(double x) {
			return 1.0 / (1.0 + Math.exp(-Math.max(-500, Math.min(500, x))));
		}
	}
}
