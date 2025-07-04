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

package com.publicissapient.kpidashboard.apis.connection.service;

import static com.publicissapient.kpidashboard.apis.constant.Constant.REPO_TOOLS;
import static com.publicissapient.kpidashboard.apis.constant.Constant.TOOL_ARGOCD;
import static com.publicissapient.kpidashboard.apis.constant.Constant.TOOL_AZURE;
import static com.publicissapient.kpidashboard.apis.constant.Constant.TOOL_AZUREPIPELINE;
import static com.publicissapient.kpidashboard.apis.constant.Constant.TOOL_AZUREREPO;
import static com.publicissapient.kpidashboard.apis.constant.Constant.TOOL_BAMBOO;
import static com.publicissapient.kpidashboard.apis.constant.Constant.TOOL_BITBUCKET;
import static com.publicissapient.kpidashboard.apis.constant.Constant.TOOL_GITHUB;
import static com.publicissapient.kpidashboard.apis.constant.Constant.TOOL_GITLAB;
import static com.publicissapient.kpidashboard.apis.constant.Constant.TOOL_JENKINS;
import static com.publicissapient.kpidashboard.apis.constant.Constant.TOOL_JIRA;
import static com.publicissapient.kpidashboard.apis.constant.Constant.TOOL_SONAR;
import static com.publicissapient.kpidashboard.apis.constant.Constant.TOOL_TEAMCITY;
import static com.publicissapient.kpidashboard.apis.constant.Constant.TOOL_ZEPHYR;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.abac.UserAuthorizedProjectsService;
import com.publicissapient.kpidashboard.apis.auth.model.Authentication;
import com.publicissapient.kpidashboard.apis.auth.repository.AuthenticationRepository;
import com.publicissapient.kpidashboard.apis.auth.service.AuthenticationService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.enums.NotificationCustomDataEnum;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.repotools.service.RepoToolsConfigServiceImpl;
import com.publicissapient.kpidashboard.apis.util.RestAPIUtils;
import com.publicissapient.kpidashboard.common.constant.ProcessorConstants;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.model.connection.Connection;
import com.publicissapient.kpidashboard.common.model.connection.ConnectionDTO;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectToolConfigRepository;
import com.publicissapient.kpidashboard.common.repository.connection.ConnectionRepository;
import com.publicissapient.kpidashboard.common.repository.rbac.UserInfoRepository;
import com.publicissapient.kpidashboard.common.service.AesEncryptionService;
import com.publicissapient.kpidashboard.common.service.NotificationService;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import java.time.format.DateTimeParseException;

/**
 * This class provides various methods related to operations on Connections
 *
 * @author dilip
 * @author jagmongr
 */
@Service
@Slf4j
public class ConnectionServiceImpl implements ConnectionService {

	private static final String CONNECTION_EMPTY_MSG = "Connection name cannot be empty";
	private static final String ERROR_MSG = "A connection with same details already exists. Connection name is ";
	private static final String NOTIFICATION_KEY = "Broken_Connection";
	@Autowired
	private AesEncryptionService aesEncryptionService;

	@Autowired
	private ConnectionRepository connectionRepository;

	@Autowired
	private ProjectToolConfigRepository toolRepository;

	@Autowired
	UserInfoRepository userInfoRepository;

	@Autowired
	AuthenticationRepository authenticationRepository;

	@Autowired
	private CustomApiConfig customApiConfig;

	@Autowired
	private UserAuthorizedProjectsService authorizedProjectsService;

	@Autowired
	private ProjectBasicConfigRepository projectBasicConfigRepository;

	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private RepoToolsConfigServiceImpl repoToolsConfigService;

	@Autowired
	private RestAPIUtils restAPIUtils;

	@Autowired
	private KafkaTemplate<String, Object> kafkaTemplate;

	/**
	 * Fetch all connection data.
	 *
	 * @return ServiceResponse with data object,message and status flag true if data
	 *         is found,false if not data found
	 */
	@Override
	public ServiceResponse getAllConnection() {
		final List<Connection> connectionData = connectionRepository.findAllWithoutSecret();
		if (CollectionUtils.isEmpty(connectionData)) {
			log.info("Db has no connectionData");
			return new ServiceResponse(false, "No connectionData in connection db", connectionData);
		}

		if (authorizedProjectsService.ifSuperAdminUser()) {
			maskConnectionDetails(connectionData);
			log.info("Successfully fetched all connectionData");
			return new ServiceResponse(true, "Found all connectionData", connectionData);
		}

		List<Connection> nonAuthConnection = new ArrayList<>();

		connectionData.stream()
				.filter(
						e -> !e.isSharedConnection() && !e.getConnectionUsers().contains(authenticationService.getLoggedInUser()))
				.forEach(nonAuthConnection::add);

		if (CollectionUtils.isNotEmpty(nonAuthConnection)) {
			connectionData.removeAll(nonAuthConnection);
		}
		maskConnectionDetails(connectionData);
		log.info("Successfully fetched all connectionData");
		return new ServiceResponse(true, "Found all connectionData", connectionData);
	}

