/*
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.abac;

import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.auth.model.Authentication;
import com.publicissapient.kpidashboard.apis.auth.repository.AuthenticationRepository;
import com.publicissapient.kpidashboard.apis.auth.service.AuthenticationService;
import com.publicissapient.kpidashboard.apis.auth.service.UserTokenDeletionService;
import com.publicissapient.kpidashboard.apis.auth.token.TokenAuthenticationService;
import com.publicissapient.kpidashboard.apis.autoapprove.service.AutoApproveAccessService;
import com.publicissapient.kpidashboard.apis.common.service.CommonService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.NotificationCustomDataEnum;
import com.publicissapient.kpidashboard.apis.hierarchy.service.OrganizationHierarchyService;
import com.publicissapient.kpidashboard.apis.projectconfig.basic.service.ProjectBasicConfigService;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.dto.HierarchyValueDTO;
import com.publicissapient.kpidashboard.common.model.application.dto.ProjectBasicConfigDTO;
import com.publicissapient.kpidashboard.common.model.rbac.AccessItem;
import com.publicissapient.kpidashboard.common.model.rbac.AccessNode;
import com.publicissapient.kpidashboard.common.model.rbac.AccessRequest;
import com.publicissapient.kpidashboard.common.model.rbac.AccessRequestDecision;
import com.publicissapient.kpidashboard.common.model.rbac.ProjectBasicConfigNode;
import com.publicissapient.kpidashboard.common.model.rbac.ProjectsAccess;
import com.publicissapient.kpidashboard.common.model.rbac.ProjectsForAccessRequest;
import com.publicissapient.kpidashboard.common.model.rbac.RoleData;
import com.publicissapient.kpidashboard.common.model.rbac.RoleWiseProjects;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.rbac.AccessRequestsRepository;
import com.publicissapient.kpidashboard.common.repository.rbac.RolesRepository;
import com.publicissapient.kpidashboard.common.repository.rbac.UserInfoCustomRepository;
import com.publicissapient.kpidashboard.common.repository.rbac.UserInfoRepository;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelService;
import com.publicissapient.kpidashboard.common.service.NotificationService;

import lombok.extern.slf4j.Slf4j;

/**
 * @author anisingh4
 */
@Service
@Slf4j
public class ProjectAccessManager {

	private static final CharSequence STRING_LIST_JOINER = ", ";
	private static final String NOTIFICATION_SUBJECT_KEY = "accessRequest";

	private static final String NOTIFICATION_KEY = "Access_Request";

	@Autowired
	private UserInfoRepository userInfoRepository;

	@Autowired
	private AccessRequestsRepository accessRequestsRepository;

	@Autowired
	private ProjectBasicConfigRepository projectBasicConfigRepository;

	@Autowired
	private UserInfoCustomRepository userInfoCustomRepository;

	@Autowired
	private ProjectBasicConfigService projectBasicConfigService;

	@Autowired
	private UserTokenDeletionService userTokenDeletionService;

	@Autowired
	private AutoApproveAccessService autoApproveService;

	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private CommonService commonService;

	@Autowired
	private CustomApiConfig customApiConfig;

	@Autowired
	private RolesRepository rolesRepository;

	@Autowired
	private AuthenticationRepository authenticationRepository;

	@Autowired
	private HierarchyLevelService hierarchyLevelService;

	@Autowired
	private TokenAuthenticationService tokenAuthenticationService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private KafkaTemplate<String, Object> kafkaTemplate;

	@Autowired
	private OrganizationHierarchyService organizationHierarchyService;

	private static String findRoleOfAccessItem(String requestedAccessLavel, AccessItem requestedAccessItem,
			List<ProjectsAccess> projectsAccesses) {

		String role = null;
		for (ProjectsAccess pa : projectsAccesses) {
			AccessItem searchedAccessItem = pa.getAccessNodes().stream()
					.filter(accessNode -> accessNode.getAccessLevel().equals(requestedAccessLavel))
					.flatMap(accessNode -> accessNode.getAccessItems().stream())
					.filter(accessItem -> accessItem.equals(requestedAccessItem)).findFirst().orElse(null);

			if (searchedAccessItem != null) {
				role = pa.getRole();
				break;
			}
		}
		return role;
	}

	// get current role and access - input user
	public UserInfo getUserInfo(String username) {
		return userInfoRepository.findByUsername(username);
	}

	/**
	 * This method check access Request
	 *
	 * @param accessRequest
	 *            accessRequest
	 * @return boolean value
	 */
	public boolean handleAccessRequest(AccessRequest accessRequest) {
		UserInfo userInfo = getUserInfo(accessRequest.getUsername());

		accessRequest.setStatus(Constant.ACCESS_REQUEST_STATUS_PENDING);

		String accessLevel = accessRequest.getAccessNode().getAccessLevel();
		Set<String> requestIds = accessRequest.getAccessNode().getAccessItems().stream().map(AccessItem::getItemId)
				.collect(Collectors.toSet());

		ProjectBasicConfigNode projectBasicConfigNode = projectBasicConfigService.getBasicConfigTree();

		Map<String, Set<String>> globalParentMap = new HashMap<>();
		Map<String, Set<String>> existingAccessMap = new HashMap<>();

		// creating userInfoMap
		if (userInfo != null) {
			creatingExistingAccessesMap(userInfo.getProjectsAccess(), existingAccessMap);
		}

		// creating parentMap
		creatingGlobalParentMap(accessLevel, requestIds, projectBasicConfigNode, globalParentMap);

		// check if user has already a pending request

		boolean isOk = false;
		if (isNewUser(userInfo)) {
			isOk = true;
		} else if (hasAccessToParentLevel(globalParentMap, existingAccessMap)) {
			isOk = false;
		} else {
			isOk = true;
		}
		return isOk;
	}

