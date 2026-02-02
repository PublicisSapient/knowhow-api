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

package com.publicissapient.kpidashboard.apis.model;

import java.util.Objects;

import org.bson.types.ObjectId;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Represents the account filtered data. */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Schema(description = "Represents the account filtered data")
public class AccountFilteredData {

	@Schema(description = "Node ID", example = "NODE123")
	private String nodeId;

	@Schema(description = "Node Name", example = "Sprint 1")
	private String nodeName;

	@Schema(description = "Node Display Name", example = "Sprint 1 - Q1")
	private String nodeDisplayName;

	@Schema(description = "Start Date of sprint", example = "2023-01-01")
	private String sprintStartDate;

	@Schema(description = "End Date of sprint", example = "2023-01-15")
	private String sprintEndDate;

	@Schema(description = "Release Date", example = "2023-02-01")
	private String releaseDate;

	@Schema(description = "From Date", example = "2023-01-01")
	private String dateFrom;

	@Schema(description = "To Date", example = "2023-01-15")
	private String dateTo;

	@Schema(description = "Path", example = "/Project/Release/Sprint")
	private String path;

	@Schema(description = "Label Name", example = "Sprint Label")
	private String labelName;

	@Schema(description = "Parent ID", example = "PARENT123")
	private String parentId;

	@Schema(description = "Sprint State", example = "ACTIVE")
	private String sprintState;

	@Schema(description = "Level", example = "2")
	private int level;

	@Schema(description = "Release Start Date", example = "2023-01-01")
	private String releaseEndDate;

	@Schema(description = "Release End Date", example = "2023-01-15")
	private String releaseStartDate;

	@Schema(description = "Release State", example = "PLANNED")
	private String releaseState;

	@Schema(description = "Basic Project Configuration ID", example = "60d5f4832f8fb814c8d6f9b1")
	private ObjectId basicProjectConfigId;

	@Schema(description = "Indicates if the sprint is on hold", example = "false")
	private boolean onHold;

	@Override
	public boolean equals(Object obj) {
		boolean isEqual = false;
		if ((null != obj && this.getClass() != obj.getClass()) || null == obj) {
			return false;
		}
		AccountFilteredData other = (AccountFilteredData) obj;
		if (obj instanceof AccountFilteredData
				&& this.nodeId.equals(other.nodeId)
				&& (null == this.parentId || this.parentId.equals(other.parentId))) {
			isEqual = true;
		}
		return isEqual;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.nodeId, this.parentId);
	}
}
