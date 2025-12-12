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

package com.publicissapient.kpidashboard.apis.analytics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Metrics Proxy Controller to forward frontend metrics to Pushgateway This solves CORS issues by
 * proxying requests through the backend
 *
 * @author KnowHow Team
 */
@RestController
@RequestMapping("/metrics-proxy")
@Slf4j
@CrossOrigin
public class MetricsProxyController {

	@Autowired private RestTemplate restTemplate;

	@Value("${analytics.pushgateway.url:http://localhost:9092}")
	private String pushgatewayBaseUrl;

	/**
	 * Proxy endpoint to send metrics to Pushgateway This avoids CORS issues when sending directly
	 * from frontend
	 */
	@PostMapping("/send")
	public ResponseEntity<String> sendMetricsToPushgateway(@RequestBody MetricsRequest request) {
		try {
			log.info("Received metrics from frontend, forwarding to Pushgateway");
			log.debug("Metrics data: {}", request.getMetrics());

			// Forward to Pushgateway
			String pushgatewayUrl = pushgatewayBaseUrl + "/metrics/job/knowhow_ui";
			log.info("Pushgateway URL: {}", pushgatewayUrl);

			ResponseEntity<String> response =
					restTemplate.postForEntity(pushgatewayUrl, request.getMetrics(), String.class);

			if (response.getStatusCode().is2xxSuccessful()) {
				log.info("✅ Successfully forwarded metrics to Pushgateway");
				return ResponseEntity.ok("Metrics sent successfully");
			} else {
				log.error("❌ Failed to forward metrics to Pushgateway: {}", response.getStatusCode());
				return ResponseEntity.status(response.getStatusCode()).body("Failed to send metrics");
			}

		} catch (Exception e) {
			log.error("❌ Error forwarding metrics to Pushgateway", e);
			return ResponseEntity.internalServerError().body("Error sending metrics: " + e.getMessage());
		}
	}
}
