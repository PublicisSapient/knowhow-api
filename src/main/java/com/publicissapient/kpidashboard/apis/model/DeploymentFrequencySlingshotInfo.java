package com.publicissapient.kpidashboard.apis.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * Holds the row-wise data used to build the Excel export for the Deployment Frequency (Speed) KPI.
 */
@Data
public class DeploymentFrequencySlingshotInfo {

	private List<String> jobNameList;
	private List<String> environmentList;
	private List<String> deploymentDateList;
	private List<String> deploymentEndDateList;
	private List<String> weeksList;
	private List<String> repoNameList;
	private List<String> deploymentStatusList;

	public DeploymentFrequencySlingshotInfo() {
		this.jobNameList = new ArrayList<>();
		this.environmentList = new ArrayList<>();
		this.deploymentDateList = new ArrayList<>();
		this.deploymentEndDateList = new ArrayList<>();
		this.weeksList = new ArrayList<>();
		this.repoNameList = new ArrayList<>();
		this.deploymentStatusList = new ArrayList<>();
	}

	public void addJobName(String jobName) {
		jobNameList.add(jobName);
	}

	public void addEnvironment(String environment) {
		environmentList.add(environment);
	}

	public void addDeploymentDate(String date) {
		deploymentDateList.add(date);
	}

	public void addDeploymentEndDate(String date) {
		deploymentEndDateList.add(date);
	}

	public void addWeeks(String weeks) {
		weeksList.add(weeks);
	}

	public void addRepoName(String repoName) {
		repoNameList.add(repoName);
	}

	public void addDeploymentStatus(String status) {
		deploymentStatusList.add(status);
	}
}
