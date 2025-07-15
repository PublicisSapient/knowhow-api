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

package com.publicissapient.kpidashboard.apis.management.service;

import java.util.Map;

/**
 * 
 * @author shunaray
 */
public interface DashboardHealthService {

	/**
	 * Retrieves the overall health details of the application, including app
	 * health, metrics, dashboard statuses, and dashboard links.
	 *
	 * @return A map containing the overall health details of the application. Keys
	 *         include:
	 *         <ul>
	 *         <li><b>appHealth</b>: The health status of the application.</li>
	 *         <li><b>metrics</b>: Global application metrics.</li>
	 *         <li><b>dashboardStatuses</b>: Statuses of all configured
	 *         dashboards.</li>
	 *         <li><b>dashboards</b>: Links to individual dashboard health
	 *         endpoints.</li>
	 *         </ul>
	 */
	Map<String, Object> healthDetails();

	/**
	 * Retrieves the health details of a specific dashboard, including its status
	 * and API metrics.
	 *
	 * @param dashboardName
	 *            The name of the dashboard for which health details are to be
	 *            retrieved.
	 * @return A map containing the health details of the specified dashboard. Keys
	 *         include:
	 *         <ul>
	 *         <li><b>dashboardName</b>: The name of the dashboard.</li>
	 *         <li><b>status</b>: The overall health status of the dashboard (e.g.,
	 *         UP, DOWN).</li>
	 *         <li><b>apiMetrics</b>: A list of metrics for each API associated with
	 *         the dashboard.</li>
	 *         </ul>
	 * @throws IllegalArgumentException
	 *             if the specified dashboard name is not found.
	 */
	Map<String, Object> dashboardHealth(String dashboardName);
}