	/**
	 * This method creates access request
	 *
	 * @param accessRequest
	 *            accessRequest
	 * @param listener
	 *            listener
	 */
	public void createAccessRequest(AccessRequest accessRequest, AccessRequestListener listener) {

		if (handelSuperAdminProjectLevelAccessRequest(accessRequest)) {
			listenAccessRequestFailure(listener,
					"SuperAdmin Role has all levels of access, you cannot request for any hierarchy or project level");
			return;
		}

		// Get all access requests for the user
		List<AccessRequest> allRequests = accessRequestsRepository.findByUsername(accessRequest.getUsername());

		List<AccessRequest> approvedRequests = allRequests.stream()
				.filter(req -> Constant.ACCESS_REQUEST_STATUS_APPROVED.equals(req.getStatus()))
				.collect(Collectors.toList());
		List<AccessRequest> pendingRequests = allRequests.stream()
				.filter(req -> Constant.ACCESS_REQUEST_STATUS_PENDING.equals(req.getStatus()))
				.collect(Collectors.toList());

		// Check if there's a pending request for any other access
		boolean hasPendingRequestForDifferentAccess = pendingRequests.stream()
				.anyMatch(pr -> pr.getAccessNode().getAccessItems().stream().map(AccessItem::getItemId)
						.noneMatch(id -> accessRequest.getAccessNode().getAccessItems().stream()
								.map(AccessItem::getItemId).anyMatch(reqId -> reqId.equals(id))));

		if (hasPendingRequestForDifferentAccess) {
			listenAccessRequestFailure(listener, "Already has a pending request for different access");
			return;
		}

		// Check for existing pending request for same project
		List<AccessRequest> existingPendingRequest = pendingRequests.stream()
				.filter(pr -> pr.getAccessNode().getAccessItems().stream().map(AccessItem::getItemId)
						.anyMatch(id -> accessRequest.getAccessNode().getAccessItems().stream()
								.map(AccessItem::getItemId).anyMatch(reqId -> reqId.equals(id))))
				.collect(Collectors.toList());

		// If there's a pending request for same project with different role, update it
		if (CollectionUtils.isNotEmpty(existingPendingRequest)) {
			for (AccessRequest pendingRequest : existingPendingRequest) {
				if (!pendingRequest.getRole().equals(accessRequest.getRole())) {
					pendingRequest.setRole(accessRequest.getRole());
					pendingRequest.setLastModifiedDate(new Date());
					accessRequestsRepository.save(pendingRequest);
					listenAccessRequestSuccess(listener, pendingRequest);
				} else {
					listenAccessRequestFailure(listener, "Already has a pending request with same role");
				}
			}
			return;
		}

		// Check if user has approved access for same project but requesting different
		// role
		Optional<AccessRequest> existingApprovedRequest = approvedRequests.stream().filter(ar -> ar.getAccessNode()
				.getAccessItems().stream().map(AccessItem::getItemId).anyMatch(id -> accessRequest.getAccessNode()
						.getAccessItems().stream().map(AccessItem::getItemId).anyMatch(reqId -> reqId.equals(id))))
				.findFirst();

		if (existingApprovedRequest.isPresent()
				&& !existingApprovedRequest.get().getRole().equals(accessRequest.getRole())) {
			// Create new pending request for different role
			accessRequest.setStatus(Constant.ACCESS_REQUEST_STATUS_PENDING);
			accessRequest.setLastModifiedDate(new Date());
			AccessRequest savedRequest = accessRequestsRepository.save(accessRequest);
			listenAccessRequestSuccess(listener, savedRequest);
			return;
		}

		if (!handleAccessRequest(accessRequest)) {
			listenAccessRequestFailure(listener, "Already has access to parent level");
			return;
		}

		UserInfo userInfo = getUserInfo(accessRequest.getUsername());

		// Check if user already has access with same role
		if (hasAccess(userInfo, accessRequest) && userInfo.getProjectsAccess().stream()
				.anyMatch(pa -> pa.getRole().equals(accessRequest.getRole()) && pa.getAccessNodes().stream()
						.flatMap(an -> an.getAccessItems().stream()).map(AccessItem::getItemId)
						.anyMatch(id -> accessRequest.getAccessNode().getAccessItems().stream()
								.map(AccessItem::getItemId).anyMatch(reqId -> reqId.equals(id))))) {
			listenAccessRequestFailure(listener, "Already has access with requested role");
			return;
		}

		// Filter out already approved access items before proceeding
		AccessRequest newAccessRequest = filterAlreadyApprovedAccessRequest(accessRequest, approvedRequests);

		List<AccessRequest> requestList = getRequestList(newAccessRequest);
		requestList = accessRequestsRepository.saveAll(requestList);
		requestList.forEach(this::autoApproveOrNotify);
		setRequestStatus(requestList, newAccessRequest);
		listenAccessRequestSuccess(listener, newAccessRequest);
	}

	private static boolean hasAccess(UserInfo userInfo, AccessRequest accessRequest) {
		if (userInfo == null || accessRequest == null || userInfo.getProjectsAccess() == null) {
			return false;
		}

		List<String> requestedItemIds = accessRequest.getAccessNode().getAccessItems().stream()
				.map(AccessItem::getItemId).collect(Collectors.toList());

		return userInfo.getProjectsAccess().stream().flatMap(projectAccess -> projectAccess.getAccessNodes().stream())
				.flatMap(accessNode -> accessNode.getAccessItems().stream()).map(AccessItem::getItemId)
				.anyMatch(requestedItemIds::contains);
	}

	private AccessRequest filterAlreadyApprovedAccessRequest(AccessRequest accessRequest,
			List<AccessRequest> approvedRequests) {
		List<AccessItem> filteredAccessItems = accessRequest.getAccessNode().getAccessItems().stream()
				.filter(accessItem -> !checkIfAlreadyHasAccess(accessItem, approvedRequests))
				.collect(Collectors.toList());

		accessRequest.getAccessNode().setAccessItems(filteredAccessItems);
		return accessRequest;
	}

	private boolean checkIfAlreadyHasAccess(AccessItem accessItem, List<AccessRequest> approvedRequests) {
		return (CollectionUtils.isNotEmpty(approvedRequests) && approvedRequests.stream()
				.anyMatch(approvedRequest -> approvedRequest.getAccessNode().getAccessItems().contains(accessItem)));
	}

	/**
	 * ROLE_SUPERADMIN have all level access but any user request for with role is
	 * superAdmin and access request of any particular level or list of projects
	 * then denied request
	 *
	 * @param accessRequest
	 * @return
	 */
	private boolean handelSuperAdminProjectLevelAccessRequest(AccessRequest accessRequest) {
		String accessLevel = accessRequest.getAccessNode().getAccessLevel();
		Set<String> requestIds = accessRequest.getAccessNode().getAccessItems().stream().map(AccessItem::getItemId)
				.collect(Collectors.toSet());
		return accessRequest.getRole().equals(Constant.ROLE_SUPERADMIN)
				&& (StringUtils.isNotEmpty(accessLevel) || CollectionUtils.isNotEmpty(requestIds));
	}

	/**
	 * Set status in case of auto approval.
	 *
	 * @param requests
	 *            requests
	 * @param accessRequest
	 *            accessRequest
	 */
	private void setRequestStatus(List<AccessRequest> requests, AccessRequest accessRequest) {
		if (CollectionUtils.isNotEmpty(requests) && null != accessRequest) {
			accessRequest.setStatus(requests.get(0).getStatus());
		}
	}

	/**
	 * This method get request list and create seperate request if access level is
	 * project
	 *
	 * @param accessRequest
	 *            accessRequest
	 * @return list of access request
	 */
	private List<AccessRequest> getRequestList(AccessRequest accessRequest) {
		List<AccessRequest> list = new ArrayList<>();
		if (accessRequest.getAccessNode().getAccessLevel()
				.equalsIgnoreCase(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT)) {
			list.addAll(getSeperateAccessRequest(accessRequest));
		} else {
			list.add(accessRequest);
		}
		return list;
	}

