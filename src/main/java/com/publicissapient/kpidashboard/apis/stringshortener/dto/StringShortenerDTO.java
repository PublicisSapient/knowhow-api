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

package com.publicissapient.kpidashboard.apis.stringshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Data Transfer Object for String Shortening")
public class StringShortenerDTO {
	@Schema(
			description = "Long String to be shortened",
			example = "This is a very long string that needs to be shortened")
	private String longStateFiltersString;

	@Schema(description = "Shortened String", example = "abc123")
	private String shortStateFiltersString;

	@Schema(
			description = "Long KPI Filters String to be shortened",
			example = "This is a very long KPI filters string that needs to be shortened")
	private String longKPIFiltersString;

	@Schema(description = "Shortened KPI Filters String", example = "kpi456")
	private String shortKPIFilterString;
}
