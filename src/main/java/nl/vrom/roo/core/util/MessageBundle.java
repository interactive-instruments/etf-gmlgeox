package nl.vrom.roo.core.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;


/**
 * This is a utility class.
 * It is possible to use registered message bundles but it is also possible to supply
 * own resource bundles.
 *
 * Message bundles can be registered using {@link MessageBundleRegistration#registerBundle(String)}.
 *
 * @author rdool
 *
 *copied from nl.vrom.roo.core.util
 */
public final class MessageBundle {

	private MessageBundle() {	// NOPMD Intentionally left empty
	}

	
	/**
	 * Returns the message from the resource bundles belonging to key
	 * @param key the key
	 * @return the message from one of the registered bundles
	 */
	public static String getMessage(String key) {
		return getMessage(key, (Object) null);
	}

	/**
	 * Returns the message from the resource bundles belonging to key.
	 *
	 * @param key the key
	 * @param args supplied arguments
	 * @return the message from one of the registered bundles
	 */
	public static String getMessage(String key, Object... args) {
		return getMessage(
				MessageResourceBundle.
					getRegisteredBundleByLocale(LocalePerThread.getThreadLocale()), key, args);
	}

	/**
	 * Returns the message from the resource bundle belonging to key.
	 *
	 * @param key the key
	 * @param args supplied arguments
	 * @param bundle resource bundle
	 * @return the message from one of the registered bundles
	 */
	public static String getMessage(MessageResourceBundle bundle, String key, Object... args) {
		if(bundle==null) {
			throw new IllegalArgumentException("No resource bundle found to get message for key: "+key);
		}
		try {
			return formatMessage(bundle.getString(key), bundle.getLocale(), args);
		} catch (MissingResourceException e) {
			return "No resource found for key: " + key;
		}
	}

	
	/**
	 * Returns the message from the resource bundle belonging to key.
	 *
	 * @param key the key
	 * @param args supplied arguments
	 * @param bundle resource bundle
	 * @return the message from one of the registered bundles
	 */
	public static String getMessage(ResourceBundle bundle, String key, Object... args) {
		if(bundle==null) {
			throw new IllegalArgumentException("No resource bundle found to get message for key: "+key);
		}
		try {
			return formatMessage(bundle.getString(key), bundle.getLocale(), args);
		} catch (MissingResourceException e) {
			return "No resource found for key: " + key;
		}
	}

	/**
	 * Returns the formatted message.
	 *
	 * @param message the message
	 * @param args supplied arguments
	 * @param Locale the locale
	 * @return the formatted message
	 */
	public static String formatMessage(String message, Locale locale, Object... args) {
		if (args != null && args.length > 0) {
			MessageFormat mformat = new MessageFormat(message, locale);
			return mformat.format(args);
		}
		return message;
	}

}
