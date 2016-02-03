package nl.vrom.roo.validator.core.util;

/**
 * Class meant for representing three states: TRUE, FALSE, UNDEFINED
 * 
 * @author rdool
 */
public enum Tristate {

	TRUE,
	FALSE,
	UNDEFINED;
		
	public boolean isUndefined() {
		return this==UNDEFINED;
	}
	
	public boolean isTrue() {
		return this==TRUE;
	}
	
	public boolean isFalse() {
		return this==FALSE;
	}
	
	public static final Tristate valueOf(boolean state) {
		return state ? TRUE : FALSE;
	}
}
