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

package com.publicissapient.kpidashboard.apis.usermanagement.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.usermanagement.dto.request.UserRequestDTO;
import com.publicissapient.kpidashboard.apis.usermanagement.dto.response.UserResponseDTO;
import com.publicissapient.kpidashboard.apis.usermanagement.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

	private MockMvc mockMvc;

	@Mock private UserService userService;

	@InjectMocks private UserController userController;

	private ObjectMapper mapper;

	@BeforeEach
	void setup() {
		mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
		mapper = new ObjectMapper();
	}

	@Test
	void testSaveUserInfo_Success() throws Exception {
		UserRequestDTO requestDTO = new UserRequestDTO();
		requestDTO.setUsername("testUser");

		// Create response DTO
		UserResponseDTO responseDTO = new UserResponseDTO();
		responseDTO.setUsername("testUser");

		// Create service response
		ServiceResponse serviceResponse =
				new ServiceResponse(true, "User information saved successfully", responseDTO);

		when(userService.saveUserInfo(anyString())).thenReturn(serviceResponse);

		mockMvc
				.perform(
						post("/usermanagement/save")
								.contentType(MediaType.APPLICATION_JSON)
								.content(mapper.writeValueAsString(requestDTO)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("User information saved successfully"))
				.andExpect(jsonPath("$.data.username").value("testUser"))
				.andExpect(jsonPath("$.data.message").doesNotExist());
	}

	@Test
	void testSaveUserInfo_EmptyUsername() throws Exception {
		UserRequestDTO requestDTO = new UserRequestDTO();
		requestDTO.setUsername("");

		mockMvc
				.perform(
						post("/usermanagement/save")
								.contentType(MediaType.APPLICATION_JSON)
								.content(mapper.writeValueAsString(requestDTO)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void testSaveUserInfo_NullUsername() throws Exception {
		UserRequestDTO requestDTO = new UserRequestDTO();
		requestDTO.setUsername(null);

		mockMvc
				.perform(
						post("/usermanagement/save")
								.contentType(MediaType.APPLICATION_JSON)
								.content(mapper.writeValueAsString(requestDTO)))
				.andExpect(status().isBadRequest());
	}
}
