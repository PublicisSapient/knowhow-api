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

package com.publicissapient.kpidashboard.apis.auth.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.publicissapient.kpidashboard.common.constant.CommonConstant;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "auth.endpoints.api-key")
public class AuthApiKeyEndpointsConfig {
	private String paths;
	private String headerName;

	public Set<String> getPaths() {
		if (StringUtils.isNotBlank(this.paths)) {
			return Arrays.stream(this.paths.split(CommonConstant.COMMA)).collect(Collectors.toSet());
		}
		return Collections.emptySet();
	}
}
