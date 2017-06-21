package nl.vrom.roo.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Extension off the default {@link Properties} to be able to resolve variables between <code>${}</code>
 *
 * How to use: Put properties into this class in the same way as the default {@link Properties} class. After this call
 * method {@link #resolveVariables()}. The result tells whether all used variables have been resolved. If not, call
 * method {@link #getKeysWithUnresolvedVariables()} to retrieve the keys containing unresolved variables.
 *
 * @author rdool
 *
 */
public class AdvancedProperties extends Properties {

	private final Logger logger = LoggerFactory.getLogger(AdvancedProperties.class);

	private static final long serialVersionUID = 1L;

	private Set<String> unresolvedVariables; // NOPMD self describing variable name
	private Set<String> unresolvedVariableKeys; // NOPMD self describing variable name

	public static void resolve(Properties properties) throws IllegalArgumentException {

		AdvancedProperties props = new AdvancedProperties();
		props.putAll(properties);
		props.resolveVariables();

		properties.putAll(props);
	}

	/**
	 * Constructor
	 */
	public AdvancedProperties() {
		unresolvedVariables = new HashSet<String>();
		unresolvedVariableKeys = new HashSet<String>();
	}

	/**
	 * @param defaults
	 */
	public AdvancedProperties(Properties defaults) {
		super(defaults);
		unresolvedVariables = new HashSet<String>();
		unresolvedVariableKeys = new HashSet<String>();
	}

	public void setProperties(Properties props) {
		putAll(props);
		resolveVariables();
	}

	public void setPropertiesArray(Properties[] propsArray) {

		for (Properties props : propsArray) {
			putAll(props);
		}
		resolveVariables();
	}

	public Set<String> getKeysWithUnresolvedVariables() {
		return unresolvedVariableKeys;
	}

	public void resolveVariables() throws IllegalArgumentException {

		Properties backup = new Properties();
		backup.putAll(this);

		unresolvedVariables.clear(); // NOPMD self describing variable name
		unresolvedVariableKeys.clear();

		Map<String, Integer> resolvingCounters = new HashMap<String, Integer>();

		boolean anythingResolved = true;

		while (anythingResolved) {

			anythingResolved = false;

			for (Object keyObj : this.keySet()) {
				String key = ((String) keyObj).trim();

				if (!resolvingCounters.containsKey(key)) {
					resolvingCounters.put(key, 0);
				}

				String variableToResolve = null;

				while ((variableToResolve = findVariableToResolve(key, 0)) != null) {

					String value = this.getProperty(key);

					if (resolveVariable(key, value, variableToResolve)) {

						int resolvingCounter = resolvingCounters.get(key);
						if (resolvingCounter > 10) {
							// Possible endless recursion. Break this.
							logger.error("Possible recursion: key=" + key + ", variable=" + variableToResolve);
							unresolvedVariables.add(variableToResolve);
							anythingResolved = false;
							break;
						}

						resolvingCounters.put(key, resolvingCounter + 1);

						anythingResolved = true;
					}
				}

				String value = getProperty(key);

				if (value == null || containsVariable(value)) {
					unresolvedVariableKeys.add(key);
				} else {
					unresolvedVariableKeys.remove(key);
				}
			}
		}

		for (String variable : unresolvedVariables) {
			logger.error("Unresolved variable : " + variable);
		}

		for (String key : unresolvedVariableKeys) {
			logger.error("Could not resolve variables for key:  " + key + ", value=" + backup.get(key));
		}

		if (!unresolvedVariableKeys.isEmpty()) {
			throw new IllegalArgumentException("Keys with unresolved variables: " + unresolvedVariableKeys);
		}
	}

	private boolean resolveVariable(String key, String value, String variableToResolve) { // NOPMD self describing
		// variable name

		boolean variableResolved = false;
		String valueWithVariables = value; // NOPMD self describing variable name
		String unwrappedKey = unwrapKey(variableToResolve);

		String variable = this.getProperty(unwrappedKey);
		if (variable == null) {
			variable = System.getProperty(unwrappedKey);
		}
		if (variable == null) {
			variable = System.getenv(unwrappedKey);
		}

		if (variable == null) {
			unresolvedVariables.add(variableToResolve);
		} else {
			variableResolved = true;
			valueWithVariables = valueWithVariables.replace(variableToResolve, variable);

			this.setProperty(key, valueWithVariables);

			if (!containsVariable(valueWithVariables)) {
				unresolvedVariables.remove(variableToResolve);
			}

		}

		return variableResolved;
	}

	private String unwrapKey(String resolveVariable) {

		if (resolveVariable.startsWith("${") && resolveVariable.endsWith("}")) {

			return resolveVariable.substring(2, resolveVariable.length() - 1).trim();
		} else {
			return resolveVariable;
		}
	}

	private boolean containsVariable(String value) {
		return containsVariable(value, 0);
	}

	private boolean containsVariable(String value, int startIndex) {

		if (value == null) {
			return false;
		}

		if (startIndex > value.length()) {
			return false;
		}

		int index = value.indexOf("${", startIndex);

		if (index >= 0) {
			int end = value.indexOf("}", index);

			if (end >= 0) {
				return true;
			}
		}

		return false;
	}

	private String findVariableToResolve(String key, int startIndex) {

		String value = this.getProperty(key);

		String result = null;

		if (containsVariable(value, startIndex)) {

			int index = value.indexOf("${", startIndex);
			int end = value.indexOf("}", index);

			result = value.substring(index, end + 1);

			if (unresolvedVariables.contains(result)) {
				return findVariableToResolve(value, end + 1);
			}
		}

		return result;
	}

}
