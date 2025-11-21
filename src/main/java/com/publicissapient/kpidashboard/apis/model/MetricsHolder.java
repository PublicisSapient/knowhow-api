package com.publicissapient.kpidashboard.apis.model;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Data
@NoArgsConstructor
public class MetricsHolder {
	private long totalRevertPRs = 0;
	private long totalMerges = 0;
	private int reworkChanges = 0;
	private int totalChanges = 0;

	// Getter for totalRevertPRs
	public long getTotalRevertPRs() {
		return totalRevertPRs;
	}

	// Setter for totalRevertPRs
	public void addTotalRevertPRs(long totalRevertPRs) {
		this.totalRevertPRs = totalRevertPRs;
	}

	// Getter for totalMerges
	public long getTotalMerges() {
		return totalMerges;
	}

	// Setter for totalMerges
	public void addTotalMerges(long totalMerges) {
		this.totalMerges += totalMerges;
	}

	/**
	 * Calculates the rework percentage based on totalRevertPRs and totalMerges. Returns 0.0 if
	 * totalMerges is zero to avoid division by zero.
	 */
	public Double calculateRevertPercentage() {
		if (totalMerges == 0) {
			return 0.0;
		}
		return ((double) totalRevertPRs / totalMerges) * 100;
	}

	public void addTotalChanges(int changes) {
		this.totalChanges += changes;
	}

	public void addRework(int rework) {
		this.reworkChanges += rework;
	}

	public Double getReworkPercentage() {
		if (totalChanges == 0) {
			return 0.0;
		}

		return ((double) reworkChanges / totalChanges) * 100;
	}

	public int getTotalChanges() {
		return this.totalChanges;
	}

	public int getReworkChanges() {
		return this.reworkChanges;
	}
}