	/**
	 * Mask user info from connection details
	 *
	 * @param connectionData
	 *          list of connections
	 */
	public void maskConnectionDetails(List<Connection> connectionData) {
		connectionData.forEach(original -> {
			original.setCreatedBy(maskStrings(original.getCreatedBy()));
			original.setUsername(maskStrings(original.getUsername()));
			original.setUpdatedBy(maskStrings(original.getUpdatedBy()));
			if (CollectionUtils.isNotEmpty(original.getConnectionUsers())) {
				List<String> connectionUsers = new ArrayList<>();
				original.getConnectionUsers().forEach(connectionUser -> connectionUsers.add(maskStrings(connectionUser)));
				original.setConnectionUsers(connectionUsers);
			}
		});
	}

	private String maskStrings(String username) {
		if (StringUtils.isNotEmpty(username)) {
			if (username.contains("@")) {
				String[] parts = username.split("@");
				if (parts.length == 2) {
					String localPart = parts[0];
					String domainPart = parts[1];
					String maskedLocalPart = maskingLogic(localPart);
					return maskedLocalPart + "@" + domainPart;
				}
			} else {
				return maskingLogic(username);
			}
		}
		return username;
	}

	/**
	 * if length is more than 2 and less than 8, mask the last 3 characters if lenth
	 * is more than 8 mask the last 3 character and the 4th character
	 *
	 * @param userInput
	 *          inputString
	 * @return maskedString
	 */
	private String maskingLogic(String userInput) {
		if (userInput.length() > 2) {
			userInput = maskCharacters(userInput);
			if (userInput.length() >= 8) {
				StringBuilder stringBuilder = new StringBuilder(userInput);
				stringBuilder.setCharAt(4, '*');
				userInput = stringBuilder.toString();
			}
		}
		return userInput;
	}

	private static String maskCharacters(String input) {
		int length = input.length();
		int startIndex = length - 3;
		StringBuilder maskedString = new StringBuilder(input);
		for (int i = startIndex; i < length; i++) {
			maskedString.setCharAt(i, '*');
		}
		return maskedString.toString();
	}

	/**
	 * Fetch a connection by type.
	 *
	 * @param type
	 *          type
	 * @return ServiceResponse with data object,message and status flag true if data
	 *         is found,false if not data found
	 */
	@Override
	public ServiceResponse getConnectionByType(String type) {
		if (null == type) {
			return new ServiceResponse(false, "No type in this collection", type);
		}
		List<Connection> typeList = connectionRepository.findAllWithoutSecret().stream()
				.filter(
						connection -> StringUtils.isNotEmpty(connection.getType()) && connection.getType().equalsIgnoreCase(type))
				.collect(Collectors.toList());

		if (CollectionUtils.isEmpty(typeList)) {
			log.info("connection Db returned null");
			return new ServiceResponse(false, "connection@" + type + " does not exist", null);
		}

		if (authorizedProjectsService.ifSuperAdminUser()) {
			maskConnectionDetails(typeList);
			log.info("Successfully found type@{}", type);
			return new ServiceResponse(true, "Found type@" + type, typeList);
		}

		List<Connection> nonAuthConnection = new ArrayList<>();
		typeList.stream()
				.filter(
						e -> !e.isSharedConnection() && !e.getConnectionUsers().contains(authenticationService.getLoggedInUser()))
				.forEach(nonAuthConnection::add);

		if (CollectionUtils.isNotEmpty(nonAuthConnection)) {
			typeList.removeAll(nonAuthConnection);
		}
		maskConnectionDetails(typeList);
		log.info("Successfully found type@{}", type);

		return new ServiceResponse(true, "Found type@" + type, typeList);
	}

