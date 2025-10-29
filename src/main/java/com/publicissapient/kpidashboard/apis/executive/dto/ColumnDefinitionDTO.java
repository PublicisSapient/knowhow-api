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

import lombok.Builder;
import lombok.Data;

/**
 * DTO representing a column definition for the executive dashboard. Defines the structure and
 * display properties of a column.
 */
@Data
@Builder
public class ColumnDefinitionDTO {
	/** The field name that maps to the data property in the row. */
	private String field;

	/** The display header text for the column. */
	private String header;
}
