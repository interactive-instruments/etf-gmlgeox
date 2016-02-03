package nl.vrom.roo.validator.core.reporting;

import java.util.Map;

import nl.vrom.roo.validator.core.reporting.exception.ReportingFactoryException;

/**
 * Factory to instantiate reportings. The intention of this factory is to make is possible to be able to configure all
 * Reportings within a Validator
 *
 * @author rdool
 * @version 1.0.0 Date: 13 Mar 2008
 */
public final class ReportingFactory {

	/**
	 * Default constructor (private, so ReportingFactory can not be instantiated)
	 */
	private ReportingFactory() {
		// Don't allow instantiating ReportingFactory
	}

	/**
	 * Creates a new instance of a Reporting based on the given parameters
	 *
	 * @param name
	 *            the name of the reporting
	 * @param className
	 *            the class name of the reporting
	 * @param parameters
	 *            the parameters used to configure this reporting
	 * @return an implementation of a AbstractReporting subclass based on the className and parameters
	 * @throws ReportingFactoryException
	 *             when the instance could not be created
	 */
	public static AbstractReporting newInstance(String reportingId, String name, String className,
			Map<String, Object> parameters) throws ReportingFactoryException {
		AbstractReporting result = null;
		try {
			Class<?> clazz = Class.forName(className);
			result = (AbstractReporting) clazz.newInstance();
			result.setId(reportingId);
			result.setName(name);
			result.setParameters(parameters);
			result.initializeTask();
		} catch (ClassNotFoundException e) {
			throw new ReportingFactoryException("Could not find class for className: " + className, e);
		} catch (InstantiationException e) {
			throw new ReportingFactoryException("Could not instantiate class for className: " + className, e);
		} catch (IllegalAccessException e) {
			throw new ReportingFactoryException("Could not access class for className: " + className, e);
		} catch (IllegalArgumentException e) {
			throw new ReportingFactoryException(e.getMessage(), e);
		}
		return result;
	}

	/**
	 * Creates a new instance of a Reporting based on the given parameters
	 *
	 * @param name
	 *            the name of the reporting
	 * @param className
	 *            the class name of the reporting
	 * @return an implementation of a AbstractReporting subclass based on the className
	 * @throws ReportingFactoryException
	 *             when the instance could not be created
	 */
	public static AbstractReporting newInstance(String reportingId, String name, String className)
			throws ReportingFactoryException {
		return ReportingFactory.newInstance(reportingId, name, className, null);
	}

}