	/**
	 * Create and save a connection in the database.
	 *
	 * @param conn
	 *          as connection
	 * @return ServiceResponse with data object,message and status flag true if data
	 *         is found,false if not data found
	 */
	@Override
	public ServiceResponse saveConnectionDetails(Connection conn) {

		conn.setId(new ObjectId());
		if (!isDataValid(conn)) {
			log.info("connectionName is empty");
			return new ServiceResponse(false, CONNECTION_EMPTY_MSG, null);
		}
		String username = authenticationService.getLoggedInUser();
		Connection connName = connectionRepository.findByConnectionName(conn.getConnectionName());
		String api = "save";

		if (connName == null) {
			List<Connection> publicConnections = connectionRepository.findByTypeAndSharedConnection(conn.getType(), true);

			List<Connection> privateConnections = connectionRepository.findByTypeAndSharedConnection(conn.getType(), false)
					.stream().filter(e -> e.getConnectionUsers().contains(authenticationService.getLoggedInUser()))
					.collect(Collectors.toList());

			Connection existingPublicConn = findConnectionWithSameDetails(conn, publicConnections, api);
			Connection existingPrivateConn = findConnectionWithSameDetails(conn, privateConnections, api);

			if (null != existingPublicConn) {
				return new ServiceResponse(false, ERROR_MSG + existingPublicConn.getConnectionName(), null);
			}

			if (null != existingPrivateConn) {
				return new ServiceResponse(false, ERROR_MSG + existingPrivateConn.getConnectionName(), null);
			} else {

				List<String> connectionUser = new ArrayList<>();
				connectionUser.add(username);
				encryptSecureFields(conn);
				conn.setCreatedAt(DateUtil.dateTimeFormatter(LocalDateTime.now(), DateUtil.TIME_FORMAT));
				conn.setCreatedBy(username);
				conn.setUpdatedBy(username);
				conn.setConnectionUsers(connectionUser);
				log.info("Successfully pushed connection into db");
				connectionRepository.save(conn);
				final ModelMapper modelMapper = new ModelMapper();
				final ConnectionDTO connectionDTO = modelMapper.map(conn, ConnectionDTO.class);
				connectionDTO.setConnectionUsers(connectionUser);
				removeSecureFields(connectionDTO);

				return new ServiceResponse(true, "created and saved new connection", connectionDTO);
			}
		}

		return new ServiceResponse(false, "Connection already exists with same name. Please choose a different name", null);
	}

	/*
	 *
	 * Find Connection with same details from db
	 *
	 * @param Connection,connList
	 *
	 * @return existing Connection
	 *
	 */

	private Connection findConnectionWithSameDetails(Connection inputConn, List<Connection> connList, String api) {
		Connection existingConn = null;
		for (Connection currConn : connList) {
			existingConn = connByType(inputConn, currConn, api);
			if (existingConn != null) {
				break;
			}
		}
		return existingConn;
	}

	/*
	 * Fetch existing Connection from db
	 *
	 * @param inputConn,currConn Db
	 *
	 * @return existing Conn
	 */

	private Connection connByType(Connection inputConn, Connection currConn, String api) {
		Connection existingConnection = null;
		switch (inputConn.getType()) {
			case TOOL_SONAR :
				existingConnection = checkConnDetailsSonar(inputConn, currConn, api);
				break;
			case TOOL_BAMBOO :
			case REPO_TOOLS :
			case TOOL_TEAMCITY :
			case TOOL_ARGOCD :
				if (checkConnDetails(inputConn, currConn))
					existingConnection = currConn;
				break;
			case TOOL_GITHUB :
			case TOOL_GITLAB :
				boolean commonConnection = checkConnDetails(inputConn, currConn);
				checkVaultConnection(inputConn, currConn, existingConnection, commonConnection);
				break;
			case TOOL_AZURE :
			case TOOL_AZUREPIPELINE :
			case TOOL_AZUREREPO :
				checkVaultConnection(inputConn, currConn, existingConnection,
						inputConn.getBaseUrl().equals(currConn.getBaseUrl()));
				break;
			case TOOL_JIRA :
			case TOOL_BITBUCKET :
				if (checkConnDetails(inputConn, currConn) && inputConn.getApiEndPoint().equals(currConn.getApiEndPoint()))
					existingConnection = currConn;
				break;
			case TOOL_JENKINS :
				checkVaultConnection(inputConn, currConn, existingConnection, checkConnDetails(inputConn, currConn));
				break;
			case TOOL_ZEPHYR :
				existingConnection = checkConnDetailsZephyr(inputConn, currConn, api);
				break;
			default :
				existingConnection = new Connection();
				break;
		}
		return existingConnection;
	}

	private boolean checkConnDetails(Connection inputConn, Connection currConn) {
		boolean b = false;
		if (!inputConn.isOffline() && inputConn.getUsername().equals(currConn.getUsername()) &&
				inputConn.getBaseUrl().equals(currConn.getBaseUrl()))
			b = true;
		return b;
	}

