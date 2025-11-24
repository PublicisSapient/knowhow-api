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
 * <ul>
 *   <li><b>Cell State</b>: Long-term memory that flows through the network</li>
 *   <li><b>Hidden State</b>: Short-term memory for immediate predictions</li>
 *   <li><b>Gates</b>: Control information flow (forget, input, output gates)</li>
 * </ul>
 *
 * <h3>How It Works:</h3>
 * <ol>
 *   <li>Normalize input data to [0,1] range for better training</li>
 *   <li>Create sequences of lookback_window size (default: 3-5 points)</li>
 *   <li>Train LSTM network to predict next value in sequence</li>
 *   <li>Use trained model to forecast next KPI value</li>
 *   <li>Denormalize prediction back to original scale</li>
 * </ol>
 *
 * <h3>Architecture:</h3>
 * <pre>
 * Input: [x(t-n), x(t-n+1), ..., x(t-1)] → LSTM → Output: x(t)
 *
 * Example with lookback=3:
 * [10, 12, 15] → LSTM → 18
 * [12, 15, 18] → LSTM → 20 (forecast)
 * </pre>
 *
 * <h3>Best For:</h3>
 * <ul>
 *   <li>Complex patterns with long-term dependencies</li>
 *   <li>Non-linear trends and seasonal patterns</li>
 *   <li>KPIs with memory effects (past values influence future)</li>
 *   <li>Large datasets (minimum 10+ points recommended)</li>
 * </ul>
 *
 * <h3>Implementation Notes:</h3>
 * <ul>
 *   <li>Uses simplified LSTM implementation for lightweight deployment</li>
 *   <li>Single hidden layer with 8-16 neurons</li>
 *   <li>Gradient descent optimization with learning rate 0.01</li>
 *   <li>Early stopping to prevent overfitting</li>
 * </ul>
 *
 * @see AbstractForecastService
 * @see ForecastingModel#LSTM
 */
@Service
@Slf4j
public class LSTMForecaster extends AbstractForecastService {

    private static final int MIN_LSTM_DATA_POINTS = 6;
    private static final int DEFAULT_LOOKBACK_WINDOW = 3;
    private static final int HIDDEN_SIZE = 12;
    private static final double LEARNING_RATE = 0.01;
    private static final int MAX_EPOCHS = 100;

    @Override
    public ForecastingModel getModelType() {
        return ForecastingModel.LSTM;
    }

    /**
     * LSTM-specific validation requiring minimum data points for sequence creation.
     * Need at least lookback_window + 2 points for training.
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
            DataCount forecast = createForecastDataCount(forecastValue, projectName, kpiGroup, getModelType().getName());
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
     * Normalize data to [0,1] range using min-max normalization.
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
     * Denormalize prediction back to original scale.
     */
    private double denormalize(double normalizedValue, double min, double max) {
        return normalizedValue * (max - min) + min;
    }

    /**
     * Create training sequences from normalized data.
     * Each sequence contains lookbackWindow inputs and 1 target output.
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
     * Get the last sequence for prediction.
     */
    private double[] getLastSequence(double[] data, int lookbackWindow) {
        double[] sequence = new double[lookbackWindow];
        System.arraycopy(data, data.length - lookbackWindow, sequence, 0, lookbackWindow);
        return sequence;
    }

    /**
     * Train the LSTM model using the provided sequences.
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
                    log.debug("KPI {}: Early stopping at epoch {} (loss={})", kpiId, epoch, String.format("%.6f", avgLoss));
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
     * This is a basic implementation focusing on the core LSTM concepts.
     */
    private static class SimpleLSTM {
        private final int inputSize;
        private final int hiddenSize;
        private final double learningRate;

        // LSTM weights (simplified)
        private double[][] weightsInput;
        private double[][] weightsHidden;
        private double[] biases;
        private double[] hiddenState;
        private double[] cellState;

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

        private double[][] initializeWeights(int rows, int cols) {
            double[][] weights = new double[rows][cols];
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    weights[i][j] = (Math.random() - 0.5) * 0.1; // Small random values
                }
            }
            return weights;
        }

        public double predict(double[] input) {
            // Reset states
            hiddenState = new double[hiddenSize];
            cellState = new double[hiddenSize];

            // Process sequence
            for (double value : input) {
                forwardStep(new double[]{value});
            }

            // Output layer (simple linear combination)
            double output = 0.0;
            for (int i = 0; i < hiddenSize; i++) {
                output += hiddenState[i] * (1.0 / hiddenSize); // Average hidden states
            }

            return sigmoid(output);
        }

        private void forwardStep(double[] input) {
            double[] newHidden = new double[hiddenSize];
            double[] newCell = new double[hiddenSize];

            for (int i = 0; i < hiddenSize; i++) {
                // Simplified LSTM gates with hidden state connections
                double hiddenContrib = dotProduct(weightsHidden[i], hiddenState);
                double inputGate = sigmoid(dotProduct(weightsInput[i], input) + hiddenContrib + biases[i]);
                double forgetGate = sigmoid(dotProduct(weightsInput[i], input) + hiddenContrib * 0.5 + biases[i] * 0.5);
                double outputGate = sigmoid(dotProduct(weightsInput[i], input) + hiddenContrib * 0.8 + biases[i] * 0.8);

                // Update cell state
                double candidateValue = Math.tanh(dotProduct(weightsInput[i], input) + hiddenContrib * 0.3);
                newCell[i] = forgetGate * cellState[i] + inputGate * candidateValue;

                // Update hidden state
                newHidden[i] = outputGate * Math.tanh(newCell[i]);
            }

            System.arraycopy(newHidden, 0, hiddenState, 0, hiddenSize);
            System.arraycopy(newCell, 0, cellState, 0, hiddenSize);
        }

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

        private double dotProduct(double[] a, double[] b) {
            double sum = 0.0;
            int minLength = Math.min(a.length, b.length);
            for (int i = 0; i < minLength; i++) {
                sum += a[i] * b[i];
            }
            return sum;
        }

        private double sigmoid(double x) {
            return 1.0 / (1.0 + Math.exp(-Math.max(-500, Math.min(500, x))));
        }
    }
}