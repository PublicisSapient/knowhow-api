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

package com.publicissapient.kpidashboard.apis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Analytics configuration exposed to frontend
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "analytics")
public class AnalyticsConfig {
	@Value("${analytics.switch}")
	private boolean analyticsSwitch;

	private Grafana grafana = new Grafana();

	private Google google = new Google();

	@Data
	public static class Grafana {
		private boolean enabled;

		private Rollout rollout = new Rollout();

		@Data
		public static class Rollout {
			private int percentage;
		}
	}

	@Data
	public static class Google {
		private boolean enabled;
	}
}