	/**
	 * Modify/Update a connection by id.
	 *
	 * @param connection
	 *          as connection.
	 * @return ServiceResponse with data object,message and status flag true if data
	 *         is found,false if not data found.
	 */
	@Override
	public ServiceResponse updateConnection(String id, Connection connection) {
		String username = authenticationService.getLoggedInUser();
		if (connection == null) {
			log.info("connection is null");
			return new ServiceResponse(false, "Invalid connection", null);
		}
		String api = "update";
		if (!isDataValid(connection)) {

			log.info("connectionName is empty");
			return new ServiceResponse(false, CONNECTION_EMPTY_MSG, null);
		}
		if (!ObjectId.isValid(id)) {
			log.info("Id not valid");
			return new ServiceResponse(false, "Invalid connection id " + id, null);
		}
		Optional<Connection> existingConnOpt = connectionRepository.findById(new ObjectId(id));
		if (!existingConnOpt.isPresent()) {
			return new ServiceResponse(false, "No connectionId found to update", null);
		}
		Connection existingConnection = existingConnOpt.get();
		Connection connectionWithSameName = connectionRepository.findByConnectionName(connection.getConnectionName());
		if (connectionWithSameName != null && !existingConnection.getId().equals(connectionWithSameName.getId())) {
			return new ServiceResponse(false,
					connection.getConnectionName() + " is already exists. Please try again with different name", null);
		}

		if (!authorizedProjectsService.ifSuperAdminUser() &&
				!existingConnection.getCreatedBy().equals(authenticationService.getLoggedInUser())) {
			return new ServiceResponse(false, existingConnection.getConnectionName() +
					" connection can't be updated as created by user " + existingConnection.getCreatedBy(), null);
		}

		List<Connection> filteredConn = getFilteredConnection(username, connection, existingConnection.getId());
		Connection existingOtherConn = findConnectionWithSameDetails(connection, filteredConn, api);

		if (null != existingOtherConn && !existingOtherConn.getId().toHexString().equals(id)) {
			return new ServiceResponse(false, ERROR_MSG + existingOtherConn.getConnectionName(), null);
		}

		encryptSecureFields(connection);
		mapConnection(connection, existingConnection);
		saveConnection(existingConnection);
		log.info("Successfully modified connection {}", existingConnection.getConnectionName());
		final ModelMapper modelMapper = new ModelMapper();
		final ConnectionDTO connectionDTO = modelMapper.map(existingConnection, ConnectionDTO.class);
		removeSecureFields(connectionDTO);
		if (CollectionUtils.isNotEmpty(existingConnection.getConnectionUsers())) {
			connectionDTO.setConnectionUsers(existingConnection.getConnectionUsers());
		}
		return new ServiceResponse(true, "modified connection " + existingConnection.getConnectionName(), connectionDTO);
	}

	private Connection checkConnDetailsZephyr(Connection inputConn, Connection currConn, String api) {
		Connection existingConnection = null;
		if (inputConn.isCloudEnv()) {
			boolean sameURLCheck = api.equals("save") && inputConn.getBaseUrl().equals(currConn.getBaseUrl());
			existingConnection = checkVaultConnection(inputConn, currConn, existingConnection, sameURLCheck);
		} else {
			if (checkConnDetails(inputConn, currConn) && inputConn.getApiEndPoint().equals(currConn.getApiEndPoint())) {
				existingConnection = currConn;
			}
		}
		return existingConnection;
	}

	private Connection checkVaultConnection(Connection inputConn, Connection currConn, Connection existingConnection,
			boolean sameUrlcheck) {
		if (!inputConn.isVault()) {
			boolean accessTokenSimilarity;
			try {
				switch (inputConn.getType()) {
					case TOOL_SONAR :
					case TOOL_ZEPHYR :
					case TOOL_GITLAB :
						String accessToken = inputConn.getAccessToken();
						String accessTokenExists = aesEncryptionService.decrypt(currConn.getAccessToken(),
								customApiConfig.getAesEncryptionKey());
						accessTokenSimilarity = accessToken.equals(accessTokenExists);
						break;
					case TOOL_AZUREREPO :
						String pat = inputConn.getPat();
						String patExists = aesEncryptionService.decrypt(currConn.getPat(), customApiConfig.getAesEncryptionKey());
						accessTokenSimilarity = pat.equals(patExists);
						break;
					case TOOL_JENKINS :
						String apiKey = inputConn.getApiKey();
						String apiKeyExists = aesEncryptionService.decrypt(currConn.getApiKey(),
								customApiConfig.getAesEncryptionKey());
						accessTokenSimilarity = apiKey.equals(apiKeyExists);
						break;
					default :
						accessTokenSimilarity = false;
						break;
				}
			} catch (Exception exception) {
				accessTokenSimilarity = false;
			}
			if (sameUrlcheck && accessTokenSimilarity) {
				existingConnection = currConn;
			}
		} else {
			if (sameUrlcheck) {
				existingConnection = currConn;
			}
		}
		return existingConnection;
	}

