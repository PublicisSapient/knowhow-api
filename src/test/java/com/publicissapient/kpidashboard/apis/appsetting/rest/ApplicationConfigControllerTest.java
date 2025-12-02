package com.publicissapient.kpidashboard.apis.appsetting.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.publicissapient.kpidashboard.apis.appsetting.service.ApplicationConfigServiceImpl;
import com.publicissapient.kpidashboard.apis.model.ApplicationConfigDto;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

@RunWith(MockitoJUnitRunner.class)
public class ApplicationConfigControllerTest {

    @InjectMocks
    private ApplicationConfigController applicationConfigController;

    @Mock
    private ApplicationConfigServiceImpl applicationConfigService;

    private ApplicationConfigDto mockConfigDto;

    @Before
    public void setUp() {
        mockConfigDto = new ApplicationConfigDto();
        mockConfigDto.setTotalTeamSize(30);
        mockConfigDto.setAvgCostPerTeamMember(100000.0);
        mockConfigDto.setTimeDuration("Per Year");
        mockConfigDto.setProductDocumentation("https://docs.example.com/product");
        mockConfigDto.setApiDocumentation("https://docs.example.com/api");
        mockConfigDto.setVideoTutorials("https://videos.example.com/tutorials");
        mockConfigDto.setRaiseTicket("https://support.example.com/tickets");
        mockConfigDto.setSupportChannel("https://chat.example.com/support");
    }

    @Test
    public void testGetApplicationConfig_Success() {
        when(applicationConfigService.getApplicationConfig()).thenReturn(mockConfigDto);

        ResponseEntity<ServiceResponse> response = applicationConfigController.getApplicationConfig();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ServiceResponse serviceResponse = response.getBody();
        assertNotNull(serviceResponse);
        assertTrue(serviceResponse.getSuccess());
        assertEquals("Application configuration retrieved successfully", serviceResponse.getMessage());

        ApplicationConfigDto responseData = (ApplicationConfigDto) serviceResponse.getData();
        assertNotNull(responseData);
        assertEquals(Integer.valueOf(30), responseData.getTotalTeamSize());
        assertEquals(Double.valueOf(100000.0), responseData.getAvgCostPerTeamMember());
        assertEquals("Per Year", responseData.getTimeDuration());
        assertEquals("https://docs.example.com/product", responseData.getProductDocumentation());
        assertEquals("https://docs.example.com/api", responseData.getApiDocumentation());
        assertEquals("https://videos.example.com/tutorials", responseData.getVideoTutorials());
        assertEquals("https://support.example.com/tickets", responseData.getRaiseTicket());
        assertEquals("https://chat.example.com/support", responseData.getSupportChannel());

        verify(applicationConfigService, times(1)).getApplicationConfig();
    }

    @Test
    public void testGetApplicationConfig_WithNullValues() {
        ApplicationConfigDto configWithNulls = new ApplicationConfigDto();
        configWithNulls.setTotalTeamSize(null);
        configWithNulls.setAvgCostPerTeamMember(null);
        configWithNulls.setTimeDuration(null);
        configWithNulls.setProductDocumentation(null);
        configWithNulls.setApiDocumentation(null);
        configWithNulls.setVideoTutorials(null);
        configWithNulls.setRaiseTicket(null);
        configWithNulls.setSupportChannel(null);

        when(applicationConfigService.getApplicationConfig()).thenReturn(configWithNulls);

        ResponseEntity<ServiceResponse> response = applicationConfigController.getApplicationConfig();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ServiceResponse serviceResponse = response.getBody();
        assertNotNull(serviceResponse);
        assertTrue(serviceResponse.getSuccess());

        ApplicationConfigDto responseData = (ApplicationConfigDto) serviceResponse.getData();
        assertNotNull(responseData);

        verify(applicationConfigService, times(1)).getApplicationConfig();
    }

    @Test
    public void testGetApplicationConfig_WithEmptyStrings() {
        ApplicationConfigDto configWithEmptyStrings = new ApplicationConfigDto();
        configWithEmptyStrings.setTotalTeamSize(0);
        configWithEmptyStrings.setAvgCostPerTeamMember(0.0);
        configWithEmptyStrings.setTimeDuration("");
        configWithEmptyStrings.setProductDocumentation("");
        configWithEmptyStrings.setApiDocumentation("");
        configWithEmptyStrings.setVideoTutorials("");
        configWithEmptyStrings.setRaiseTicket("");
        configWithEmptyStrings.setSupportChannel("");

        when(applicationConfigService.getApplicationConfig()).thenReturn(configWithEmptyStrings);

        ResponseEntity<ServiceResponse> response = applicationConfigController.getApplicationConfig();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ServiceResponse serviceResponse = response.getBody();
        assertNotNull(serviceResponse);
        assertTrue(serviceResponse.getSuccess());

        ApplicationConfigDto responseData = (ApplicationConfigDto) serviceResponse.getData();
        assertNotNull(responseData);
        assertEquals(Integer.valueOf(0), responseData.getTotalTeamSize());
        assertEquals(Double.valueOf(0.0), responseData.getAvgCostPerTeamMember());
        assertEquals("", responseData.getTimeDuration());

        verify(applicationConfigService, times(1)).getApplicationConfig();
    }

    @Test
    public void testGetApplicationConfig_ServiceCallVerification() {
        when(applicationConfigService.getApplicationConfig()).thenReturn(mockConfigDto);

        applicationConfigController.getApplicationConfig();

        verify(applicationConfigService, times(1)).getApplicationConfig();
    }

    @Test
    public void testGetApplicationConfig_ResponseStructure() {
        when(applicationConfigService.getApplicationConfig()).thenReturn(mockConfigDto);

        ResponseEntity<ServiceResponse> response = applicationConfigController.getApplicationConfig();

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        assertTrue(response.getBody().getData() instanceof ApplicationConfigDto);
    }
}