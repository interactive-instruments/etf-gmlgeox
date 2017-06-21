package nl.vrom.roo.core.util;

import java.util.*;
import java.util.Map.Entry;
// *copied from nl.vrom.roo.core.util
public class MessageBundleRegistration {


	private static Locale findLocaleForLanguage(String language) {
		
		if(language == null) {
			return Locale.ROOT;
		}
		
		for(Locale locale : Locale.getAvailableLocales()) {
			if(locale.getLanguage().equals(language)) {
				return locale;
			}
		}
		
		return new Locale(language);
	}
	
	private static void registerBundle(String resourceBundleName, Locale locale) {
		ResourceBundle bundle = PropertyResourceBundle.getBundle(resourceBundleName, locale);
		MessageResourceBundle.registerBundle(bundle, locale);
	}

	
	
	private static void registerBundle(String resourceBundleName, Locale locale, ClassLoader classLoader) {
		MessageResourceBundle.registerBundle(PropertyResourceBundle.getBundle(resourceBundleName, locale, classLoader), locale);
	}
	
	

	/**
	 * Sets the resource bundles
	 * @param resourceBundleNames the names of the bundles
	 */
	public static void setResourceBundles(List<String> resourceBundleNames) {
		

		for(String resourceBundleName : resourceBundleNames) {
			registerBundle(resourceBundleName, null);
		}
	}

	/**
	 * Sets the resource bundles
	 * @param resourceBundleNames the names of the bundles
	 */
	public static void setResourceBundles(List<String> resourceBundleNames, ClassLoader classLoader) {
		

		for(String resourceBundleName : resourceBundleNames) {
			registerBundle(resourceBundleName, null, classLoader);
		}
	}

	/**
	 * Sets the resource bundles
	 * @param resourceBundlesLocalesMap the names of the bundles
	 */
	public static void setResourceBundles(Map<String,String> resourceBundlesLocalesMap, ClassLoader classLoader) {
		
		for(Entry<String, String> resourceBundleLocaleEntry : resourceBundlesLocalesMap.entrySet()) {
			String resourceBundleName = resourceBundleLocaleEntry.getKey();
			String localeLanguage = resourceBundleLocaleEntry.getValue();
			
			registerBundle(resourceBundleName, findLocaleForLanguage(localeLanguage), classLoader);
		}
	}
	
	/**
	 * Sets the resource bundles
	 * @param resourceBundlesLocalesMap the names of the bundles
	 */
	public static void setResourceBundles(Map<String,String> resourceBundlesLocalesMap) {
		
		for(Entry<String, String> resourceBundleLocaleEntry : resourceBundlesLocalesMap.entrySet()) {
			String resourceBundleName = resourceBundleLocaleEntry.getKey();
			String localeLanguage = resourceBundleLocaleEntry.getValue();
			
			registerBundle(resourceBundleName, findLocaleForLanguage(localeLanguage));
		}
	}

	public static void addResourceBundle(String resourceBundleName) {
		registerBundle(resourceBundleName, Locale.ROOT);
	}

	public static void addResourceBundle(String resourceBundleName, ClassLoader classLoader) {
		registerBundle(resourceBundleName, Locale.ROOT, classLoader);
	}

	
	public static void addResourceBundle(String resourceBundleName, String languageName, ClassLoader classLoader) {
		registerBundle(resourceBundleName, findLocaleForLanguage(languageName), classLoader);
	}

	public static void addResourceBundle(String resourceBundleName, Locale locale) {
		if(locale==null) {
			registerBundle(resourceBundleName, Locale.ROOT);
		}
		else {
			registerBundle(resourceBundleName, locale);
		}
	}

	public static void addResourceBundle(String resourceBundleName, Locale locale, ClassLoader classLoader) {
		if(locale==null) {
			registerBundle(resourceBundleName, Locale.ROOT, classLoader);
		}
		else {
			registerBundle(resourceBundleName, locale, classLoader);
		}
	}

}