	/*
	 * Fetch list of all connections by Type from db
	 *
	 * @param Logged username,inputConn,ObjectId
	 *
	 * @return Connection List
	 *
	 */

	private List<Connection> getFilteredConnection(String username, Connection inputConn, ObjectId objId) {
		List<Connection> connection = connectionRepository.findByType(inputConn.getType());

		return connection.stream()
				.filter(e -> e.isSharedConnection() || e.getConnectionUsers().contains(username) && !e.getId().equals(objId))
				.collect(Collectors.toList());
	}

	private void mapConnection(Connection connection, Connection existingConnection) {
		existingConnection.setType(connection.getType());

		if (StringUtils.isNotEmpty(connection.getAccessToken())) {
			existingConnection.setAccessToken(connection.getAccessToken());
		}
		existingConnection.setApiEndPoint(connection.getApiEndPoint());
		if (StringUtils.isNotEmpty(connection.getApiKey())) {
			existingConnection.setApiKey(connection.getApiKey());
		}
		existingConnection.setApiKeyFieldName(connection.getApiKeyFieldName());
		existingConnection.setBaseUrl(connection.getBaseUrl());
		if (StringUtils.isNotEmpty(connection.getClientId())) {
			existingConnection.setClientId(connection.getClientId());
		}
		if (StringUtils.isNotEmpty(connection.getClientSecretKey())) {
			existingConnection.setClientSecretKey(connection.getClientSecretKey());
		}
		existingConnection.setConnectionName(connection.getConnectionName());
		existingConnection.setConsumerKey(connection.getConsumerKey());
		if (StringUtils.isNotEmpty(connection.getPassword())) {
			existingConnection.setPassword(connection.getPassword());
		}
		if (StringUtils.isNotEmpty(connection.getPat())) {
			existingConnection.setPat(connection.getPat());
		}

		if (StringUtils.isNotEmpty(connection.getPrivateKey())) {
			existingConnection.setPrivateKey(connection.getPrivateKey());
		}
		existingConnection.setUsername(connection.getUsername());
		existingConnection.setTenantId(connection.getTenantId());
		existingConnection.setOffline(connection.isOffline());
		existingConnection.setOfflineFilePath(connection.getOfflineFilePath());
		existingConnection.setCloudEnv(connection.isCloudEnv());
		existingConnection.setVault(connection.isVault());
		existingConnection.setUpdatedAt(DateUtil.dateTimeFormatter(LocalDateTime.now(), DateUtil.TIME_FORMAT));
		existingConnection.setSharedConnection(connection.isSharedConnection());
		existingConnection.setAccessTokenEnabled(connection.isAccessTokenEnabled());
		existingConnection.setUpdatedBy(authenticationService.getLoggedInUser());
		existingConnection.setPatOAuthToken(connection.getPatOAuthToken());
		existingConnection.setBearerToken(connection.isBearerToken());
		existingConnection.setJaasKrbAuth(connection.isJaasKrbAuth());
		existingConnection.setJaasConfigFilePath(connection.getJaasConfigFilePath());
		existingConnection.setJaasUser(connection.getJaasUser());
		existingConnection.setSamlEndPoint(connection.getSamlEndPoint());
		existingConnection.setKrb5ConfigFilePath(connection.getKrb5ConfigFilePath());
		existingConnection.setEmail(connection.getEmail());
		existingConnection.setBrokenConnection(false);
	}

	private void saveConnection(Connection conn) {
		if (conn != null) {
			if (conn.getType().equalsIgnoreCase(REPO_TOOLS)) {
				int httpStatus = repoToolsConfigService.updateRepoToolConnection(conn);
				if (httpStatus == HttpStatus.OK.value())
					connectionRepository.save(conn);
			} else {
				connectionRepository.save(conn);
			}
		}
	}

	/**
	 * Checks if @param Connection has non empty connectionName
	 *
	 * @param conn
	 *          for details.
	 * @return Boolean
	 */
	private boolean isDataValid(Connection conn) {
		if (StringUtils.isEmpty(conn.getConnectionName())) {
			log.info("Mandatory fields need to filled");
			return false;
		}
		log.info("Valid connection object");
		return true;
	}

