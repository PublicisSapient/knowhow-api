package com.publicissapient.kpidashboard.apis.bitbucket.service.scm;

import com.fasterxml.jackson.databind.JsonNode;

public interface ScmUserService {
    JsonNode getScmToolUsersMailList(String projectConfigId);
}
