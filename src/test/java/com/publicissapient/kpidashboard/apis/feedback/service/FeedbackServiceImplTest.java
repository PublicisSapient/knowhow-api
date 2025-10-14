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

package com.publicissapient.kpidashboard.apis.feedback.service;

import static com.mongodb.assertions.Assertions.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.common.collect.Lists;
import com.publicissapient.kpidashboard.apis.auth.model.Authentication;
import com.publicissapient.kpidashboard.apis.common.service.CommonService;
import com.publicissapient.kpidashboard.apis.common.service.UserInfoService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.model.FeedbackSubmitDTO;
import com.publicissapient.kpidashboard.common.model.application.EmailServerDetail;
import com.publicissapient.kpidashboard.common.model.application.GlobalConfig;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;
import com.publicissapient.kpidashboard.common.repository.application.GlobalConfigRepository;
import com.publicissapient.kpidashboard.common.service.NotificationService;

/**
 * @author sanbhand1
 */
@RunWith(MockitoJUnitRunner.class)
public class FeedbackServiceImplTest {

	/*
	 * Creating a new test FeedbackSubmitDTO object
	 */
	FeedbackSubmitDTO feedbackSubmitDTO = new FeedbackSubmitDTO();
	@InjectMocks private FeedbackServiceImpl feedbackServiceImpl;
	@Mock private CustomApiConfig customApiConfig;
	@Mock private CommonService commonService;
	@Mock private UserInfoService userInfoService;
	@Mock private GlobalConfigRepository globalConfigRepository;
	@Mock private NotificationService notificationService;

	/** method includes preprocesses for test cases */
	@Before
	public void setUp() {

		feedbackSubmitDTO.setUsername("testuser");
		feedbackSubmitDTO.setFeedback("feedback");
	}

	/** to clean up the code */
	@After
	public void cleanup() {
		feedbackSubmitDTO = new FeedbackSubmitDTO();
	}

	/** Get method to Test for all the categories */
	@Test
	public void getFeedbackCategoriesTest() {
		when(customApiConfig.getFeedbackCategories()).thenReturn(new ArrayList<>());
		List<String> response = feedbackServiceImpl.getFeedBackCategories();
		// assertThat("status: ", response.getSuccess(), equalTo(true));
		assertThat("Data should exist but empty: ", response, equalTo(new ArrayList<>()));
	}

	/**
	 * method to Test submit Feedback Request
	 *
	 * @throws UnknownHostException
	 */
	@Test
	public void testSubmitFeedbackRequest() throws UnknownHostException {
		Authentication authentication = new Authentication();
		authentication.setEmail("abc@gmail.com");
		authentication.setUsername("testuser");
		List<String> emailAddresses = new ArrayList<>();
		List<GlobalConfig> globalConfigs = new ArrayList<>();
		emailAddresses.add("abc@gmail.com");
		GlobalConfig globalConfig = new GlobalConfig();
		EmailServerDetail emailServerDetail = new EmailServerDetail();
		emailServerDetail.setFeedbackEmailIds(emailAddresses);
		globalConfig.setEmailServerDetail(emailServerDetail);
		globalConfigs.add(globalConfig);
		Map<String, String> customData = new HashMap<>();
		customData.put("abc", "dfe");
		when(customApiConfig.getFeedbackEmailSubject()).thenReturn("TEST_EMAILS");
		when(commonService.getApiHost()).thenReturn("host");
		UserInfo userInfo = new UserInfo();
		userInfo.setUsername("user");
		userInfo.setId(new ObjectId("61e4f7852747353d4405c762"));
		userInfo.setAuthorities(Lists.newArrayList());
		userInfo.setEmailAddress("xyz@example.com");
		when(userInfoService.getUserInfo(anyString())).thenReturn(userInfo);
		when(globalConfigRepository.findAll()).thenReturn(globalConfigs);
		String loggedUserName = "testDummyUser";
		boolean response = feedbackServiceImpl.submitFeedback(feedbackSubmitDTO, loggedUserName);
		assertThat("status: ", response, equalTo(true));
	}

	@Test
	public void testSubmitFeedback_Success() throws UnknownHostException {
		// Arrange
		FeedbackSubmitDTO feedback = new FeedbackSubmitDTO();
		feedback.setFeedback("Great service!");
		String loggedUserName = "testUser";
		List<GlobalConfig> globalConfigs = Collections.singletonList(new GlobalConfig());
		EmailServerDetail emailServerDetail = new EmailServerDetail();
		emailServerDetail.setFeedbackEmailIds(Arrays.asList("feedback@example.com"));
		globalConfigs.get(0).setEmailServerDetail(emailServerDetail);

		when(globalConfigRepository.findAll()).thenReturn(globalConfigs);
		when(customApiConfig.getFeedbackEmailSubject()).thenReturn("Feedback Subject");
		when(commonService.getApiHost()).thenReturn("http://localhost");
		when(userInfoService.getUserInfo(loggedUserName)).thenReturn(new UserInfo());

		// Act
		boolean result = feedbackServiceImpl.submitFeedback(feedback, loggedUserName);

		// Assert
		assertTrue(result);
	}

	@Test
	public void testSubmitFeedback_NoEmailServerDetail() {
		// Arrange
		FeedbackSubmitDTO feedback = new FeedbackSubmitDTO();
		feedback.setFeedback("Great service!");
		String loggedUserName = "testUser";

		when(globalConfigRepository.findAll()).thenReturn(Collections.emptyList());

		// Act
		boolean result = feedbackServiceImpl.submitFeedback(feedback, loggedUserName);

		// Assert
		assertFalse(result);
		verify(notificationService, never())
				.sendNotificationEvent(anyList(), anyMap(), anyString(), anyBoolean(), anyString());
	}

	@Test
	public void testSubmitFeedback_EmptyFeedback() {
		// Arrange
		FeedbackSubmitDTO feedback = new FeedbackSubmitDTO();
		feedback.setFeedback("");
		String loggedUserName = "testUser";

		// Act
		boolean result = feedbackServiceImpl.submitFeedback(feedback, loggedUserName);

		// Assert
		assertFalse(result);
		verify(notificationService, never())
				.sendNotificationEvent(anyList(), anyMap(), anyString(), anyBoolean(), anyString());
	}

	@Test
	public void testSubmitFeedback_UnknownHostException() throws UnknownHostException {
		// Arrange
		FeedbackSubmitDTO feedback = new FeedbackSubmitDTO();
		feedback.setFeedback("Great service!");
		String loggedUserName = "testUser";
		List<GlobalConfig> globalConfigs = Collections.singletonList(new GlobalConfig());
		EmailServerDetail emailServerDetail = new EmailServerDetail();
		emailServerDetail.setFeedbackEmailIds(Arrays.asList("feedback@example.com"));
		globalConfigs.get(0).setEmailServerDetail(emailServerDetail);

		when(globalConfigRepository.findAll()).thenReturn(globalConfigs);
		when(customApiConfig.getFeedbackEmailSubject()).thenReturn("Feedback Subject");
		when(commonService.getApiHost()).thenThrow(new UnknownHostException());

		// Act
		boolean result = feedbackServiceImpl.submitFeedback(feedback, loggedUserName);

		// Assert
		assertFalse(result);
	}
}