	private String encryptStringForDb(String rasEncryptedStringFromClient) {
		String encryptedString = aesEncryptionService.encrypt(rasEncryptedStringFromClient,
				customApiConfig.getAesEncryptionKey());
		return encryptedString == null ? "" : encryptedString;
	}

	private void setEncryptedAccessTokenForDb(Connection conn) {
		String accessTokenFromClient = conn.getAccessToken();
		if (StringUtils.isEmpty(accessTokenFromClient)) {
			conn.setAccessToken(conn.getType() == null ? "" : conn.getAccessToken());
		} else {
			conn.setAccessToken(encryptStringForDb(accessTokenFromClient));
		}
	}

	private void setEncryptedAccessTokenForDbZephyr(Connection conn) {
		String accessTokenFromClient = conn.getAccessToken();
		if (StringUtils.isEmpty(accessTokenFromClient)) {
			conn.setAccessToken(conn.getType() == null ? "" : conn.getAccessToken());
		} else {
			conn.setAccessToken(encryptStringForDbZephyr(accessTokenFromClient));
		}
	}

	private void setEncryptedPatField(Connection conn) {
		String passwordFromClient = conn.getPat();
		if (StringUtils.isEmpty(passwordFromClient)) {
			conn.setPat(conn.getType() == null ? "" : conn.getPat());
		} else {
			conn.setPat(encryptStringForDb(passwordFromClient));
		}
	}

	private void setEncryptedPatOAuthTokenForDb(Connection conn) {
		String patOAuthTokenFromClient = conn.getPatOAuthToken();
		if (StringUtils.isEmpty(patOAuthTokenFromClient)) {
			conn.setPatOAuthToken(conn.getType() == null ? "" : conn.getPatOAuthToken());
		} else {
			conn.setPatOAuthToken(encryptStringForDb(patOAuthTokenFromClient));
		}
	}

	private String encryptStringForDbZephyr(String plainTextAccessToken) {
		String encryptedString = aesEncryptionService.encrypt(plainTextAccessToken, customApiConfig.getAesEncryptionKey());
		return encryptedString == null ? "" : encryptedString;
	}

	private void setEncryptedApiKeyForDb(Connection conn) {
		String apiKeyFromClient = conn.getApiKey();
		if (StringUtils.isEmpty(apiKeyFromClient)) {
			conn.setApiKey(conn.getType() == null ? "" : conn.getApiKey());
		} else {
			conn.setApiKey(encryptStringForDb(apiKeyFromClient));
		}
	}

	private void setEncryptedPasswordFieldForDb(Connection conn) {
		String passwordFromClient = conn.getPassword();
		conn.setPassword(encryptStringForDb(passwordFromClient));
	}

	private Connection checkConnDetailsSonar(Connection inputConn, Connection currConn, String api) {
		Connection existingConnection = null;
		if (inputConn.isCloudEnv() || (!inputConn.isCloudEnv() && inputConn.isAccessTokenEnabled())) {
			boolean sameURLCheck = api.equals("save") && inputConn.getBaseUrl().equals(currConn.getBaseUrl());
			existingConnection = checkVaultConnection(inputConn, currConn, existingConnection, sameURLCheck);
		} else {
			if (checkConnDetails(inputConn, currConn)) {
				existingConnection = currConn;
			}
		}
		return existingConnection;
	}

	private void encryptSecureFields(Connection conn) {

		String typeName = conn.getType();
		switch (typeName) {
			case ProcessorConstants.JIRA :
				setEncryptedPasswordFieldForDb(conn);
				if (conn.isBearerToken()) {
					setEncryptedPatOAuthTokenForDb(conn);
				}
				break;
			case ProcessorConstants.BAMBOO :
			case ProcessorConstants.TEAMCITY :
			case ProcessorConstants.BITBUCKET :
				setEncryptedPasswordFieldForDb(conn);
				break;
			case ProcessorConstants.GITLAB :
			case ProcessorConstants.GITHUB :
			case ProcessorConstants.ARGOCD :
				setEncryptedAccessTokenForDb(conn);
				break;
			case ProcessorConstants.JENKINS :
			case ProcessorConstants.NEWREILC :
				setEncryptedApiKeyForDb(conn);
				break;

			case ProcessorConstants.AZURE :
			case ProcessorConstants.AZUREPIPELINE :
			case ProcessorConstants.AZUREREPO :
				setEncryptedPatField(conn);
				break;
			case ProcessorConstants.SONAR :
				if (conn.isCloudEnv() || (!conn.isCloudEnv() && conn.isAccessTokenEnabled())) {
					setEncryptedAccessTokenForDb(conn);
				} else {
					setEncryptedPasswordFieldForDb(conn);
				}
				break;
			case ProcessorConstants.ZEPHYR :
				if (conn.isCloudEnv()) {
					setEncryptedAccessTokenForDbZephyr(conn);
				} else {
					setEncryptedPasswordFieldForDb(conn);
				}
				break;
			default :
				log.error("Unknown type = {}", typeName);
				break;
		}
	}

