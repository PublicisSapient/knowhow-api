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

package com.publicissapient.kpidashboard.apis.kpiintegration.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.publicissapient.kpidashboard.apis.kpiintegration.service.KpiIntegrationServiceImpl;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @author kunkambl
 */
@RunWith(MockitoJUnitRunner.class)
public class KpiIntegrationControllerTest {

	@InjectMocks private KpiIntegrationController kpiIntegrationController;

	@Mock private KpiIntegrationServiceImpl maturityService;

	@Mock private HttpServletRequest httpServletRequest;

	@Test
	public void testGetScrumKpiValuesSuccess() {
		KpiRequest kpiRequest = new KpiRequest();
		when(maturityService.processScrumKpiRequest(kpiRequest))
				.thenReturn(Collections.singletonList(new KpiElement()));

		ResponseEntity<List<KpiElement>> responseEntity =
				kpiIntegrationController.getScrumKpiValues(kpiRequest);

		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
		assertFalse(responseEntity.getBody().isEmpty());
		verify(maturityService).processScrumKpiRequest(kpiRequest);
	}

	@Test
	public void testGetScrumKpiValuesOk() {
		KpiRequest kpiRequest = new KpiRequest();
		when(maturityService.processScrumKpiRequest(kpiRequest))
				.thenReturn(Collections.singletonList(new KpiElement()));
		when(maturityService.processScrumKpiRequest(kpiRequest)).thenReturn(Collections.emptyList());

		ResponseEntity<List<KpiElement>> responseEntity =
				kpiIntegrationController.getScrumKpiValues(kpiRequest);

		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
		assertTrue(responseEntity.getBody().isEmpty());
		verify(maturityService).processScrumKpiRequest(kpiRequest);
	}
}
