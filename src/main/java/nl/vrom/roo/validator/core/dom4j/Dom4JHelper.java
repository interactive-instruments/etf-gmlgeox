package nl.vrom.roo.validator.core.dom4j;

import org.dom4j.*;
import org.dom4j.io.DOMWriter;
import org.dom4j.io.SAXReader;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.Reader;

public final class Dom4JHelper {

	private static final DocumentBuilderFactory DB_FACTORY;
	private static final DocumentFactory D4JD_FACTORY;

	static {
		DB_FACTORY = DocumentBuilderFactory.newInstance();
		DB_FACTORY.setNamespaceAware(true);
		D4JD_FACTORY = org.dom4j.DocumentFactory.getInstance();
	}

	private Dom4JHelper() {
		// Don't instantiate this
	}

	public static void read(Reader reader,
			final ElementHandler... elementHandlers) throws DocumentException {
		if (elementHandlers == null || elementHandlers.length == 0) {
			throw new IllegalStateException(
					"No elementhandlers defined for Dom4JValidation");
		}

		final SAXReader saxReader = new SAXReader();

		saxReader.setDefaultHandler(new ElementHandler() {

			public void onEnd(ElementPath elementPath) {

				for (ElementHandler handler : elementHandlers) {
					handler.onEnd(elementPath);
				}
			}

			public void onStart(ElementPath elementPath) {

				for (ElementHandler handler : elementHandlers) {
					handler.onStart(elementPath);
				}
			}

		});

		saxReader.read(reader);

	}

	public static org.w3c.dom.Element toW3CElement(org.dom4j.Element d4element)
			throws DocumentException {
		org.dom4j.Document d4doc = D4JD_FACTORY.createDocument();
		d4doc.setRootElement(d4element.createCopy());
		return new DOMWriter().write(d4doc).getDocumentElement();
	}

	public static String findGmlId(Element current) {

		Element searchElement = findPlanObject(current);
		if (searchElement == null) {
			return null;
		} else {
			return searchElement.attributeValue("id");
		}
	}

	public static String findCurrentSrsName(Element currentElement) {

		Element searchElement = currentElement;

		while (searchElement != null) {
			Attribute att = searchElement.attribute("srsName");
			if (att == null) {
				searchElement = searchElement.getParent();
			} else {
				return att.getValue();
			}
		}
		return null;
	}

	public static String findPlanOnderdeel(Element current) {

		Element searchElement = findPlanObject(current);
		if (searchElement == null) {
			return null;
		} else {
			return searchElement.getName();
		}
	}

	/**
	 * Searches for the nearest object that has an 'id' attribute. That is
	 * either current itself or one of its parents.
	 * 
	 * @param current
	 * @return the nearest element with an 'id' attribute, or <code>null</code>
	 *         if no such element was found.
	 */
	public static Element findPlanObject(Element current) {

		if (current == null) {
			return null;
		}

		Element searchElement = current;

		String gmlId = searchElement.attributeValue("id");

		if (gmlId == null) {
			return findPlanObject(searchElement.getParent());
		} else {
			return searchElement;
		}
	}

	public static String getNodeFromPath(String path) { // NOPMD Suppress PMD
														// bug, this is not an
														// empty method
		return path.substring(path.lastIndexOf('/') + 1).trim();
	}

	public static String getParentPath(String path) {// NOPMD Suppress PMD bug,
														// this is not an empty
														// method
		int index = path.lastIndexOf('/');
		if (index > 0) {
			return path.substring(0, index);
		} else {
			return "";
		}
	}

}
