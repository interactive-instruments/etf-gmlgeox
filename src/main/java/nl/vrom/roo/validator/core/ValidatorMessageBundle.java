package nl.vrom.roo.validator.core;

import nl.vrom.roo.core.util.MessageBundle;
import nl.vrom.roo.core.util.MessageBundleRegistration;

import java.util.Locale;

public final class ValidatorMessageBundle {

	private static final String RESOURCE_BUNDLE_BASENAME = "validator-core-messages";

	// private static final Locale RESOURCE_BUNDLE_LOCALE = new Locale("nl", "NL");

	private static final Locale RESOURCE_BUNDLE_LOCALE = Locale.ENGLISH;

	static {
		MessageBundleRegistration.addResourceBundle(RESOURCE_BUNDLE_BASENAME, RESOURCE_BUNDLE_LOCALE);
	}
	
	private ValidatorMessageBundle() {
		// no instantiation allowed
	}

	public static String getMessage(String key) {
		return getMessage(key, (Object[]) null);
	}

	public static String getMessage(String key, Object... args) {
		return MessageBundle.getMessage(key, args);
	}

}
