/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.apis.productivity.config;

import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "productivity")
public class ProductivityGainConfig {
    public static final String CATEGORY_SPEED = "speed";
    public static final String CATEGORY_QUALITY = "quality";
    public static final String CATEGORY_EFFICIENCY = "efficiency";
    public static final String CATEGORY_PRODUCTIVITY = "productivity";

    @Data
    public static class DataPoints {
        private int count;
    }

    private Map<String, Double> weights;

    private DataPoints dataPoints = new DataPoints();

    public Double getWeightForCategory(String category) {
        return this.weights.get(category);
    }

    public Set<String> getAllCategories() {
        return weights.keySet();
    }
}
