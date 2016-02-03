package nl.vrom.roo.validator.core.validation;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import nl.vrom.roo.validator.core.ValidatorContext;
import nl.vrom.roo.validator.core.dom4j.Dom4JHelper;
import nl.vrom.roo.validator.core.exception.ValidationException;

import org.dom4j.DocumentException;
import org.dom4j.ElementHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

public abstract class AbstractDom4JValidation extends AbstractValidation {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	
	protected void read(Reader reader, ElementHandler... elementHandlers) throws ValidationException {
		try {
			Dom4JHelper.read(reader, elementHandlers);
		}
		catch(DocumentException e) {
			logger.error("DocumentException ocured: {}", e);
			throw new ValidationException(e);
		}
	}

	public String getNodeFromPath(String path) {
		return Dom4JHelper.getNodeFromPath(path);
	}

	public Element toW3CElement(org.dom4j.Element d4element) throws DocumentException {
		return Dom4JHelper.toW3CElement(d4element);
	}
	
	
	private <T extends ElementHandler> T instantiateHandler(Class<T> clazz) {
		try {
			return (T) clazz.newInstance();
		} catch (InstantiationException e) {	// NOPMD This will never happen
			// Ignore. This will never happen
			return null;
		} catch (IllegalAccessException e) {	// NOPMD This will never happen
			// Ignore. This will never happen
			return null;
		}
	}
	
	protected void readUsingHandlersAndBuffer(ValidatorContext validatorContext, Reader reader, Class<? extends ElementHandler>... handlerClasses) throws ValidationException {
		
		List<ElementHandler> elementHandlersToUse = new ArrayList<ElementHandler>();
		
		for(Class<? extends ElementHandler> elementHandlerClass : handlerClasses) {
			if(retrieveHandlerFromContext(validatorContext, elementHandlerClass)==null) {
				elementHandlersToUse.add(instantiateHandler(elementHandlerClass));
			}
		}
		
		if(elementHandlersToUse.size()==0) {
			return;
		}
		
		for(ElementHandler handler : elementHandlersToUse) {
			validatorContext.setParameter(handler.getClass().getName(), handler);
		}
		
		read(reader, elementHandlersToUse.toArray(new ElementHandler[0]));
	}
	
}
