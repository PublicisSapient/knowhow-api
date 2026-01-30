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

package com.publicissapient.kpidashboard.apis.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "KPI Recommendation Request Data Transfer Object")
public class KpiRecommendationRequestDTO implements Serializable {

	@Serial private static final long serialVersionUID = 1L;

	@Schema(description = "List of Identifiers", example = "[\"id1\", \"id2\"]")
	private String[] ids;

	@Schema(description = "List of KPI Identifiers", example = "[\"kpiId1\", \"kpiId2\"]")
	private List<String> kpiIdList;

	@Schema(description = "Recommendation for", example = "PROJECT")
	private String recommendationFor;

	@Schema(
			description = "Selected Map",
			example = "{\"key1\": [\"value1\", \"value2\"], \"key2\": [\"value3\"]}")
	private Map<String, List<String>> selectedMap;

	@Schema(description = "Level", example = "2")
	private int level;

	@Schema(description = "Label", example = "Sprint 1")
	private String label;

	@Schema(description = "List of Sprints Included", example = "[\"Sprint 1\", \"Sprint 2\"]")
	private List<String> sprintIncluded;
}
