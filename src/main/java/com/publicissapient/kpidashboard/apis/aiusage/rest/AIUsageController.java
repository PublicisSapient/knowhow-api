/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *    http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.aiusage.rest;

import com.publicissapient.kpidashboard.apis.aiusage.dto.LatestAIUsageResponse;
import com.publicissapient.kpidashboard.apis.aiusage.rest.contract.AIUsageAPI;
import com.publicissapient.kpidashboard.apis.aiusage.service.AIUsageService;
import com.publicissapient.kpidashboard.apis.aiusage.service.SharedDataService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
public class AIUsageController implements AIUsageAPI {

    private final AIUsageService aiUsageService;
    private final SharedDataService sharedDataService;

    @Override
    public LatestAIUsageResponse getProcessingStatusFromSharedDataService(@RequestParam String email) {
        return sharedDataService.getAiUsageStatus(email);
    }

}

