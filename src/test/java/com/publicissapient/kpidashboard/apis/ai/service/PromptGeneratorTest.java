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

package com.publicissapient.kpidashboard.apis.ai.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.publicissapient.kpidashboard.apis.ai.constants.PromptKeys;
import com.publicissapient.kpidashboard.apis.ai.model.PromptDetails;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleValueWrapper;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyFilterDataFactory;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceKanbanImpl;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.AdditionalFilterCategory;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.ProjectHierarchy;
import com.publicissapient.kpidashboard.common.repository.application.AdditionalFilterCategoryRepository;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelService;
import com.publicissapient.kpidashboard.common.service.ProjectHierarchyService;

@RunWith(MockitoJUnitRunner.class)
public class PromptGeneratorTest {

    @Mock
    private CacheService cacheService;

    private PromptGenerator promptGenerator;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

		PromptDetails kpiRecommendationPrompt = new PromptDetails(PromptKeys.KPI_RECOMMENDATION_PROMPT,
				"Prompt for KPI recommendation", "Recommendation format", Collections.singletonList("DummyField1"),
				"DummyField2", "DummyField3", Collections.singletonList("DummyField4"));

		PromptDetails kpiCorrelationAnalysisReport = new PromptDetails(PromptKeys.KPI_CORRELATION_ANALYSIS_REPORT,
				"Prompt for KPI correlation analysis", "Analysis format", Collections.singletonList("DummyField1"),
				"DummyField2", "DummyField3", Collections.singletonList("DummyField4"));

		when(cacheService.getPromptDetails()).thenReturn(Map.of(PromptKeys.KPI_RECOMMENDATION_PROMPT,
				kpiRecommendationPrompt, PromptKeys.KPI_CORRELATION_ANALYSIS_REPORT, kpiCorrelationAnalysisReport));
        promptGenerator = new PromptGenerator(cacheService);

    }

    @Test
    public void testGetKpiRecommendationPromptValid() throws Exception {

        // Inputs
        Map<String, Object> kpiDataByProject = Map.of("key1", "value1");
        String userRole = "Admin";

        // Execution
        String result = promptGenerator.getKpiRecommendationPrompt(kpiDataByProject, userRole);

        // Verification
        assertNotNull(result);
    }

}