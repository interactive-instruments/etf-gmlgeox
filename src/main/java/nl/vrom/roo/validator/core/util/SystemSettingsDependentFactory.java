package nl.vrom.roo.validator.core.util;

import nl.vrom.roo.core.util.SystemPropertiesParameterUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public abstract class SystemSettingsDependentFactory<T> {
	
	static final Logger LOGGER = LoggerFactory.getLogger(SystemSettingsDependentFactory.class);
	
	private Properties properties;
	
	public SystemSettingsDependentFactory(Properties properties) {
		this.properties = properties;
	}

	/**
	 * @return the properties
	 */
	public Properties getSystemProperties() {
		return properties;
	}

	
	
	/**
	 * create a factory depending on temporary System properties.
	 * We really don't like this mechanism but there is no other option to force the system to use
	 * one implementation for one application and another one for another application running in the same web container.
	 * Since providers sometimes deliver buggy implementations we need to decide which implementation to choose.
	 * This may not be done system wide (with jaxp.properties) because one implementation is buggy in another way than 
	 * the other. Settings must be adjustable whatever which functionality we need.
	 * 
	 * @return the factory created using System properties dependent settings
	 */
	public T createSystemSettingsDependentFactory() {
		
		try {
			SystemPropertiesParameterUtil.setTempSystemProperties(properties);
			T factory = createFactory();
			LOGGER.info("Factory created: class={}", factory==null ? null : factory.getClass().getName());
    		return factory;
		}
		finally {
			SystemPropertiesParameterUtil.clearTempSystemProperties(properties);
		}
	}

	protected abstract T createFactory();
}

