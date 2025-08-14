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
package com.publicissapient.kpidashboard.apis.executive.rest;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.service.ExecutiveServiceImpl;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;

import lombok.RequiredArgsConstructor;

/**
 * Controller for handling executive dashboard requests.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/executive")
public class ExecutiveController {

    private static final Logger log = LoggerFactory.getLogger(ExecutiveController.class);

    private final ExecutiveServiceImpl executiveService;

    /**
     * Retrieves executive dashboard data based on the provided KPI request.
     *
     * @param kpiRequest The KPI request containing filter criteria
     * @param iskanban   Flag indicating whether to use Kanban (true) or Scrum (false) strategy
     * @return Executive dashboard response with project metrics
     */
    @PostMapping()
    public ResponseEntity<ExecutiveDashboardResponseDTO> getExecutive(
            @Valid @RequestBody KpiRequest kpiRequest,
            @RequestParam(required = true) boolean iskanban) {
        
        log.info("Processing executive dashboard request for {} methodology", iskanban ? "Kanban" : "Scrum");
        
        ExecutiveDashboardResponseDTO response = iskanban
                ? executiveService.getExecutiveDashboardKanban(kpiRequest)
                : executiveService.getExecutiveDashboardScrum(kpiRequest);
                
        log.debug("Successfully processed executive dashboard request");
        return ResponseEntity.ok(response);
    }
}