	/**
	 * This method create seperate request if accessLevel is project
	 *
	 * @param accessRequest
	 *            accessRequest
	 * @return list of access Request
	 */
	private List<AccessRequest> getSeperateAccessRequest(AccessRequest accessRequest) {
		List<AccessRequest> requests = new ArrayList<>();
		accessRequest.getAccessNode().getAccessItems().forEach(item -> {
			AccessItem newItem = new AccessItem();
			newItem.setItemId(item.getItemId());
			List<AccessItem> itemList = new ArrayList<>();
			itemList.add(newItem);
			AccessRequest request = createAccessRequest(accessRequest);
			request.getAccessNode().setAccessItems(itemList);
			requests.add(request);
		});
		return requests;
	}

	/**
	 * create access request
	 *
	 * @param accessRequest
	 *            accessRequest
	 * @return new access Request object
	 */
	private AccessRequest createAccessRequest(AccessRequest accessRequest) {
		AccessRequest newAccessRequest = new AccessRequest();
		newAccessRequest.setUsername(accessRequest.getUsername());
		newAccessRequest.setStatus(accessRequest.getStatus());
		newAccessRequest.setReviewComments(accessRequest.getReviewComments());
		newAccessRequest.setRole(accessRequest.getRole());
		AccessNode accessNode = new AccessNode();
		accessNode.setAccessLevel(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);
		accessNode.setAccessItems(new ArrayList<>());
		newAccessRequest.setAccessNode(accessNode);
		return newAccessRequest;
	}

	private void autoApproveOrNotify(AccessRequest savedAccessRequest) {
		grantAccessWhenAutoApproveEnabled(savedAccessRequest);
		sendAccessRequestEmailToAdmin(savedAccessRequest);
	}

	private void sendAccessRequestEmailToAdmin(AccessRequest accessRequest) {
		List<String> emailAddresses = commonService
				.getEmailAddressBasedOnRoles(Arrays.asList(Constant.ROLE_SUPERADMIN));

		if (accessRequest.getAccessNode().getAccessLevel().equals(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT)) {
			emailAddresses.addAll(commonService.getProjectAdminEmailAddressBasedProjectId(
					accessRequest.getAccessNode().getAccessItems().get(0).getItemId()));
		}

		Map<String, String> notificationSubjects = customApiConfig.getNotificationSubject();
		if (CollectionUtils.isNotEmpty(emailAddresses) && MapUtils.isNotEmpty(notificationSubjects)) {
			String serverPath = "";
			try {
				serverPath = commonService.getApiHost();
			} catch (UnknownHostException e) {
				log.error("AccessRequestController: Server Host name is not bind with Access Request mail ");
			}
			Map<String, String> customData = createCustomData(accessRequest, serverPath);
			String subject = notificationSubjects.get(NOTIFICATION_SUBJECT_KEY);
			log.info("Notification message sent to kafka with key : {}", NOTIFICATION_KEY);
			String templateKey = customApiConfig.getMailTemplate().getOrDefault(NOTIFICATION_KEY, "");
			notificationService.sendNotificationEvent(emailAddresses, customData, subject, NOTIFICATION_KEY,
					customApiConfig.getKafkaMailTopic(), customApiConfig.isNotificationSwitch(), kafkaTemplate,
					templateKey, customApiConfig.isMailWithoutKafka());
		} else {
			log.error("Notification Event not sent : No email address found associated with Superadmin role "
					+ "or Property - notificationSubject.accessRequest not set in property file ");
		}
	}

	private Map<String, String> createCustomData(AccessRequest accessRequestsData, String serverPath) {
		Map<String, String> customData = new HashMap<>();
		String email = getEmailAddress(accessRequestsData);
		String accessLevel = "";
		String accessItemsAsString = "";

		AccessNode accessNode = accessRequestsData.getAccessNode();
		if (accessNode != null) {
			accessLevel = accessNode.getAccessLevel();
			List<AccessItem> accessItems = accessNode.getAccessItems();
			Map<String, String> organizationHierarchyMap = createOrganizationHierarchyMap();

			accessItemsAsString = accessItems.stream()
					.map(accessItem -> organizationHierarchyMap.get(accessItem.getItemId()))
					.collect(Collectors.joining(STRING_LIST_JOINER));
		}
		RoleData roleData = rolesRepository.findByRoleName(accessRequestsData.getRole());

		customData.put(NotificationCustomDataEnum.USER_NAME.getValue(), accessRequestsData.getUsername());
		customData.put(NotificationCustomDataEnum.USER_EMAIL.getValue(), email);
		customData.put(NotificationCustomDataEnum.USER_EMAIL.getValue(), email);
		customData.put(NotificationCustomDataEnum.ACCESS_LEVEL.getValue(), accessLevel);
		customData.put(NotificationCustomDataEnum.ACCESS_ITEMS.getValue(), accessItemsAsString);
		customData.put(NotificationCustomDataEnum.USER_ROLES.getValue(), roleData.getDisplayName());
		customData.put(NotificationCustomDataEnum.SERVER_HOST.getValue(), serverPath);
		return customData;
	}

	/**
	 * getEmailAddress for email data
	 *
	 * @param accessRequestsData
	 *            accessRequestsData
	 * @return mail
	 */
	private String getEmailAddress(AccessRequest accessRequestsData) {
		String email = "";
		email = getUserInfo(accessRequestsData.getUsername()).getEmailAddress().toLowerCase();
		if (StringUtils.isEmpty(email)) {
			Authentication authentication = authenticationRepository.findByUsername(accessRequestsData.getUsername());
			if (null == authentication) {
				log.error("User {} Does not Exist in Authentication Collection", accessRequestsData.getUsername());
			} else {
				email = authentication.getEmail().toLowerCase();
			}
		}
		return email;
	}

	private void grantAccessWhenAutoApproveEnabled(AccessRequest accessRequest) {
		if (accessRequest != null && autoApproveService.isAutoApproveEnabled(accessRequest.getRole())) {
			AccessRequestDecision accessRequestDecision = new AccessRequestDecision();
			accessRequestDecision.setRole(accessRequest.getRole());
			accessRequestDecision.setStatus(Constant.ACCESS_REQUEST_STATUS_APPROVED);

			grantAccess(accessRequest.getId().toHexString(), accessRequestDecision, new GrantAccessListener() {
				@Override
				public void onSuccess(UserInfo userInfo) {
					accessRequest.setStatus(Constant.ACCESS_REQUEST_STATUS_APPROVED);
				}

				@Override
				public void onFailure(AccessRequest accessRequest, String message) {
					// Do nothing
				}
			});
		}
	}

