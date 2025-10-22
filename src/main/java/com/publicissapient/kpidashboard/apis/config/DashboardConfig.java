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

package com.publicissapient.kpidashboard.apis.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration class for managing dashboard health settings. This class is used to load and
 * validate dashboard-related properties from the application configuration.
 */
@Data
@Slf4j
@Validated
@Configuration
@ConfigurationProperties(prefix = "dashboard")
public class DashboardConfig {

	@NotEmpty(message = "At least one dashboard type must be defined")
	private Map<@NotEmpty String, @Valid BoardType> types;

	@DecimalMin(value = "0.0", message = "API error threshold must be at least 0.0")
	@DecimalMax(value = "100.0", message = "API error threshold must be at most 100.0")
	@NotNull(message = "API error threshold must be specified in the configuration")
	private double maxApiErrorThreshold;

	@NotEmpty(message = "API base path must be specified")
	@Pattern(regexp = "^/.*[^/]$", message = "API base path must start with '/' and not end with '/'")
	private String healthApiBasePath;

	@Data
	public static class BoardType {
		@NotEmpty(message = "Each type must define at least one board")
		private Map<@NotEmpty String, @Valid Board> boards;
	}

	@Data
	public static class Board {
		@NotEmpty(message = "Each board must define at least one API endpoint")
		private List<
						@NotEmpty(message = "API path must not be empty")
						@Pattern(
								regexp = "^/.*[^/]$",
								message = "API path must start with '/' and not end with '/'")
						String>
				apis;
	}
}
