package com.publicissapient.kpidashboard.apis.aiusage.service;

import com.publicissapient.kpidashboard.apis.aiusage.config.AIUsageFileFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class AIUsageFileFormatTest {

    private AIUsageFileFormat aiUsageFileFormat;

    @BeforeEach
    void setUp() {
        aiUsageFileFormat = new AIUsageFileFormat();
    }

    @Test
    void testGetExpectedHeaders_NullMappings() {
        aiUsageFileFormat.setMappings(null);
        List<String> expectedHeaders = aiUsageFileFormat.getExpectedHeaders();
        assertEquals(aiUsageFileFormat.getRequiredHeaders(), expectedHeaders);
    }

    @Test
    void testGetExpectedHeaders_ValidMappings() {
        List<String> mappings = List.of("email", "promptCount", "businessUnit", "vertical", "account");
        aiUsageFileFormat.setMappings(mappings);
        List<String> expectedHeaders = aiUsageFileFormat.getExpectedHeaders();
        assertEquals(mappings, expectedHeaders);
    }

    @Test
    void testGetHeaderToMappingMap_NullMappings() {
        aiUsageFileFormat.setMappings(null);
        HashMap<String, String> expectedMap = new HashMap<>();
        Map<String, String> headerToMappingMap = aiUsageFileFormat.getHeaderToMappingMap();
        assertEquals(expectedMap, headerToMappingMap);
    }

    @Test
    void testGetHeaderToMappingMap_ValidMappings() {
        List<String> mappings = List.of("email", "promptCount", "businessUnit", "vertical", "account");
        aiUsageFileFormat.setMappings(mappings);
        Map<String, String> headerToMappingMap = aiUsageFileFormat.getHeaderToMappingMap();
        for (int i = 0; i < mappings.size(); i++) {
            assertEquals(aiUsageFileFormat.getRequiredHeaders().get(i), headerToMappingMap.get(mappings.get(i)));
        }
    }
}