	public void grantAccess(String accessRequestId, AccessRequestDecision accessRequestDecision,
			GrantAccessListener grantAccessListener) {
		AccessRequest accessRequest = getAccessRequest(accessRequestId);

		if (StringUtils.isNotEmpty(accessRequestDecision.getRole())) {
			accessRequest.setRole(accessRequestDecision.getRole());
		}

		// Fetch and delete any existing approved requests for the same project
		List<AccessRequest> existingApprovedRequests = accessRequestsRepository
				.findByUsernameAndStatus(accessRequest.getUsername(), Constant.ACCESS_REQUEST_STATUS_APPROVED);

		if (CollectionUtils.isNotEmpty(existingApprovedRequests)) {
			existingApprovedRequests.stream()
					.filter(ar -> ar.getAccessNode() != null
							&& CollectionUtils.isNotEmpty(ar.getAccessNode().getAccessItems())
							&& ar.getAccessNode().getAccessItems().stream().map(AccessItem::getItemId)
									.anyMatch(id -> accessRequest.getAccessNode().getAccessItems().stream()
											.map(AccessItem::getItemId).anyMatch(reqId -> reqId.equals(id))))
					.forEach(ar -> accessRequestsRepository.delete(ar));
		}

		UserInfo existingUserInfo = getUserInfo(accessRequest.getUsername());
		UserInfo resultUserInfo = copyUserInfo(existingUserInfo);

		if (accessRequest.getRole().equals(Constant.ROLE_SUPERADMIN)) {
			if (resultUserInfo != null) {
				resultUserInfo.setAuthorities(new ArrayList<>(Arrays.asList(Constant.ROLE_SUPERADMIN)));
				resultUserInfo.setProjectsAccess(new ArrayList<>());
			}
		} else {
			// create a tree from basic_config with root node
			ProjectBasicConfigNode projectBasicConfigNode = projectBasicConfigService.getBasicConfigTree();

			String accessLevel = accessRequest.getAccessNode().getAccessLevel();
			String accessRole = accessRequest.getRole();

			Map<String, Set<String>> globalChildrenMap = new HashMap<>();
			Map<String, Set<String>> existingAccessMap = new HashMap<>();

			// creating userInfoMap
			if (existingUserInfo != null) {
				creatingExistingAccessesMap(existingUserInfo.getProjectsAccess(), existingAccessMap);
			}

			// creating global children map
			createGlobalChildrenMap(accessLevel, accessRequest.getAccessNode().getAccessItems(), projectBasicConfigNode,
					globalChildrenMap);
			boolean isUserExists = existingUserInfo != null && existingUserInfo.getAuthorities().size() == 1
					&& existingUserInfo.getAuthorities().contains(Constant.ROLE_VIEWER)
					&& existingUserInfo.getProjectsAccess().isEmpty();

			if (isUserExists) {
				updateAuthorities(resultUserInfo, accessRequest.getRole());
				setFirstProjectsAccess(resultUserInfo, accessRequest.getRole(), accessRequest.getAccessNode());

			} else {
				modifyUserInfoForAccess(accessRequest, existingUserInfo, resultUserInfo, accessLevel, accessRole,
						globalChildrenMap);
				cleanUserInfo(resultUserInfo);
			}
		}

		saveUserInfo(resultUserInfo);
		updateAccessRequestStatus(accessRequest, Constant.ACCESS_REQUEST_STATUS_APPROVED, null);
		listenGrantAccessSuccess(grantAccessListener, resultUserInfo);
	}

	public void rejectAccessRequest(String accessRequestId, String message, RejectAccessListener listener) {
		AccessRequest accessRequest = getAccessRequest(accessRequestId);
		AccessRequest updatedAccessRequest = updateAccessRequestStatus(accessRequest,
				Constant.ACCESS_REQUEST_STATUS_REJECTED, message);
		if (updatedAccessRequest.getStatus().equalsIgnoreCase(Constant.ACCESS_REQUEST_STATUS_REJECTED)) {
			if (listener != null) {
				listener.onSuccess(updatedAccessRequest);
			}
		} else {
			if (listener != null) {
				listener.onFailure(accessRequest, "Failed to reject the request");
			}
		}
	}

	private AccessRequest updateAccessRequestStatus(AccessRequest accessRequest, String status, String msg) {
		accessRequest.setStatus(status);
		if (StringUtils.isNotEmpty(msg)) {
			accessRequest.setReviewComments(msg);
		}
		accessRequest.setLastModifiedDate(new Date());
		return accessRequestsRepository.save(accessRequest);
	}

	private void modifyUserInfoForAccess(AccessRequest accessRequest, UserInfo existingUserInfo,
			UserInfo resultUserInfo, String accessLevel, String accessRole,
			Map<String, Set<String>> globalChildrenMap) {
		Map<String, String> organizationHierarchyMap = createOrganizationHierarchyMap();

		accessRequest.getAccessNode().getAccessItems().forEach(item -> {
			String existingRoleForItem = findRoleOfAccessItem(accessLevel, item, existingUserInfo.getProjectsAccess());

			if (existingRoleForItem != null) {
				if (accessRole.equals(existingRoleForItem)) {
					log.info("already has same access for {}", organizationHierarchyMap.get(item.getItemId()));
					// do nothing
				} else {
					// remove item from old role and add to new role
					moveItemIntoNewRole(accessLevel, item, existingRoleForItem, accessRole, resultUserInfo);
				}
			} else {
				removeChildren(globalChildrenMap, resultUserInfo);
				addAccessItemToProjectAccess(accessLevel, item, accessRole, resultUserInfo);
			}
		});
	}

	private Map<String, String> createOrganizationHierarchyMap() {
		List<OrganizationHierarchy> organizationHierarchyList = organizationHierarchyService.findAll();
		if (CollectionUtils.isEmpty(organizationHierarchyList)) {
			log.error("No organization hierarchy found");
		}
		return organizationHierarchyList.stream().collect(Collectors.toMap(OrganizationHierarchy::getNodeId,
				OrganizationHierarchy::getNodeDisplayName, (e1, e2) -> e1));
	}

	private void cleanUserInfo(UserInfo userInfo) {

		if (userInfo != null) {
			// remove accessNode if no accessItem
			userInfo.getProjectsAccess().forEach(projectsAccess -> projectsAccess.getAccessNodes()
					.removeIf(accessNode -> CollectionUtils.isEmpty(accessNode.getAccessItems())));
			// remove role if no accessNodes
			userInfo.getProjectsAccess()
					.removeIf(projectsAccess -> CollectionUtils.isEmpty(projectsAccess.getAccessNodes()));
			// update authorities
			List<String> roles = userInfo.getProjectsAccess().stream().map(ProjectsAccess::getRole)
					.collect(Collectors.toList());

			if (CollectionUtils.isEmpty(roles)) {
				roles.add(Constant.ROLE_VIEWER);
				userInfo.setAuthorities(roles);
			} else if (roles.contains(Constant.ROLE_GUEST)) {
				userInfo.getProjectsAccess()
						.removeIf(projectsAccess -> !projectsAccess.getRole().equals(Constant.ROLE_GUEST));
				userInfo.setAuthorities(new ArrayList<>(Arrays.asList(Constant.ROLE_GUEST)));
			} else {
				userInfo.setAuthorities(roles);
			}
		}
	}

	private void removeChildren(Map<String, Set<String>> globalChildrenMap, UserInfo resultUserInfo) {

		resultUserInfo.getProjectsAccess().stream().flatMap(projectsAccess -> projectsAccess.getAccessNodes().stream())
				.forEach(accessNode -> accessNode.getAccessItems()
						.removeIf(accessItem -> isChildOf(accessNode.getAccessLevel(), accessItem, globalChildrenMap)));
	}

