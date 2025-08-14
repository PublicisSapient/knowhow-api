package com.publicissapient.kpidashboard.apis.executive.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardDataDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveMatrixDTO;
import com.publicissapient.kpidashboard.apis.executive.service.ExecutiveService;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;

import java.util.Collections;

@ExtendWith(SpringExtension.class)
@WebMvcTest(ExecutiveController.class)
public class ExecutiveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExecutiveService executiveService;

    @Autowired
    private ObjectMapper objectMapper;

    private KpiRequest kpiRequest;
    private ExecutiveDashboardResponseDTO responseDTO;

    @BeforeEach
    public void setUp() {
        kpiRequest = new KpiRequest();
        responseDTO = ExecutiveDashboardResponseDTO.builder()
                .data(ExecutiveDashboardDataDTO.builder()
                        .matrix(ExecutiveMatrixDTO.builder()
                                .rows(Collections.emptyList())
                                .build())
                        .build())
                .build();
    }

    @Test
    public void testGetExecutiveDashboard_Kanban() throws Exception {
        when(executiveService.getExecutiveDashboardKanban(any(KpiRequest.class))).thenReturn(responseDTO);

        mockMvc.perform(post("/executive?isKanban=true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(kpiRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matrix.rows").isArray());
    }

    @Test
    public void testGetExecutiveDashboard_Scrum() throws Exception {
        when(executiveService.getExecutiveDashboardScrum(any(KpiRequest.class))).thenReturn(responseDTO);

        mockMvc.perform(post("/executive?isKanban=false")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(kpiRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matrix.rows").isArray());
    }
}
