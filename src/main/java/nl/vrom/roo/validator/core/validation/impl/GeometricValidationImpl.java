package nl.vrom.roo.validator.core.validation.impl;

import java.io.Reader;

import nl.vrom.roo.validator.core.TaskVersion;
import nl.vrom.roo.validator.core.ValidatorContext;
import nl.vrom.roo.validator.core.ValidatorMessageBundle;
import nl.vrom.roo.validator.core.dom4j.handlers.GeometryElementHandler;
import nl.vrom.roo.validator.core.exception.ValidationException;
import nl.vrom.roo.validator.core.validation.AbstractDom4JValidation;



public class GeometricValidationImpl extends AbstractDom4JValidation {

//	private static final Logger logger = LoggerFactory.getLogger(GeometricValidationImpl.class);

//	private static final Object GML_NAMESPACE_PARAM = "gmlNamespace";
//
//	private static final Object GML_NAMESPACE_VALUE = "http://www.opengis.net/gml";

	@Override
	protected void initialize() {


	}

	@Override
	public void validateTemplate(final ValidatorContext validatorContext, Reader reader) throws ValidationException {

		this.read(reader, new GeometryElementHandler(validatorContext, parameters));

		if (validatorContext.isSuccessful(this)) {
			validatorContext.addNotice(ValidatorMessageBundle.getMessage(
					"validator.core.validation.geometry.valid", new Object[] { validatorContext.getInputFile()
							.getName() }), null);
		}
	}

	@Override
	public TaskVersion getTaskVersion() {
		return new TaskVersion("1.7");
	}
}
