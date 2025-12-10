/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *    http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.aiusage.dto;

import com.publicissapient.kpidashboard.apis.aiusage.enums.HierarchyLevelType;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsageStatistics;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AIUsageStatisticsDTO {
	@Size(max = 100)
	private String levelName;

	@Size(max = 100)
	private String organizationEntityName;

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private Instant statsDate;

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private Instant ingestTimestamp;

	private AIUsageSummary usageSummary;

	public AIUsageStatisticsDTO(AIUsageStatistics aiUsageStatistics) {
		if (aiUsageStatistics == null) {
			this.levelName = "";
			this.organizationEntityName = "";
			this.statsDate = null;
			this.ingestTimestamp = null;
			this.usageSummary = new AIUsageSummary(0L, 0L, 0L, 0L, null);
			return;
		}

		if (aiUsageStatistics.getLevelType() == null) {
			this.levelName = StringUtils.EMPTY;
		} else {
			this.levelName = Objects.toString(
					HierarchyLevelType.valueOf(aiUsageStatistics.getLevelType()).getLevelName(),
					StringUtils.EMPTY);
		}
		this.organizationEntityName = Objects.toString(aiUsageStatistics.getLevelName(), StringUtils.EMPTY);
		this.statsDate = aiUsageStatistics.getStatsDate();
		this.ingestTimestamp = aiUsageStatistics.getIngestTimestamp();
		this.usageSummary = aiUsageStatistics.getUsageSummary() != null
						? aiUsageStatistics.getUsageSummary()
						: new AIUsageSummary(0L, 0L, 0L, 0L, null);
	}
}
