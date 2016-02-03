package nl.vrom.roo.core.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;


/**
 * This MessageBundle class is accessible from anywhere and
 * contains all registered message bundles.
 *
 * Message bundles need to be registered using method {@link #registerBundle(ResourceBundle)}.
 *
 * @author rdool
 *  *copied from nl.vrom.roo.core.util
 */
public final class MessageResourceBundle {

	private static Map<Locale,MessageResourceBundle> languageBundleMap;

	private final ResourceBundle bundle;
	private MessageResourceBundle parent;

	private MessageResourceBundle(ResourceBundle bundle) {
		this.bundle = bundle;
	}

	// public static final Locale DEFAULT_LOCALE = new Locale("nl","NL");
	public static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
	
	private static synchronized Map<Locale,MessageResourceBundle> 
							getMessageResourceBundlePerLocale() {	// NOPMD
		
		if(languageBundleMap==null) {
			languageBundleMap = new LinkedHashMap<Locale, MessageResourceBundle>();
		}
		Map<Locale, MessageResourceBundle> l = languageBundleMap;
		return l;//languageBundleMap;
	}


	/**
	 * @return the startBundle
	 */
	protected static MessageResourceBundle getRegisteredBundleByLocale(Locale locale) {	// NOPMD
		Map<Locale, MessageResourceBundle> bundles =getMessageResourceBundlePerLocale();
		MessageResourceBundle theBundle = bundles.get(locale);


		if(theBundle==null && !Locale.ROOT.equals(locale) ) {
			theBundle = getMessageResourceBundlePerLocale().get(Locale.ROOT);
		}

		if(theBundle==null && !DEFAULT_LOCALE.equals(locale)) {
			theBundle = getMessageResourceBundlePerLocale().get(DEFAULT_LOCALE);
		}
		
		return theBundle;
	}

	/**
	 * @return the startBundle
	 */
	protected static void setFirstRegisteredBundle(MessageResourceBundle bundle, Locale locale) {	// NOPMD

		getMessageResourceBundlePerLocale().put(locale, bundle);
	}




	protected final String getString(String key) {
		
		if(bundle==null) {
			return null;
		}
		
		try {
			return bundle.getString(key);
		}
		catch(MissingResourceException mse) {
			return parent.getString(key);
		}
	}
	
	/* (non-Javadoc)
	 * @see java.util.ResourceBundle#getLocale()
	 */
	public Locale getLocale() {
		return bundle==null ? null : bundle.getLocale();
	}


	private void setParent(MessageResourceBundle parent) {
		this.parent = parent;
	}


	/**
	 * Registers a message bundle
	 * @param bundle the message bundle
	 */
	protected static void registerBundle(ResourceBundle bundle, Locale locale) {
		
		MessageResourceBundle newBundle = new MessageResourceBundle(bundle);
		
		MessageResourceBundle oldRegisteredBundle = getMessageResourceBundlePerLocale().get(locale);
		if(oldRegisteredBundle!=null) {
			newBundle.setParent(oldRegisteredBundle);
		}
		
		setFirstRegisteredBundle(newBundle, locale);
	}



}