	private boolean isChildOf(String accessLevel, AccessItem accessItem, Map<String, Set<String>> globalChildrenMap) {

		Set<String> childrenIds = globalChildrenMap.get(accessLevel.toUpperCase());
		return childrenIds != null
				&& childrenIds.stream().anyMatch(childId -> childId.equalsIgnoreCase(accessItem.getItemId()));
	}

	private void moveItemIntoNewRole(String accessLevel, AccessItem targetAccessItem, String existingRoleForItem,
			String requestedAccessRole, UserInfo resultUserInfo) {
		removeAccessItemFromProjectAccess(accessLevel, targetAccessItem, existingRoleForItem, resultUserInfo);
		addAccessItemToProjectAccess(accessLevel, targetAccessItem, requestedAccessRole, resultUserInfo);
	}

	private void addAccessItemToProjectAccess(String accessLevel, AccessItem targetAccessItem,
			String requestedAccessRole, UserInfo resultUserInfo) {
		if (resultUserInfo.getProjectsAccess().stream()
				.noneMatch(projectsAccess -> projectsAccess.getRole().equals(requestedAccessRole))) {
			ProjectsAccess pa = createNewProjectsAccess(accessLevel, targetAccessItem, requestedAccessRole);
			resultUserInfo.getProjectsAccess().add(pa);

		} else {
			resultUserInfo.getProjectsAccess().stream()
					.filter(projectsAccess -> projectsAccess.getRole().equals(requestedAccessRole))
					.forEach(projectsAccess -> addAccessNode(projectsAccess.getAccessNodes(), accessLevel,
							targetAccessItem));
		}
	}

	private void removeAccessItemFromProjectAccess(String accessLevel, AccessItem targetAccessItem,
			String existingRoleForItem, UserInfo resultUserInfo) {
		resultUserInfo.getProjectsAccess().stream()
				.filter(projectsAccess -> projectsAccess.getRole().equals(existingRoleForItem))
				.forEach(projectsAccess -> projectsAccess.getAccessNodes().stream()
						.filter(accessNode -> accessNode.getAccessLevel().equals(accessLevel))
						.forEach(accessNode -> accessNode.getAccessItems()
								.removeIf(item -> item.equals(targetAccessItem))));
	}

	private void addAccessNode(List<AccessNode> accessNodes, String accessLevel, AccessItem accessItem) {
		if (accessNodes.stream().noneMatch(accessNode -> accessNode.getAccessLevel().equals(accessLevel))) {
			AccessNode newAccessNode = new AccessNode();
			newAccessNode.setAccessLevel(accessLevel);
			newAccessNode.setAccessItems(new ArrayList<>(Arrays.asList(accessItem)));
			accessNodes.add(newAccessNode);
		} else {
			accessNodes.stream().filter(accessNode -> accessNode.getAccessLevel().equals(accessLevel)).findFirst()
					.ifPresent(accessNode -> accessNode.getAccessItems().add(accessItem));
		}
	}

	@NotNull
	private ProjectsAccess createNewProjectsAccess(String accessLevel, AccessItem targetAccessItem,
			String requestedAccessRole) {
		ProjectsAccess pa = new ProjectsAccess();
		pa.setRole(requestedAccessRole);
		AccessNode an = new AccessNode();
		an.setAccessLevel(accessLevel);
		an.setAccessItems(new ArrayList<>(Arrays.asList(targetAccessItem)));
		pa.setAccessNodes(new ArrayList<>(Arrays.asList(an)));
		return pa;
	}

	private void setFirstProjectsAccess(UserInfo resultUserInfo, String role, AccessNode accessNode) {
		ProjectsAccess projectsAccess = new ProjectsAccess();
		projectsAccess.setRole(role);
		projectsAccess.setAccessNodes(new ArrayList<>(Arrays.asList(accessNode)));
		resultUserInfo.setProjectsAccess(new ArrayList<>(Arrays.asList(projectsAccess)));
	}

	private void updateAuthorities(UserInfo userInfo, String role) {
		List<String> authorities = userInfo.getAuthorities();
		if (!authorities.contains(role)) {
			authorities.add(role);
		}
		userInfo.setAuthorities(authorities);
	}

	private UserInfo saveUserInfo(UserInfo userInfo) {
		return userInfoRepository.save(userInfo);
	}

	private UserInfo copyUserInfo(UserInfo userInfo) {
		UserInfo copyOfUserInfo = null;
		if (userInfo != null) {
			copyOfUserInfo = new UserInfo();
			copyOfUserInfo.setId(userInfo.getId());
			copyOfUserInfo.setUsername(userInfo.getUsername());
			copyOfUserInfo.setAuthorities(userInfo.getAuthorities());
			copyOfUserInfo.setAuthType(userInfo.getAuthType());
			copyOfUserInfo.setFirstName(userInfo.getFirstName());
			copyOfUserInfo.setMiddleName(userInfo.getMiddleName());
			copyOfUserInfo.setLastName(userInfo.getLastName());
			copyOfUserInfo.setEmailAddress(userInfo.getEmailAddress());
			copyOfUserInfo.setProjectsAccess(userInfo.getProjectsAccess());
			copyOfUserInfo.setNotificationEmail(userInfo.getNotificationEmail());
		}
		return copyOfUserInfo;
	}

	private void creatingExistingAccessesMap(List<ProjectsAccess> projectsAccess,
			Map<String, Set<String>> userInfoMap) {
		projectsAccess.forEach(pa -> {
			Map<String, List<AccessNode>> accessNodeMap = pa.getAccessNodes().stream()
					.collect(Collectors.groupingBy(AccessNode::getAccessLevel));
			accessNodeMap.forEach((k, v) -> {
				Set<String> aid = null;
				for (AccessNode an : v) {
					aid = an.getAccessItems().stream().map(AccessItem::getItemId).collect(Collectors.toSet());
				}
				userInfoMap.put(k, aid);
			});
		});
	}

	private void creatingGlobalParentMap(String accessLevel, Set<String> requestIds,
			ProjectBasicConfigNode projectBasicConfigNode, Map<String, Set<String>> globalParentMap) {
		requestIds.forEach(reqId -> {
			ProjectBasicConfigNode searchNode = projectBasicConfigService.findNode(projectBasicConfigNode, reqId,
					accessLevel);
			List<ProjectBasicConfigNode> parents = new ArrayList<>();
			projectBasicConfigService.findParents(Arrays.asList(searchNode), parents);

			Map<String, List<ProjectBasicConfigNode>> parentMap = parents.stream()
					.collect(Collectors.groupingBy(ProjectBasicConfigNode::getGroupName));
			parentMap.forEach((k, v) -> {
				Set<String> items = globalParentMap.get(k);
				if (CollectionUtils.isEmpty(items)) {
					globalParentMap.put(k,
							v.stream().map(ProjectBasicConfigNode::getValue).collect(Collectors.toSet()));
				} else {
					items.addAll(v.stream().map(ProjectBasicConfigNode::getValue).collect(Collectors.toSet()));
					globalParentMap.put(k, items);
				}
			});
		});
	}

