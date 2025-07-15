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

package com.publicissapient.kpidashboard.apis.management.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * Configuration for a dashboard, including its name and associated APIs.
 * 
 * @author shunaray
 */
@Data
public class DashboardConfiguration {

	@NotBlank(message = "Dashboard name must not be empty")
	@Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid Dashboard name: ${validatedValue}. Only alphanumeric, underscore, dash allowed")
	private String name;

	@NotEmpty(message = "At least one API must be specified")
	private List<@Pattern(regexp = "/.*", message = "API path must start with '/'") String> apis;

}