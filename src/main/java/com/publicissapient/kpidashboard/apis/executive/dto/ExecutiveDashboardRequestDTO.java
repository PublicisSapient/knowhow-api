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
package com.publicissapient.kpidashboard.apis.executive.dto;

import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for executive dashboard request payload. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutiveDashboardRequestDTO {

	/** The hierarchy level for the request */
	@NotNull(message = "Level is mandatory")
	private Integer level;

	/** The label for the request (e.g., "account") */
	@NotNull(message = "Label is mandatory")
	private String label;

	/** The date range type (e.g., "Weeks"). Optional, not used in Scrum. */
	private String date;

	/** The duration for the date range. Optional, not used in Scrum. */
	private Integer duration;

	/** The parent ID. If absent, process all account level IDs. */
	private String parentId;
}