	private void createGlobalChildrenMap(String accessLevel, List<AccessItem> accessItems,
			ProjectBasicConfigNode projectBasicConfigNode, Map<String, Set<String>> globalChildrenMap) {

		accessItems.forEach(accessItem -> {
			ProjectBasicConfigNode searchNode = projectBasicConfigService.findNode(projectBasicConfigNode,
					accessItem.getItemId(), accessLevel);
			List<ProjectBasicConfigNode> children = new ArrayList<>();
			projectBasicConfigService.findChildren(searchNode, children);

			Map<String, List<ProjectBasicConfigNode>> accessLevelWiseChildren = children.stream()
					.collect(Collectors.groupingBy(ProjectBasicConfigNode::getGroupName));
			accessLevelWiseChildren.forEach((k, v) -> {
				Set<String> items = globalChildrenMap.get(k);
				if (CollectionUtils.isEmpty(items)) {
					globalChildrenMap.put(k,
							v.stream().map(ProjectBasicConfigNode::getValue).collect(Collectors.toSet()));
				} else {
					items.addAll(v.stream().map(ProjectBasicConfigNode::getValue).collect(Collectors.toSet()));
					globalChildrenMap.put(k, items);
				}
			});
		});
	}

	private boolean hasAccessToParentLevel(Map<String, Set<String>> globalParentMap,
			Map<String, Set<String>> userInfoMap) {
		Optional<Entry<String, Set<String>>> accesslevelWiseMap = globalParentMap.entrySet().stream()
				.filter(e -> userInfoMap.entrySet().stream().anyMatch(e1 -> e1.getKey().equalsIgnoreCase(e.getKey())))
				.findAny();
		if (accesslevelWiseMap.isPresent()) {
			return userInfoMap.get(accesslevelWiseMap.get().getKey()).stream()
					.anyMatch(elem -> accesslevelWiseMap.get().getValue().contains(elem));
		}
		return false;
	}

	private void listenAccessRequestFailure(AccessRequestListener listener, String msg) {
		if (listener != null) {
			listener.onFailure(msg);
		}
	}

	private void listenAccessRequestSuccess(AccessRequestListener listener, AccessRequest savedAccessRequest) {
		if (listener != null) {
			listener.onSuccess(savedAccessRequest);
		}
	}

	private void listenGrantAccessSuccess(GrantAccessListener listener, UserInfo userInfo) {
		if (listener != null) {
			tokenAuthenticationService.updateExpiryDate(userInfo.getUsername(), LocalDateTime.now().toString());
			listener.onSuccess(userInfo);
		}
	}

	private boolean isNewUser(UserInfo userInfo) {
		if (userInfo == null) {
			return false;
		}
		List<String> authorities = userInfo.getAuthorities();
		return authorities.size() == 1 && authorities.contains(Constant.ROLE_VIEWER)
				&& CollectionUtils.isEmpty(userInfo.getProjectsAccess());
	}

	public List<RoleWiseProjects> getProjectAccessesWithRole(String username) {

		UserInfo userInfo = getUserInfo(username);
		List<RoleWiseProjects> result = new ArrayList<>();
		if (Objects.nonNull(userInfo)) {
			List<ProjectsAccess> projectsAccesses = userInfo.getProjectsAccess();

			if (CollectionUtils.isNotEmpty(projectsAccesses)) {
				projectsAccesses.forEach(projectsAccess -> {
					RoleWiseProjects roleWiseProjects = new RoleWiseProjects();
					roleWiseProjects.setRole(projectsAccess.getRole());
					roleWiseProjects.setProjects(getProjects(projectsAccess.getAccessNodes()));
					result.add(roleWiseProjects);
				});
			}
		}

		return result;
	}

	private List<ProjectsForAccessRequest> getProjects(List<AccessNode> accessNodes) {
		List<ProjectsForAccessRequest> projects = new ArrayList<>();

		if (CollectionUtils.isNotEmpty(accessNodes)) {
			List<ProjectBasicConfig> projectBasicConfigs = new ArrayList<>();
			for (AccessNode node : accessNodes) {
				List<ProjectBasicConfig> projectBasicConfigsOfNode = getProjectBasicConfigs(node);
				if (CollectionUtils.isNotEmpty(projectBasicConfigsOfNode)) {
					projectBasicConfigs.addAll(projectBasicConfigsOfNode);
				}
			}

			Set<ProjectBasicConfig> uniqueProjectBasicConfigs = new HashSet<>(projectBasicConfigs);

			for (ProjectBasicConfig projectBasicConfig : uniqueProjectBasicConfigs) {
				ProjectsForAccessRequest project = new ProjectsForAccessRequest();
				project.setProjectId(projectBasicConfig.getId().toHexString());
				project.setProjectNodeId(projectBasicConfig.getProjectNodeId());
				project.setProjectName(projectBasicConfig.getProjectName());
				project.setHierarchy(projectBasicConfig.getHierarchy());
				projects.add(project);
			}
		}

		return projects;
	}

	private List<ProjectBasicConfig> getProjectBasicConfigs(AccessNode accessNode) {
		String accessLevel = accessNode.getAccessLevel();
		List<AccessItem> accessItems = accessNode.getAccessItems();

		if (accessLevel.equals(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT)) {
			return findByIdIn(accessItems.stream().map(AccessItem::getItemId).collect(Collectors.toSet()));
		} else {
			List<String> accessItemIds = accessItems.stream().map(AccessItem::getItemId).collect(Collectors.toList());
			return filterProjectsByHierarchyLevelAndValue(accessLevel, accessItemIds);
		}
	}

	private List<ProjectBasicConfig> findByIdIn(Set<String> projectBasicConfigNodeIds) {
		return projectBasicConfigService.getAllProjectBasicConfigs(Boolean.TRUE).stream()
				.filter(project -> projectBasicConfigNodeIds.contains(project.getProjectNodeId()))
				.collect(Collectors.toList());
	}

	private List<ProjectBasicConfig> filterProjectsByHierarchyLevelAndValue(String hierarchyLevelId,
			List<String> orgHierarchyNodeIds) {

		return projectBasicConfigService.getAllProjectBasicConfigs(Boolean.TRUE).stream().filter(project -> project
				.getHierarchy().stream()
				.anyMatch(hierarchy -> hierarchy.getHierarchyLevel().getHierarchyLevelId().equals(hierarchyLevelId)
						&& orgHierarchyNodeIds.contains(hierarchy.getOrgHierarchyNodeId())))
				.collect(Collectors.toList());
	}

	private AccessRequest getAccessRequest(String id) {
		return accessRequestsRepository.findById(id);
	}

	public boolean hasProjectEditPermission(ObjectId projectBasicConfigId, String username) {

		if (projectBasicConfigId == null || username == null) {
			return false;
		}

		UserInfo userInfo = getUserInfo(username);

		if (userInfo.getAuthorities().contains(Constant.ROLE_SUPERADMIN)) {
			return true;
		}

		if (CollectionUtils.isEmpty(userInfo.getProjectsAccess())) {
			return false;
		}

		List<RoleWiseProjects> roleWiseProjects = getProjectAccessesWithRole(username);

		RoleWiseProjects roleWiseAdminProjects = roleWiseProjects.stream()
				.filter(roleWiseProject -> roleWiseProject.getRole().equals(Constant.ROLE_PROJECT_ADMIN)).findFirst()
				.orElse(null);

		if (roleWiseAdminProjects != null) {
			List<ProjectsForAccessRequest> adminProjects = roleWiseAdminProjects.getProjects();
			return adminProjects.stream()
					.anyMatch(adminProject -> adminProject.getProjectId().equals(projectBasicConfigId.toString()));
		} else {
			return false;
		}
	}

