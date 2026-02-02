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

package com.publicissapient.kpidashboard.apis.auth.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.auth.AuthenticationUtil;
import com.publicissapient.kpidashboard.apis.auth.service.UserTokenDeletionService;
import com.publicissapient.kpidashboard.apis.auth.token.CookieUtil;
import com.publicissapient.kpidashboard.apis.common.service.UserInfoService;
import com.publicissapient.kpidashboard.apis.common.service.UsersSessionService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.constant.Status;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

/**
 * Rest controller to handle logout requests.
 *
 * @author anisingh4
 */
@Slf4j
@RestController
@Tag(
		name = "User Token Deletion Controller",
		description = "APIs for User Token Deletion Management")
public class UserTokenDeletionControllerApplication {
	@Autowired private CookieUtil cookieUtil;

	@Autowired private UserInfoService userInfoService;
	@Autowired private UsersSessionService usersSessionService;

	private final UserTokenDeletionService userTokenDeletionService;

	/**
	 * Instantiates a new User token deletion controller.
	 *
	 * @param userTokenDeletionService the user token deletion service
	 */
	@Autowired
	public UserTokenDeletionControllerApplication(UserTokenDeletionService userTokenDeletionService) {
		this.userTokenDeletionService = userTokenDeletionService;
	}

	/**
	 * Logout user from central service.
	 *
	 * @param request the request
	 */
	@Operation(
			summary = "Logout User from Central Authentication Service",
			description =
					"Logs out the user from the central authentication service "
							+ "by invalidating the session and deleting the authentication token.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "User logged out successfully from Central Authentication Service"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error during logout process")
			})
	@GetMapping(value = "/centralUserlogout", produces = APPLICATION_JSON_VALUE) // NOSONAR
	public ResponseEntity<ServiceResponse> deleteUserTokenForCentralAuth(
			HttpServletRequest request, HttpServletResponse response) {
		String userName = AuthenticationUtil.getUsernameFromContext();
		Cookie authCookie = cookieUtil.getAuthCookie(request);
		String authCookieToken = authCookie.getValue();
		authCookie.setMaxAge(0);
		HttpSession session;
		SecurityContextHolder.clearContext();
		session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		boolean cookieClear = userInfoService.getCentralAuthUserDeleteUserToken(authCookieToken);
		cookieUtil.deleteCookie(request, response, CookieUtil.AUTH_COOKIE);
		if (cookieClear) {
			usersSessionService.auditLogout(userName, Status.SUCCESS);
			return ResponseEntity.status(HttpStatus.OK)
					.body(new ServiceResponse(true, "Logout Successfully", true));
		} else {
			usersSessionService.auditLogout(userName, Status.FAIL);
			return ResponseEntity.status(HttpStatus.OK)
					.body(new ServiceResponse(false, "Error while Logout from Central Auth", false));
		}
	}

	/**
	 * Logout user from local auth.
	 *
	 * @param request the request
	 */
	@Operation(
			summary = "Logout User from Local Authentication Service",
			description =
					"Logs out the user from the local authentication service "
							+ "by invalidating the session and deleting the authentication token.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "User logged out successfully from Local Authentication Service"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error during logout process")
			})
	@GetMapping(value = "/userlogout", produces = APPLICATION_JSON_VALUE) // NOSONAR
	public ResponseEntity<ServiceResponse> deleteUserToken(
			HttpServletRequest request, HttpServletResponse response) {
		String userName = AuthenticationUtil.getUsernameFromContext();
		log.info("UserTokenDeletionController::deleteUserToken start");
		String token = StringUtils.removeStart(request.getHeader("Authorization"), "Bearer ");
		userTokenDeletionService.deleteUserDetails(token);
		ResponseCookie authCookie = cookieUtil.deleteAccessTokenCookie();
		log.info("UserTokenDeletionController::deleteUserToken end");
		cookieUtil.deleteCookie(request, response, CookieUtil.AUTH_COOKIE);
		if (Objects.nonNull(authCookie)) {
			usersSessionService.auditLogout(userName, Status.SUCCESS);
			return ResponseEntity.status(HttpStatus.OK)
					.body(new ServiceResponse(true, "local auth Logout Successfully", true));
		} else {
			usersSessionService.auditLogout(userName, Status.FAIL);
			return ResponseEntity.status(HttpStatus.OK)
					.body(new ServiceResponse(false, "Error while Logout from local auth", false));
		}
	}
}
