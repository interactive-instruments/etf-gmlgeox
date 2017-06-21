package nl.vrom.roo.util;

import org.dom4j.dom.DOMNodeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;





/**
 * Utility class needed to use in schematron files and style sheets while using regular expressions. This because XSLT-1
 * does not support regular expressions.
 *
 * @author rdool
 *
 */
public final class Regexp {

	private static final Logger LOGGER = LoggerFactory.getLogger(Regexp.class);

	private static final Map<String, Pattern> PATTERN_MAP = new HashMap<String, Pattern>();

	private Regexp() {
		// Not allowed to instantiate
	}

	private static Pattern getPattern(String regex) {

		synchronized(PATTERN_MAP) {
			// Caching of previously used pattern
			Pattern pattern = PATTERN_MAP.get(regex);
	
			if (pattern == null) {
				pattern = Pattern.compile(regex);
				PATTERN_MAP.put(regex, pattern);
			}
			return pattern;
		}
	}

	private static String getTextFromNode(Node node) {

		Node testNode = node;
		if (testNode.getNodeType() == Node.ELEMENT_NODE) {
			testNode = testNode.getFirstChild();
		}

		if (testNode == null) {
			// This is an empty node: <node/>
			return null;
		}

		String text = null;

		if (testNode.getNodeType() == Node.TEXT_NODE || testNode.getNodeType() == Node.ATTRIBUTE_NODE) {
			text = testNode.getNodeValue();
		}

		return text;
	}

	/**
	 * Matches the value of the node against the regular expression
	 *
	 * @param node
	 *            the node which value needs to be checked
	 * @param regex
	 *            the regular expression needed to check
	 * @return true if the node value is compliant with the regular expression.
	 */
	public static boolean matches(Node node, String regex) {

		if (node == null) {
			// Unexpected.
			LOGGER.error("Null node encountered while evaluating regular expression " + regex);
			return false;
		}

		String text = getTextFromNode(node);

		return matches(text, regex);
	}

	/**
	 * Matches text against the regular expression
	 *
	 * @param text
	 *            the text which needs to be checked
	 * @param regex
	 *            the regular expression needed to check
	 * @return true if the node value is compliant with the regular expression.
	 */
	public static boolean matches(String text, String regex) {

		if (regex == null) {
			LOGGER.error("Regular expression equal to null found.");
			return false;
		}

		if (text == null) {
			return false;
		}

		return getPattern(regex).matcher(text).matches();
	}

	/**
	 * Matches the value of the node against the regular expression after tokenizing with another regular expression
	 *
	 * @param node
	 *            the node which value needs to be checked
	 * @param regex
	 *            the regular expression needed to check
	 * @param delimiterRegexp
	 *            the regular expression needed for tokenization
	 * @return true if the node value is compliant with the regular expression.
	 */
	public static boolean matchesAfterTokenize(Node node, String regex, String delimiterRegexp) {

		if (node == null) {
			// Unexpected.
			LOGGER.error("Null node encountered while evaluating regular expression " + regex
					+ " and tokenizing with delimiter regexp " + delimiterRegexp);
			return false;
		}

		String text = getTextFromNode(node);

		return matchesAfterTokenize(text, regex, delimiterRegexp);
	}

	/**
	 * Matches text against the regular expression after tokenizing with another regular expression
	 *
	 * @param text
	 *            the text that needs to be checked
	 * @param regex
	 *            the regular expression needed to check
	 * @param delimiterRegexp
	 *            the regular expression needed for tokenization
	 * @return true if the node value is compliant with the regular expression.
	 */
	public static boolean matchesAfterTokenize(String text, String regex, String delimiterRegexp) {

		if (delimiterRegexp == null) {
			LOGGER.error("Regular expression of delimiter equal to null found.");
			return false;
		}

		if (regex == null) {
			LOGGER.error("Regular expression equal to null found.");
			return false;
		}

		if (text == null) {
			return false;
		}

		String[] parts = getPattern(delimiterRegexp).split(text);

		for (String piece : parts) {

			if (!getPattern(regex).matcher(piece).matches()) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Count tokens separated by a regexp in a string
	 *
	 * @param text
	 *            the text of which to count the tokens
	 * @param regex
	 *            the regexp that correspond to the separator
	 * @return the count of tokens
	 *
	 */
	public static int tokenCount(String text, String regex) {

		if (regex == null) {
			LOGGER.error("Regular expression equal to null found.");
			return 0;
		}

		if (text == null) {
			return 0;
		}

		String[] parts = getPattern(regex).split(text);

		if (parts.length > 1) {
			return parts.length;
		} else if (parts[0].length() > 0) {
			return 1;
		} else {
			return 0;
		}
	}


	/**
	 * Joins strings contained in a list of nodes. The joined strings are separated with a
	 * delimiter character. This method corresponds of the XPath-2.0 function string-join.
	 *
	 * @param list the list of nodes to join the texts from.
	 * @param delimiter the delimiter character
	 * @return a string containing a concatenation of all texts of the nodes.
	 */
	public static String stringJoin(NodeList list, String delimiter) {

		StringBuffer buf = new StringBuffer();

		for(int index=0; index< list.getLength(); index++) {
			String piece = getTextFromNode(list.item(index));
			if(piece!=null) {
    			if(index!=0 && delimiter!=null) {
    				buf.append(delimiter);
    			}
    			buf.append(piece);
			}
		}

		return buf.toString();
	}


	/**
	 * Joins strings contained in a list of nodes.
	 *
	 * @param list the list of nodes to join the texts from.
	 * @return a string containing a concatenation of all texts of the nodes.
	 */
	public static String stringJoin(NodeList list) {
		return stringJoin(list,null);
	}

	/**
	 * Represents the XPath-2.0 function tokenize(string,regexp).
	 * Prevent the use of this XPath-2.0 function when there are enormous lists of data
	 * to process. This because of the large memory consumption and the bad garbage collection
	 * in XSLTC.
	 * @param text The text to split
	 * @param regex the regular expression to use for splitting the text
	 * @return list of nodes containing the split text fragments
	 */
	public static NodeList tokenize(String text, String regex) {

		if (text == null) {
			return DOMNodeHelper.EMPTY_NODE_LIST;
		}

		String[] parts = getPattern(regex).split(text);

		return createTextNodeList(parts);
	}


	private static NodeList createTextNodeList(String[] list) {

		Document doc = getResultDocument();
		if(doc==null) {
			// Should not happen.
			return DOMNodeHelper.EMPTY_NODE_LIST;
		}

		synchronized(doc) {
    		Element tokens = doc.createElement("Tokens");

    		for(String part : list) {
    			Element newChild = doc.createElement("token");
    			tokens.appendChild(newChild);

    			newChild.appendChild(doc.createTextNode(part));
    		}
    		return tokens.getChildNodes();
		}
	}

	// Every thread using this class owns its own result document.
	// After the thread terminates this document is cleaned up by garbage collection.
	// This trick ensures that there is no pileup of memory used by NodeSets.
	private static ThreadLocal<Document> resultDocument;


	private static synchronized Document getResultDocument() {	// NOPMD Deliberately done at method level

		if(resultDocument==null) {

    			resultDocument = new ThreadLocal<Document>() {

					/* (non-Javadoc)
					 * @see java.lang.ThreadLocal#initialValue()
					 */
					@Override
				protected Document initialValue() {
						try {
							return DocumentBuilderFactory.newInstance().
										newDocumentBuilder().newDocument();
    		    		} catch (ParserConfigurationException e) {
    		    			// Should not happen.
    		    			return null;
    		    		}
					}
    			};
		}
		return resultDocument.get();
	}
}
