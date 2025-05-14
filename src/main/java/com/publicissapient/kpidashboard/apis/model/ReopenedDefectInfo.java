package com.publicissapient.kpidashboard.apis.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.joda.time.DateTime;

/**
 * This class is used to store the information about the defect that was
 * reopened.
 */
@Data
@AllArgsConstructor
public class ReopenedDefectInfo {
	private DateTime closedDate;
	private DateTime reopenDate;
	private double reopenDuration;
}
