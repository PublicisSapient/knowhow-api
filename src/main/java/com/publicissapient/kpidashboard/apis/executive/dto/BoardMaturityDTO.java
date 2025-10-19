/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.executive.dto;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;

import lombok.Builder;
import lombok.Data;

/**
 * DTO representing the board maturity metrics for a project. Dynamically holds key-value pairs of
 * board names and their maturity levels.
 */
@Data
@Builder
public class BoardMaturityDTO {
	/** Map of board names to their maturity levels. Example: {"speed": "M1", "quality": "M3"} */
	@Builder.Default private Map<String, String> metrics = new HashMap<>();

	/**
	 * Adds a board metric to the maturity map.
	 *
	 * @param boardName The name of the board (e.g., "speed", "quality")
	 * @param maturityLevel The maturity level (e.g., "M1", "M2")
	 */
	public void addMetric(String boardName, String maturityLevel) {
		metrics.put(boardName, maturityLevel);
	}

	/** Jackson annotation to serialize the metrics map as top-level properties. */
	@JsonAnyGetter
	public Map<String, String> getMetrics() {
		return metrics;
	}
}
