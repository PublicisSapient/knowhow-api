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
package com.publicissapient.kpidashboard.apis.executive.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardDataDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveMatrixDTO;
import com.publicissapient.kpidashboard.apis.executive.strategy.ExecutiveDashboardStrategyFactory;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service implementation for executive dashboard functionality.
 * Uses Strategy pattern to delegate to appropriate dashboard strategy (Kanban/Scrum).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutiveServiceImpl implements ExecutiveService {

    private static final String KANBAN = "kanban";
    private static final String SCRUM = "scrum";
    private final ExecutiveDashboardStrategyFactory strategyFactory;

    @Override
    public ExecutiveDashboardResponseDTO getExecutiveDashboardScrum(KpiRequest kpiRequest) {
        return strategyFactory.getStrategy(SCRUM).getExecutiveDashboard(kpiRequest);
    }

    @Override
    public ExecutiveDashboardResponseDTO getExecutiveDashboardKanban(KpiRequest kpiRequest) {
        return strategyFactory.getStrategy(KANBAN).getExecutiveDashboard(kpiRequest);
    }

}
