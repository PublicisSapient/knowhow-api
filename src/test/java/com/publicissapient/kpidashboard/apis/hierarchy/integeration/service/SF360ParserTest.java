package com.publicissapient.kpidashboard.apis.hierarchy.integeration.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.publicissapient.kpidashboard.apis.errors.HierarchyParsingException;
import com.publicissapient.kpidashboard.apis.hierarchy.integeration.dto.HierarchyDetails;

public class SF360ParserTest {

    private SF360Parser sf360Parser;

    @Before
    public void setUp() {
        sf360Parser = new SF360Parser();
    }

    @Test
    public void testConvertToHierachyDetail_ValidJson() {
        String validJson = "{" +
                "\"data\": [{" +
                "\"hierarchyGroup\": \"SF360Hierarchy\"," +
                "\"hierarchyDetails\": {\"hierarchyNode\": [{\"Portfolio\": \"Santander\",\"Portfolio_unique_id\": \"custom_QjRmdKP2RKlbndOySfIzOdaTkcDqKSbg\",\"Portfolio_id\": \"43646\",\"Account\": \"Santander Consumer Bank GmbH\",\"Account_unique_id\": \"p.semea/apacnon-oracle_financialservicesnon-oracle_0013g00000rKGpWAAW\",\"Account_id\": \"11578\",\"poc\": null,\"alternate_poc\": null,\"pid\": null,\"archetype\": null,\"probability\": null,\"clientPartnerLeadName\": null,\"clientPartnerLeadEmail\": null,\"deliveryLeadName\": \"Andrei Baiasiu\",\"deliveryLeadEmail\": \"andrei.baiasiu@publicissapient.com\",\"engineeringLeadName\": null,\"engineeringLeadEmail\": null,\"buGroup\": null,\"team\": null,\"projectType\": null,\"capabilityGroup\": null,\"startDate\": null,\"endDate\": null,\"Vertical\": \"Financial Services Non-Oracle\",\"Vertical_unique_id\": \"p.semea/apacnon-oracle_financialservicesnon-oracle\",\"Vertical_id\": \"10512\",\"BU\": \"P.S EMEA / APAC Non-Oracle\",\"BU_unique_id\": \"p.semea/apacnon-oracle\",\"BU_id\": \"10490\",\"Root\": \"Root\",\"Root_unique_id\": \"SF360HIERARCHY_ROOT\",\"Root_id\": \"10329\",\"Id\": \"10329\"}]}}]" +
                "}";

        HierarchyDetails result = sf360Parser.convertToHierachyDetail(validJson);

        assertNotNull("Result should not be null", result);
        // Add more assertions here based on HierarchyDetails fields
    }

    @Test
    public void testConvertToHierachyDetail_InvalidJson() {
        String invalidJson = "{ invalid json }";

        try {
            sf360Parser.convertToHierachyDetail(invalidJson);
            fail("Expected HierarchyParsingException to be thrown");
        } catch (HierarchyParsingException e) {
            // Expected exception
        }
    }
}
