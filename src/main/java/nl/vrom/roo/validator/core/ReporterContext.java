package nl.vrom.roo.validator.core;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is contains the reporter context information of the several reportings executed by the reporter.
 *
 * @author rdool
 * @version 1.0.0 Date: 13 Mar 2008
 */

public class ReporterContext extends TaskContext {

	protected String errorInformation;

	protected List<String> reportNamesList;

	/** Output data of the report. */
	protected Map<String, Map<?, ?>> outputDataMap;

	/**
	 * Default constructor.
	 *
	 * @param inputFile for which the current {@linkplain ReporterContext} is valid
	 */
	public ReporterContext(File inputFile) {
		super(inputFile);
	}

	public Map<String, Map<?, ?>> getOutputDataMap() {
		if (outputDataMap == null) {
			outputDataMap = new LinkedHashMap<String, Map<?, ?>>();
		}
		return outputDataMap;
	}

	public Set<String> getReporterNames() {
		return getOutputDataMap().keySet();
	}

	/**
	 * Gets the list of performed validation properties.
	 *
	 * @return the unmodifiable list of performed validation properties
	 */
	public List<TaskValueObject> getReportings() {
		return super.getTaskHistory();
	}

	public void setOutputData(Map<?, ?> outputData) {
		getOutputDataMap().put(getCurrentReporterName(), outputData);
	}

	public String getErrorInformation() {
		return errorInformation;
	}

	public void setErrorInformation(String errorInfo) {
		this.errorInformation = errorInfo;
	}

	private String getCurrentReporterName() {
		return this.getCurrentTaskHandlingName();
	}

	public List<String> getReportNamesList() {
		return reportNamesList;
	}

	public void setReportNamesList(List<String> reportNamesList) {
		this.reportNamesList = reportNamesList;

	}
}
