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

import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardRequestDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.service.ExecutiveService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller for handling executive dashboard requests.
 */
/**
 * Controller for handling executive dashboard requests.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/executive")
@Slf4j
public class ExecutiveController {

    private final ExecutiveService executiveService;

    /**
     * Retrieves executive dashboard data based on the provided request.
     *
     * @param request The executive dashboard request containing filter criteria
     * @param iskanban Flag indicating whether to use Kanban (true) or Scrum (false) strategy
     * @return Executive dashboard response with project metrics
     */
    @PostMapping()
    public ResponseEntity<ServiceResponse> getExecutive(
            @Valid @RequestBody ExecutiveDashboardRequestDTO request,
            @RequestParam(required = true) boolean iskanban) {

        log.info("Processing executive dashboard request for {} methodology with level: {}, label: {}",
                iskanban ? "Kanban" : "Scrum", request.getLevel(), request.getLabel());
        ExecutiveDashboardResponseDTO response= null;
      try {
          response = iskanban
                  ? executiveService.getExecutiveDashboardKanban(request)
                  : executiveService.getExecutiveDashboardScrum(request);

          log.debug("Successfully processed executive dashboard request");
      } catch (Exception e) {
          log.error("Error processing executive dashboard request", e);
          return ResponseEntity.ok(new ServiceResponse(false,"Error Occurred, Try again later", response));
      }
        return ResponseEntity.ok(new ServiceResponse(true,"Data fetched successfully", response));
    }
}
