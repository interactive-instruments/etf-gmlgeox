package nl.vrom.roo.validator.core.errorlocation;

public class IdErrorLocation extends ErrorLocation {

	protected String locationId;

	public IdErrorLocation(String locationid) {
		this.locationId = locationid;
	}

	public String getId() {
		return locationId;
	}

}
