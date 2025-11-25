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

package com.publicissapient.kpidashboard.apis.filter.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class OrganizationLookup {

	private final Map<String, List<AccountFilteredData>> byNodeId;
	private final Map<String, List<AccountFilteredData>> byParentId;

	private final Map<Integer, List<AccountFilteredData>> byLevel;

	public OrganizationLookup(Set<AccountFilteredData> flatAccountFilteredData) {
		Objects.requireNonNull(flatAccountFilteredData, "The account filtered data cannot be null");

		this.byNodeId = new HashMap<>();
		this.byLevel = new HashMap<>();
		this.byParentId = new HashMap<>();

		for (AccountFilteredData accountFilteredData : flatAccountFilteredData) {
			if (Objects.nonNull(accountFilteredData) && StringUtils.isNotBlank(accountFilteredData.getNodeId())) {
				this.byNodeId.putIfAbsent(accountFilteredData.getNodeId(), new ArrayList<>());
				this.byNodeId.get(accountFilteredData.getNodeId()).add(accountFilteredData);

				this.byLevel.putIfAbsent(accountFilteredData.getLevel(), new ArrayList<>());
				this.byLevel.get(accountFilteredData.getLevel()).add(accountFilteredData);

				if (StringUtils.isNotBlank(accountFilteredData.getParentId())) {
					this.byParentId.putIfAbsent(accountFilteredData.getParentId(), new ArrayList<>());
					this.byParentId.get(accountFilteredData.getParentId()).add(accountFilteredData);
				}
			}
		}
	}

	public List<AccountFilteredData> getAccountDataByLevel(int level) {
		return this.byLevel.getOrDefault(level, List.of());
	}

	public List<AccountFilteredData> getAccountDataByNodeId(String nodeId) {
		return this.byNodeId.get(nodeId);
	}

	public List<AccountFilteredData> getChildrenByParentNodeId(String parentNodeId, int childLevel) {
		List<AccountFilteredData> parents = this.byNodeId.get(parentNodeId);
		if (CollectionUtils.isEmpty(parents)) {
			return Collections.emptyList();
		}
		List<AccountFilteredData> children = new ArrayList<>();
		for (AccountFilteredData parent : parents) {
			children.addAll(findDescendantsByLevel(parent, childLevel));
		}
		return children;
	}

	public Map<String, List<AccountFilteredData>> getChildrenGroupedByParentNodeIds(Set<String> parentNodeIds,
			int childLevel) {
		List<AccountFilteredData> parents = parentNodeIds.stream()
				.flatMap(parentNodeId -> this.byNodeId.get(parentNodeId).stream()).toList();
		Map<String, List<AccountFilteredData>> childrenGroupedByParents = new HashMap<>();
		for (AccountFilteredData parent : parents) {
			childrenGroupedByParents.put(parent.getNodeId(), findDescendantsByLevel(parent, childLevel));
		}
		return childrenGroupedByParents;
	}

	public Map<String, List<AccountFilteredData>> getChildrenGroupedByParentNodeIds(int parentLevel, int childLevel) {
		if (childLevel < parentLevel) {
			throw new IllegalArgumentException(
					String.format("The child level must be higher than the parent level. Received child "
							+ "level: %s, received parent level %s", childLevel, parentLevel));
		}
		List<AccountFilteredData> parents = getAccountDataByLevel(parentLevel);
		Map<String, List<AccountFilteredData>> childrenGroupedByParents = new HashMap<>();
		for (AccountFilteredData parent : parents) {
			childrenGroupedByParents.put(parent.getNodeId(), findDescendantsByLevel(parent, childLevel));
		}
		return childrenGroupedByParents;
	}

	private List<AccountFilteredData> findDescendantsByLevel(AccountFilteredData currentNode, int targetLevel) {
		List<AccountFilteredData> results = new ArrayList<>();
		if (currentNode.getLevel() == targetLevel) {
			results.add(currentNode);
			return results;
		}
		List<AccountFilteredData> children = this.byParentId.getOrDefault(currentNode.getNodeId(), List.of());

		children.forEach(child -> results.addAll(findDescendantsByLevel(child, targetLevel)));

		return results;
	}
}
