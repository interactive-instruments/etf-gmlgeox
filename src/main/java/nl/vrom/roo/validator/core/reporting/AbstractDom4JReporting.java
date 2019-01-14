package nl.vrom.roo.validator.core.reporting;

import nl.vrom.roo.validator.core.dom4j.Dom4JHelper;
import org.dom4j.DocumentException;
import org.dom4j.ElementHandler;
import org.w3c.dom.Element;

import java.io.Reader;

public abstract class AbstractDom4JReporting extends AbstractReporting {

	protected void read(Reader reader, ElementHandler... elementHandlers) throws DocumentException {
		Dom4JHelper.read(reader, elementHandlers);
	}

	public String getNodeFromPath(String path) {
		return Dom4JHelper.getNodeFromPath(path);
	}

	public Element toW3CElement(org.dom4j.Element d4element) throws DocumentException {
		return Dom4JHelper.toW3CElement(d4element);
	}

}