	private void removeSecureFields(ConnectionDTO connectionDTO) {

		String typeName = connectionDTO.getType();
		switch (typeName) {
			case ProcessorConstants.JIRA :
				connectionDTO.setPassword("");
				connectionDTO.setPatOAuthToken("");
				break;
			case ProcessorConstants.BAMBOO :
			case ProcessorConstants.TEAMCITY :
			case ProcessorConstants.BITBUCKET :
			case ProcessorConstants.ARGOCD :
				connectionDTO.setPassword("");
				break;
			case ProcessorConstants.GITLAB :
			case ProcessorConstants.GITHUB :
				connectionDTO.setAccessToken("");
				break;
			case ProcessorConstants.JENKINS :
			case ProcessorConstants.NEWREILC :
				connectionDTO.setApiKey("");
				break;

			case ProcessorConstants.AZURE :
			case ProcessorConstants.AZUREPIPELINE :
			case ProcessorConstants.AZUREREPO :
				connectionDTO.setPat("");
				break;
			case ProcessorConstants.ZEPHYR :
			case ProcessorConstants.SONAR :
				if (connectionDTO.isCloudEnv()) {
					connectionDTO.setAccessToken("");
				} else {
					connectionDTO.setPassword("");
				}
				break;
			default :
				log.error("Unknown type = {}", typeName);
				break;
		}
	}

	/**
	 * delete a connection by id.
	 *
	 * @param id
	 *          deleted the connection data present at id.
	 * @return ServiceResponse with data object,message and status flag true if data
	 *         is found,false if not data found
	 */
	@Override
	public ServiceResponse deleteConnection(String id) {
		if (!ObjectId.isValid(id)) {
			log.info("Id not valid");
			return new ServiceResponse(false, "Invalid connection id " + id, null);
		}

		Optional<Connection> exisConnectionOpt = connectionRepository.findById(new ObjectId(id));
		if (!exisConnectionOpt.isPresent()) {
			return new ServiceResponse(false, "No connectionId found to delete", null);
		}
		Connection existingConnection = exisConnectionOpt.get();
		if (!authorizedProjectsService.ifSuperAdminUser() &&
				!existingConnection.getCreatedBy().equals(authenticationService.getLoggedInUser())) {
			return new ServiceResponse(false, existingConnection.getConnectionName() +
					" connection can't be Deleted as created by user " + existingConnection.getCreatedBy(), null);
		}

		List<ProjectToolConfig> projectToolConfig = toolRepository.findByConnectionId(new ObjectId(id));

		if (CollectionUtils.isNotEmpty(projectToolConfig)) {
			return new ServiceResponse(false,
					"cannot delete connection, " + existingConnection.getConnectionName() + " is already in use",
					getProjectName(projectToolConfig));
		} else {
			connectionRepository.deleteById(new ObjectId(id));
		}
		return new ServiceResponse(true, "deleted connection " + existingConnection.getConnectionName(), null);
	}

	/**
	 * get a List of projects using connection.
	 *
	 * @param projectToolConfig
	 * @return projectInUseList
	 */
	private List<String> getProjectName(List<ProjectToolConfig> projectToolConfig) {
		Set<ObjectId> basicProjectConfigIds = new HashSet<>();
		List<String> projectInUseList = new ArrayList<>();
		projectToolConfig.forEach(e -> basicProjectConfigIds.add(e.getBasicProjectConfigId()));
		projectBasicConfigRepository.findByIdIn(basicProjectConfigIds)
				.forEach(e -> projectInUseList.add(e.getProjectName()));
		return projectInUseList;
	}

	/**
	 * update breaking connection flag
	 *
	 * @param connection
	 * @param conErrorMsg
	 */
	@Override
	public void updateBreakingConnection(Connection connection, String conErrorMsg) {
		if (connection == null) return;

		connectionRepository.findById(connection.getId())
							.ifPresent(existingConnection -> {
								if (StringUtils.isEmpty(conErrorMsg)) {
									resetConnectionState(existingConnection);
								} else {
									handleBrokenConnection(existingConnection, conErrorMsg);
								}
								connectionRepository.save(existingConnection);
							});
	}

