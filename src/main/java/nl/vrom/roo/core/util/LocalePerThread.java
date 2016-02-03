package nl.vrom.roo.core.util;

import java.util.Locale;
// *copied from nl.vrom.roo.core.util
public class LocalePerThread {

	private static ThreadLocal<Locale> localeForThread = new ThreadLocal<Locale>();
	
	public static void setThreadLocale(Locale locale) {
		
		boolean localeFound = false;
		for(Locale loc : Locale.getAvailableLocales()) {
			if(loc.equals(locale)) {
				localeFound = true;
			}
		}
		if(localeFound) {
			localeForThread.set(locale);
		}
		else {
			throw new IllegalArgumentException("Setting Thread Locale impossible: Locale unknown: "+locale);
		}
	}
	
	/**
	 * @return the language used in this thread. Default: "nl".
	 */
	public static Locale getThreadLocale() {
		
		return localeForThread.get();
	}
	
	protected static void clearThreadLocale() {
		localeForThread.set(null);
	}
}