	private boolean isAccessRequestDeletable(String id) {
		boolean isDeletePermitted = false;
		AccessRequest requestData = getAccessRequest(id);
		if (null != requestData) {
			String username = authenticationService.getLoggedInUser();
			UserInfo userInfo = getUserInfo(username);
			if ((username.equals(requestData.getUsername())
					|| userInfo.getAuthorities().contains(Constant.ROLE_SUPERADMIN))
					&& requestData.getStatus().equals(Constant.ACCESS_REQUEST_STATUS_PENDING)) {
				isDeletePermitted = true;
			} else {
				log.info("Unauthorized to perform deletion of id " + id);
			}
		}
		return isDeletePermitted;
	}

	public boolean deleteAccessRequestById(String id) {
		boolean isDeleted = false;
		if (ObjectId.isValid(id) && isAccessRequestDeletable(id)) {
			accessRequestsRepository.deleteById(new ObjectId(id));
			isDeleted = true;
		} else {
			log.info("Access request id {} is not valid or deletion is not permitted", id);
		}
		return isDeleted;
	}

	public String getAccessRoleOfNearestParent(ProjectBasicConfigDTO projectBasicConfigDTO, String username) {

		Map<String, String> parents = new LinkedHashMap<>();
		List<HierarchyValueDTO> hierarchyLevelValues = projectBasicConfigDTO.getHierarchy();
		CollectionUtils.emptyIfNull(hierarchyLevelValues).stream()
				.sorted(Comparator
						.comparing((HierarchyValueDTO hierarchyValue) -> hierarchyValue.getHierarchyLevel().getLevel())
						.reversed())
				.forEach(hierarchyValue -> parents.put(hierarchyValue.getHierarchyLevel().getHierarchyLevelId(),
						hierarchyValue.getValue()));

		UserInfo userInfo = getUserInfo(username);
		List<ProjectsAccess> projectsAccesses = userInfo.getProjectsAccess();
		String result = null;

		if (CollectionUtils.isEmpty(projectsAccesses)) {
			return null;
		} else {

			for (Entry<String, String> entry : parents.entrySet()) {
				String k = entry.getKey();
				String v = entry.getValue();
				Map<String, List<String>> roleWiseAccessIds = projectsAccesses.stream()
						.collect(Collectors.toMap(ProjectsAccess::getRole,
								projectsAccess -> projectsAccess.getAccessNodes().stream()
										.filter(accessNode -> accessNode.getAccessLevel().equalsIgnoreCase(k))
										.flatMap(accessNode -> accessNode.getAccessItems().stream())
										.map(AccessItem::getItemId).collect(Collectors.toList())));
				String role = roleWiseAccessIds.entrySet().stream().filter(e -> e.getValue().contains(v))
						.map(Entry::getKey).findFirst().orElse(null);

				if (role != null) {
					result = role;
					break;
				}
			}
		}

		return result;
	}