	private void resetConnectionState(Connection connection) {
		connection.setBrokenConnection(false);
		connection.setConnectionErrorMsg(null);
		connection.setNotifiedOn(null);
		connection.setNotificationCount(0);
	}

	private void handleBrokenConnection(Connection connection, String errorMsg) {
		connection.setBrokenConnection(true);
		connection.setConnectionErrorMsg(errorMsg);

		if (shouldSendNotification(connection)) {
			sendBrokenConnectionNotification(connection);
			connection.setNotifiedOn(DateUtil.getTodayTime().toString());
			connection.setNotificationCount(connection.getNotificationCount() + 1);
		}
	}

	private boolean shouldSendNotification(Connection connection) {
		int maxCount = customApiConfig.getBrokenConnectionMaximumEmailNotificationCount();
		int frequencyDays = customApiConfig.getBrokenConnectionEmailNotificationFrequency();

		if (maxCount <= 0) return false;

		int count = connection.getNotificationCount();
		if (count >= maxCount) return false;

		String notifiedOn = connection.getNotifiedOn();
		if (StringUtils.isBlank(notifiedOn)) return true;

		try {
			LocalDateTime lastNotified = LocalDateTime.parse(DateUtil.localDateTimeToUTC(notifiedOn));
			return DateUtil.getTodayTime().isAfter(lastNotified.plusDays(frequencyDays));
		} catch (DateTimeParseException e) {
			log.warn("Invalid notifiedOn timestamp for connection ID {}: {}", connection.getId(), notifiedOn, e);
			return false;
		}
	}

	void sendBrokenConnectionNotification(Connection connection) {
		UserInfo userInfo = userInfoRepository.findByUsername(connection.getCreatedBy());
		if (userInfo == null) {
			log.warn("No userInfo found for username: {}", connection.getCreatedBy());
			return;
		}

		Authentication authentication = authenticationRepository.findByUsername(connection.getCreatedBy());
		String email = authentication == null ? userInfo.getEmailAddress() : authentication.getEmail();
		boolean notifyUserOnError = Boolean.TRUE.equals(isErrorAlertNotificationEnabled(userInfo));

		String subjectTemplate = customApiConfig.getBrokenConnectionEmailNotificationSubject();
		String notificationSubject = subjectTemplate.replace("{{Tool_Name}}", connection.getType());

		String fixUrl = customApiConfig.getUiHost() + customApiConfig.getBrokenConnectionFixUrl();

		if (notifyUserOnError && StringUtils.isNotBlank(email) && StringUtils.isNotBlank(notificationSubject)) {
			Map<String, String> customData = createCustomData(
					userInfo.getDisplayName(),
					connection.getType(),
					fixUrl,
					customApiConfig.getBrokenConnectionHelpUrl()
			);

			String templateKey = customApiConfig.getMailTemplate().getOrDefault(NOTIFICATION_KEY, "");

			log.info("Sending broken connection notification to user: {}", email);
			notificationService.sendNotificationEvent(
					Collections.singletonList(email),
					customData,
					notificationSubject,
					NOTIFICATION_KEY,
					customApiConfig.getKafkaMailTopic(),
					customApiConfig.isNotificationSwitch(),
					kafkaTemplate,
					templateKey,
					customApiConfig.isMailWithoutKafka()
			);
		} else {
			log.info("Notification not sent. Conditions failed — email: {}, notifyUserOnError: {}, subject blank: {}",
					 email, notifyUserOnError, StringUtils.isBlank(notificationSubject));
		}
	}

	private Boolean isErrorAlertNotificationEnabled(UserInfo userInfo) {
		return Boolean.TRUE.equals(userInfo.getNotificationEmail().get("errorAlertNotification"));
	}

	private Map<String, String> createCustomData(String userName, String toolName, String connectionFixUrl, String helpUrl) {
		Map<String, String> customData = new HashMap<>();
		customData.put(NotificationCustomDataEnum.USER_NAME.getValue(), userName);
		customData.put(NotificationCustomDataEnum.TOOL_NAME.getValue(), toolName);
		customData.put(NotificationCustomDataEnum.FIX_URL.getValue(), connectionFixUrl);
		customData.put(NotificationCustomDataEnum.HELP_URL.getValue(), helpUrl);
		return customData;
	}
	/**
	 * @param connection
	 *          connection
	 */
	public void validateConnectionFlag(Connection connection) {
		if (connection.isBrokenConnection()) {
			updateBreakingConnection(connection, null);
		}
	}
}
