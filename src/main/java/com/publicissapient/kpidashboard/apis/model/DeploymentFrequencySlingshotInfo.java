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
	private List<String> weeksList;

	public DeploymentFrequencySlingshotInfo() {
		this.jobNameList = new ArrayList<>();
		this.environmentList = new ArrayList<>();
		this.deploymentDateList = new ArrayList<>();
		this.weeksList = new ArrayList<>();
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

	public void addWeeks(String weeks) {
		weeksList.add(weeks);
	}
}