	public UserInfo addNewProjectIntoUserInfo(ProjectBasicConfig basicConfig, String username) {
		AccessItem newAccessItem = new AccessItem();
		newAccessItem.setItemId(basicConfig.getProjectNodeId());

		UserInfo userInfo = getUserInfo(username);

		if (userInfo.getAuthorities().contains(Constant.ROLE_PROJECT_ADMIN)) {
			Optional<AccessNode> projectNode = userInfo.getProjectsAccess().stream()
					.filter(projectAccess -> Constant.ROLE_PROJECT_ADMIN.equals(projectAccess.getRole()))
					.flatMap(projectsAccess -> projectsAccess.getAccessNodes().stream()).filter(accessNode -> accessNode
							.getAccessLevel().equalsIgnoreCase(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
					.findFirst();

			if (projectNode.isPresent()) {
				projectNode.get().getAccessItems().add(newAccessItem);
			} else {
				AccessNode accessNode = new AccessNode();
				accessNode.setAccessLevel(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);
				List<AccessItem> accessItems = new ArrayList<>();
				accessItems.add(newAccessItem);
				accessNode.setAccessItems(accessItems);

				userInfo.getProjectsAccess().stream()
						.filter(projectAccess -> Constant.ROLE_PROJECT_ADMIN.equals(projectAccess.getRole()))
						.findFirst().ifPresent(projectsAccess -> projectsAccess.getAccessNodes().add(accessNode));
			}

		} else {
			ProjectsAccess newProjectAccess = new ProjectsAccess();
			newProjectAccess.setRole(Constant.ROLE_PROJECT_ADMIN);
			AccessNode accessNode = new AccessNode();
			accessNode.setAccessLevel(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);
			List<AccessItem> accessItems = new ArrayList<>();
			accessItems.add(newAccessItem);
			accessNode.setAccessItems(accessItems);
			List<AccessNode> accessNodes = new ArrayList<>();
			accessNodes.add(accessNode);
			newProjectAccess.setAccessNodes(accessNodes);
			userInfo.getProjectsAccess().add(newProjectAccess);
			userInfo.getAuthorities().add(Constant.ROLE_PROJECT_ADMIN);
		}

		return userInfoRepository.save(userInfo);
	}

	public void removeProjectAccessFromAllUsers(String basicProjectConfigId) {

		List<UserInfo> userInfoList = userInfoCustomRepository.findByProjectAccess(basicProjectConfigId);

		for (UserInfo userInfo : userInfoList) {
			userInfo.getProjectsAccess().stream().flatMap(projectsAccess -> projectsAccess.getAccessNodes().stream())
					.forEach(accessNode -> accessNode.getAccessItems()
							.removeIf(accessItem -> accessItem.getItemId().equals(basicProjectConfigId)));
			saveUserInfo(userInfo);
		}
	}

	public UserInfo updateAccessOfUserInfo(UserInfo existingUserInfo, UserInfo requestedUserInfo) {
		UserInfo resultUserInfo = copyUserInfo(existingUserInfo);
		List<ProjectsAccess> projectsAccess = new ArrayList<>();
		if (requestedUserInfo != null && !requestedUserInfo.getProjectsAccess().isEmpty()) {
			projectsAccess = requestedUserInfo.getProjectsAccess();
		}
		if (projectsAccess.stream().anyMatch(pa -> pa.getRole().equalsIgnoreCase(Constant.ROLE_SUPERADMIN))) {
			makeItSuperAdmin(resultUserInfo);
		} else {
			makeItDefaultNewUser(resultUserInfo);

			Map<String, Integer> allowedAccessLevelsOrder = new HashMap<>();
			CollectionUtils.emptyIfNull(hierarchyLevelService.getTopHierarchyLevels()).stream()
					.forEach(hierarchyLevel -> allowedAccessLevelsOrder.put(hierarchyLevel.getHierarchyLevelId(),
							hierarchyLevel.getLevel()));
			allowedAccessLevelsOrder.put(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT,
					hierarchyLevelService.getTopHierarchyLevels().size() + 1);
			ProjectBasicConfigNode projectBasicConfigNode = projectBasicConfigService.getBasicConfigTree();
			Map<String, String> organizationHierarchyMap = createOrganizationHierarchyMap();
			projectsAccess.forEach(projectAccess -> {
				List<AccessNode> anList = new CopyOnWriteArrayList<>(projectAccess.getAccessNodes());
				Collections.sort(anList, Comparator.comparing(o -> allowedAccessLevelsOrder.get(o.getAccessLevel())));
				for (AccessNode an : anList) {
					checkOnNewUser(resultUserInfo, projectAccess, an, projectBasicConfigNode, organizationHierarchyMap);
				}
			});
			cleanUserInfo(resultUserInfo);
		}

		if (requestedUserInfo != null) {
			List<AccessRequest> accessRequests = accessRequestsRepository
					.findByUsernameAndStatus(requestedUserInfo.getUsername(), Constant.ACCESS_REQUEST_STATUS_APPROVED);

			List<ObjectId> obsoleteAccessRequests = getObsoleteAccessRequests(requestedUserInfo, accessRequests);
			accessRequestsRepository.deleteById(obsoleteAccessRequests);
		}

		return saveUserInfo(resultUserInfo);
	}

	public List<ObjectId> getObsoleteAccessRequests(UserInfo userInfo, List<AccessRequest> accessRequests) {
		List<ObjectId> toBeDeleted = new ArrayList<>();

		Set<String> validAccessKeys = new HashSet<>();
		for (ProjectsAccess pa : userInfo.getProjectsAccess()) {
			String role = pa.getRole();
			for (AccessNode node : pa.getAccessNodes()) {
				String level = node.getAccessLevel();
				for (AccessItem item : node.getAccessItems()) {
					validAccessKeys.add(role + "::" + level + "::" + item.getItemId());
				}
			}
		}

		for (AccessRequest ar : accessRequests) {

			String role = ar.getRole();
			String level = ar.getAccessNode().getAccessLevel();
			List<AccessItem> items = ar.getAccessNode().getAccessItems();

			boolean hasInvalidItem = items.stream().anyMatch(item -> {
				String key = role + "::" + level + "::" + item.getItemId();
				return !validAccessKeys.contains(key);
			});

			if (hasInvalidItem) {
				toBeDeleted.add(ar.getId());
			}
		}

		return toBeDeleted;
	}

	private void checkOnNewUser(UserInfo resultUserInfo, ProjectsAccess projectAccess, AccessNode an,
			ProjectBasicConfigNode projectBasicConfigNode, Map<String, String> organizationHierarchyMap) {
		if (isNewUser(resultUserInfo)) {
			updateAuthorities(resultUserInfo, projectAccess.getRole());
			setFirstProjectsAccess(resultUserInfo, projectAccess.getRole(), an);
		} else {
			String accessLevel = an.getAccessLevel();
			List<AccessItem> accessItems = new CopyOnWriteArrayList<>(an.getAccessItems());

			Map<String, Set<String>> globalChildrenMap = new HashMap<>();

			createGlobalChildrenMap(accessLevel, an.getAccessItems(), projectBasicConfigNode, globalChildrenMap);

			for (AccessItem accessItem : accessItems) {
				Map<String, Set<String>> globalParentMap = new HashMap<>();
				Map<String, Set<String>> existingAccessMap = new HashMap<>();
				if (resultUserInfo != null) {
					creatingExistingAccessesMap(resultUserInfo.getProjectsAccess(), existingAccessMap);
				}
				creatingGlobalParentMap(accessLevel, Stream.of(accessItem.getItemId()).collect(Collectors.toSet()),
						projectBasicConfigNode, globalParentMap);
				if (hasAccessToParentLevel(globalParentMap, existingAccessMap)) {
					log.debug("parent already added");
					continue;
				}
				if (resultUserInfo != null) {
					modifyUserInfoForAccessManagement(an, projectAccess.getRole(), resultUserInfo, accessLevel,
							globalChildrenMap, organizationHierarchyMap);
				}
			}
		}
	}

	private void makeItDefaultNewUser(UserInfo resultUserInfo) {
		if (resultUserInfo != null) {
			resultUserInfo.setProjectsAccess(new ArrayList<>());
			resultUserInfo.setAuthorities(new ArrayList<>(Arrays.asList(Constant.ROLE_VIEWER)));
		}
	}

	private void makeItSuperAdmin(UserInfo resultUserInfo) {
		if (resultUserInfo != null) {
			resultUserInfo.setProjectsAccess(new ArrayList<>());
			resultUserInfo.setAuthorities(Arrays.asList(Constant.ROLE_SUPERADMIN));
		}
	}

	private void modifyUserInfoForAccessManagement(AccessNode an, String role, UserInfo userInfo, String accessLevel,
			Map<String, Set<String>> globalChildrenMap, Map<String, String> organizationHierarchyMap) {
		List<AccessItem> aiList = new CopyOnWriteArrayList<>(an.getAccessItems());
		for (AccessItem ai : aiList) {
			String existingRoleForItem = findRoleOfAccessItem(accessLevel, ai, userInfo.getProjectsAccess());

			if (existingRoleForItem != null) {
				if (role.equals(existingRoleForItem)) {
					log.info("already has same access for {}", organizationHierarchyMap.get(ai.getItemId()));
					// do nothing
				} else {
					// remove item from old role and add to new role
					moveItemIntoNewRole(accessLevel, ai, existingRoleForItem, role, userInfo);
				}
			} else {
				removeChildren(globalChildrenMap, userInfo);
				addAccessItemToProjectAccess(accessLevel, ai, role, userInfo);
			}
		}
	}

	public boolean canTriggerProcessorFor(List<String> projectBasicConfigIds, String username) {
		if (CollectionUtils.isEmpty(projectBasicConfigIds)) {
			return false;
		}

		boolean result = true;

		for (String projectBasicConfigId : projectBasicConfigIds) {
			if (!hasProjectEditPermission(new ObjectId(projectBasicConfigId), username)) {
				result = false;
				break;
			}
		}

		return result;
	}

	/**
	 * This method get project id based on user and role
	 *
	 * @param user
	 *            user
	 * @param roleList
	 *            roleList
	 * @return list of projectId
	 */
	public List<String> getProjectBasicOnRoleList(UserInfo user, List<String> roleList) {
		ProjectsAccess projectAccess = user.getProjectsAccess().stream()
				.filter(access -> roleList.contains(access.getRole())).findAny().orElse(null);
		List<String> projectIdList = new ArrayList<>();
		if (null != projectAccess) {
			projectIdList = getProjects(projectAccess.getAccessNodes()).stream()
					.map(ProjectsForAccessRequest::getProjectNodeId).collect(Collectors.toList());
		}
		return projectIdList;
	}
}
