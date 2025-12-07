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

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.function.Supplier;

import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

/**
 * Test class for LSTMForecaster.
 * 
 * @author KnowHOW Development Team
 * @since 14.2.0
 */
class LSTMForecasterTest {

    private LSTMForecaster lstmForecaster;

    @BeforeEach
    void setUp() {
        lstmForecaster = new LSTMForecaster();
    }

    @Test
    void testGetModelType() {
        assertEquals(ForecastingModel.LSTM, lstmForecaster.getModelType());
    }

    @Test
    void testGenerateForecast_EmptyData() {
        List<DataCount> historicalData = new ArrayList<>();
        
        List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, "EMPTY_KPI");
        
        assertNotNull(forecasts);
        assertTrue(forecasts.isEmpty());
    }

    @Test
    void testGenerateForecast_InsufficientData() {
        List<DataCount> historicalData = createTestData(new double[]{10.0, 15.0, 20.0});
        
        List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, "INSUFFICIENT_KPI");
        
        assertNotNull(forecasts);
        assertTrue(forecasts.isEmpty());
    }

    @Test
    void testGenerateForecast_MinimumData() {
        List<DataCount> historicalData = createTestData(new double[]{10.0, 15.0, 20.0, 25.0, 30.0, 35.0});
        
        List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, "MINIMUM_KPI");
        
        assertNotNull(forecasts);
        assertEquals(1, forecasts.size());
        
        DataCount forecast = forecasts.get(0);
        assertNotNull(forecast.getValue());
        assertTrue(((Double) forecast.getValue()) >= 0);
        assertEquals("lstm", forecast.getForecastingModel());
    }

    @Test
    void testGenerateForecast_TrendingData() {
        List<DataCount> historicalData = createTestData(new double[]{10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0, 45.0});
        
        List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, "TRENDING_KPI");
        
        assertNotNull(forecasts);
        assertEquals(1, forecasts.size());
        
        DataCount forecast = forecasts.get(0);
        assertNotNull(forecast.getValue());
        assertTrue(((Double) forecast.getValue()) >= 0);
    }

    @Test
    void testGenerateForecast_VolatileData() {
        List<DataCount> historicalData = createTestData(new double[]{161.0, 329.43, 72.0, 312.25, 179.25, 137.5, 0.0, 0.0});
        
        List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, "VOLATILE_KPI");
        
        assertNotNull(forecasts);
        assertEquals(1, forecasts.size());
        
        DataCount forecast = forecasts.get(0);
        assertNotNull(forecast.getValue());
        assertTrue(((Double) forecast.getValue()) >= 0);
    }

    @Test
    void testGenerateForecast_AllZeros() {
        List<DataCount> historicalData = createTestData(new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0});
        
        List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, "ZERO_KPI");
        
        assertNotNull(forecasts);
        assertEquals(1, forecasts.size());
        
        DataCount forecast = forecasts.get(0);
        assertNotNull(forecast.getValue());
        assertTrue(((Double) forecast.getValue()) >= 0);
    }

    @Test
    void testGenerateForecast_ConstantValues() {
        List<DataCount> historicalData = createTestData(new double[]{25.0, 25.0, 25.0, 25.0, 25.0, 25.0});
        
        List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, "CONSTANT_KPI");
        
        assertNotNull(forecasts);
        assertEquals(1, forecasts.size());
        
        DataCount forecast = forecasts.get(0);
        assertNotNull(forecast.getValue());
        assertTrue(((Double) forecast.getValue()) >= 0);
    }

    @Test
    void testGenerateForecast_PreservesMetadata() {
        List<DataCount> historicalData = createTestData(new double[]{10.0, 15.0, 20.0, 25.0, 30.0, 35.0});
        
        List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, "METADATA_KPI");
        
        assertNotNull(forecasts);
        assertEquals(1, forecasts.size());
        
        DataCount forecast = forecasts.get(0);
        assertEquals("TestProject", forecast.getSProjectName());
        assertEquals("TestGroup", forecast.getKpiGroup());
        assertEquals("lstm", forecast.getForecastingModel());
    }

    private List<DataCount> createTestData(double[] values) {
        List<DataCount> dataList = new ArrayList<>();
        
        for (int i = 0; i < values.length; i++) {
            DataCount dataCount = new DataCount();
            dataCount.setValue(values[i]);
            dataCount.setData(String.valueOf(values[i]));
            dataCount.setSProjectName("TestProject");
            dataCount.setKpiGroup("TestGroup");
            dataList.add(dataCount);
        }
        
        return dataList;
    }


    @Test
    void testValidateEnvironment_Success() {
        // Test successful ND4J initialization
        assertDoesNotThrow(() -> lstmForecaster.validateEnvironment());
    }

    @Test
    void testExecuteWithTimeout_Success() throws Exception {
        // Use reflection to test private method
        Method method = LSTMForecaster.class.getDeclaredMethod("executeWithTimeout", Supplier.class, long.class);
        method.setAccessible(true);

        Supplier<INDArray> operation = () -> Nd4j.zeros(1);

        INDArray result = (INDArray) method.invoke(lstmForecaster, operation, 5000L);

        assertNotNull(result);
        assertEquals(1, result.length());
    }

    @Test
    void testExecuteWithTimeout_Timeout() throws Exception {
        Method method = LSTMForecaster.class.getDeclaredMethod("executeWithTimeout", Supplier.class, long.class);
        method.setAccessible(true);

        CountDownLatch latch = new CountDownLatch(1);
        Supplier<INDArray> slowOperation = () -> {
            try {
                latch.await(); // Wait indefinitely
                return Nd4j.zeros(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        };

        assertThrows(InvocationTargetException.class, () ->
                method.invoke(lstmForecaster, slowOperation, 100L));
    }


    @Test
    void testNd4jZeros_Success() throws Exception {
        Field field = LSTMForecaster.class.getDeclaredField("nd4jZeros");
        field.setAccessible(true);
        Function<int[], INDArray> nd4jZeros = (Function<int[], INDArray>) field.get(lstmForecaster);

        INDArray result = nd4jZeros.apply(new int[]{2, 3});

        assertNotNull(result);
        assertEquals(2, result.rows());
        assertEquals(3, result.columns());
    }

    @Test
    void testNormalizeData_NormalRange() throws Exception {
        Method method = LSTMForecaster.class.getDeclaredMethod("normalizeData", List.class);
        method.setAccessible(true);

        List<Double> values = Arrays.asList(10.0, 20.0, 30.0, 40.0);
        double[] result = (double[]) method.invoke(lstmForecaster, values);

        assertEquals(0.0, result[0], 0.001);
        assertEquals(1.0, result[3], 0.001);
    }

    @Test
    void testNormalizeData_ConstantValues() throws Exception {
        Method method = LSTMForecaster.class.getDeclaredMethod("normalizeData", List.class);
        method.setAccessible(true);

        List<Double> values = Arrays.asList(25.0, 25.0, 25.0);
        double[] result = (double[]) method.invoke(lstmForecaster, values);

        for (double value : result) {
            assertEquals(0.5, value, 0.001);
        }
    }

    @Test
    void testCreateSequences_SufficientData() throws Exception {
        Method method = LSTMForecaster.class.getDeclaredMethod("createSequences", double[].class);
        method.setAccessible(true);

        double[] data = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6};
        INDArray[] result = (INDArray[]) method.invoke(lstmForecaster, data);

        assertNotNull(result);
        assertEquals(2, result.length);
        assertTrue(result[0].size(0) > 0);
    }

    @Test
    void testCreateSequences_InsufficientData() throws Exception {
        Method method = LSTMForecaster.class.getDeclaredMethod("createSequences", double[].class);
        method.setAccessible(true);

        double[] data = {0.1, 0.2};
        INDArray[] result = (INDArray[]) method.invoke(lstmForecaster, data);

        assertNotNull(result);
        assertEquals(0, result[0].size(0));
    }

    @Test
    void testBuildLSTMModel() throws Exception {
        Method method = LSTMForecaster.class.getDeclaredMethod("buildLSTMModel");
        method.setAccessible(true);

        MultiLayerNetwork model = (MultiLayerNetwork) method.invoke(lstmForecaster);

        assertNotNull(model);
        assertEquals(3, model.getnLayers());
    }

    @Test
    void testGetLastSequence() throws Exception {
        Method method = LSTMForecaster.class.getDeclaredMethod("getLastSequence", double[].class);
        method.setAccessible(true);

        double[] data = {0.1, 0.2, 0.3, 0.4, 0.5};
        INDArray result = (INDArray) method.invoke(lstmForecaster, data);

        assertNotNull(result);
        assertEquals(1, result.size(0));
        assertEquals(3, result.size(2));
    }

    @Test
    void testDenormalizeValue() throws Exception {
        Method method = LSTMForecaster.class.getDeclaredMethod("denormalizeValue", double.class, double.class, double.class);
        method.setAccessible(true);

        double result = (Double) method.invoke(lstmForecaster, 0.5, 10.0, 30.0);

        assertEquals(20.0, result, 0.001);
    }

}