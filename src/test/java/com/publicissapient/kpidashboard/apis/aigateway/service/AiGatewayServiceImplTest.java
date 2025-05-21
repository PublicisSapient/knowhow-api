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

package com.publicissapient.kpidashboard.apis.aigateway.service;

import com.publicissapient.kpidashboard.apis.aigateway.client.AiGatewayClient;
import com.publicissapient.kpidashboard.apis.aigateway.config.AiGatewayConfig;
import com.publicissapient.kpidashboard.apis.aigateway.dto.request.ChatGenerationRequestDTO;
import com.publicissapient.kpidashboard.apis.aigateway.dto.response.AiProviderDTO;
import com.publicissapient.kpidashboard.apis.aigateway.dto.response.AiProvidersResponseDTO;
import com.publicissapient.kpidashboard.apis.aigateway.dto.response.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.apis.errors.ApiClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGatewayServiceImplTest {

    @Mock
    private AiGatewayClient aiGatewayClient;

    @Mock
    private AiGatewayConfig aiGatewayConfig;

    @InjectMocks
    private AiGatewayServiceImpl aiGatewayService;

    @Mock
    private Call<AiProvidersResponseDTO> providersCall;

    @Mock
    private Call<ChatGenerationResponseDTO> chatCall;

    @Captor
    private ArgumentCaptor<Callback<ChatGenerationResponseDTO>> callbackCaptor;

    @Test
    void testGetProvidersSuccess() throws IOException {
        when(aiGatewayClient.getProviders()).thenReturn(providersCall);
        AiProvidersResponseDTO expectedResponse = new AiProvidersResponseDTO(List.of(new AiProviderDTO("openai", List.of("gpt3", "gpt4"))));
        when(providersCall.execute()).thenReturn(Response.success(expectedResponse));

        AiProvidersResponseDTO actualResponse = aiGatewayService.getProviders();

        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    void testGetProvidersFailure() throws IOException {
        when(aiGatewayClient.getProviders()).thenReturn(providersCall);
        when(providersCall.execute()).thenThrow(new IOException("Network error"));

        assertThrows(ApiClientException.class, () -> aiGatewayService.getProviders());
    }

    @Test
    void testGenerateChatResponseSuccess() throws IOException {
        when(aiGatewayClient.generate(any(ChatGenerationRequestDTO.class))).thenReturn(chatCall);
        ChatGenerationResponseDTO expectedResponse = new ChatGenerationResponseDTO("test prompt");
        when(chatCall.execute()).thenReturn(Response.success(expectedResponse));

        ChatGenerationResponseDTO actualResponse = aiGatewayService.generateChatResponse("test prompt");

        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    void testGenerateChatResponseFailure() throws IOException {
        when(aiGatewayClient.generate(any(ChatGenerationRequestDTO.class))).thenReturn(chatCall);
        when(chatCall.execute()).thenThrow(new IOException("Network error"));

        assertThrows(ApiClientException.class, () -> aiGatewayService.generateChatResponse("test prompt"));
    }

    @Test
    void testGenerateChatResponseAsync() {
        when(aiGatewayClient.generate(any(ChatGenerationRequestDTO.class))).thenReturn(chatCall);
        doNothing().when(chatCall).enqueue(callbackCaptor.capture());

        aiGatewayService.generateChatResponseAsync("test prompt", mock(Callback.class));

        verify(chatCall).enqueue(any());
    }
}
