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

package com.publicissapient.kpidashboard.apis.aiusage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai-usage-file-format.required-headers")
public class AIUsageFileFormat {
    private List<String> requiredHeaders = List.of("email", "promptCount", "businessUnit", "vertical", "account");

    private List<String> mappings;

    public Map<String, String> getHeaderToMappingMap() {
        Map<String, String> headerToMappingMap = new HashMap<>();
        if (Objects.nonNull(requiredHeaders) && Objects.nonNull(mappings) && requiredHeaders.size() == mappings.size()) {
            for (int i = 0; i < requiredHeaders.size(); i++) {
                headerToMappingMap.put(requiredHeaders.get(i), mappings.get(i));
            }
        }
        return headerToMappingMap;
    }
}
