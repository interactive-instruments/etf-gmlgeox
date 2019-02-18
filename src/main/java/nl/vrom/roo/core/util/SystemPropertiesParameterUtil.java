package nl.vrom.roo.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

public class SystemPropertiesParameterUtil {

	static final Logger LOGGER = LoggerFactory.getLogger(SystemPropertiesParameterUtil.class);
	
	private static final String PROPKEY_SYSTEM = "system.properties.prop";
	
	public static void copySystemProperties(Map<String, Object> parameters, Properties properties) {

		// Possibility to configure temporary system properties for validations. These properties can only be used while in initializing phase. 
    	for(int tempSystemPropsCount = 1; properties.get(PROPKEY_SYSTEM+tempSystemPropsCount)!=null; tempSystemPropsCount++) {
    		parameters.put(PROPKEY_SYSTEM+tempSystemPropsCount, properties.get(PROPKEY_SYSTEM+tempSystemPropsCount));
    	}
	}
	
	
	public static void copySystemProperties(Map<String, Object> targetParameters, Map<String, Object> sourceParameters) {

		// Possibility to configure temporary system properties for validations. These properties can only be used while in initializing phase. 
    	for(int tempSystemPropsCount = 1; sourceParameters.get(PROPKEY_SYSTEM+tempSystemPropsCount)!=null; tempSystemPropsCount++) {
    		targetParameters.put(PROPKEY_SYSTEM+tempSystemPropsCount, sourceParameters.get(PROPKEY_SYSTEM+tempSystemPropsCount));
    	}
	}
	
	private static String prepareParam(String property) {
		String result = property;
		if (property != null && System.getProperty("CONFIGDIR") != null) {
			result = property.replace("${CONFIGDIR}", System.getProperty("CONFIGDIR"));
		}
		return result;
	}

	
	public static void setTempSystemProperties(Properties props) {

		LOGGER.debug("Set temporary system properties");
		
		for(Entry<Object, Object> property : props.entrySet()) {
			String key = (String) property.getKey();
			String value = (String) property.getValue();

			LOGGER.debug("System.setProperty(\"{}\",\"{}\")", key, value);
			System.setProperty(key, value);
		}
	}
	
	public static void clearTempSystemProperties(Properties props) {
		
		LOGGER.debug("Clear temporary system properties");
		
		for(Object property : props.keySet()) {
			
			LOGGER.debug("System.clearProperty(\"{}\")", property);
			System.clearProperty((String) property);
		}
	}
	
	
	public static Properties getSystemProperties(Map<String, Object> sourceParameters) {
		
		Properties sysProps = new Properties();
		
    	for(int tempSystemPropsCount = 1; sourceParameters.get(PROPKEY_SYSTEM+tempSystemPropsCount)!=null; tempSystemPropsCount++) {
    		String sysPropDef = prepareParam((String) sourceParameters.get(PROPKEY_SYSTEM+tempSystemPropsCount));
    		String[] parts = sysPropDef.split("=");
    		
    		sysProps.put(parts[0], parts[1]);
    	}
    	
    	return sysProps;
	}
	
}
