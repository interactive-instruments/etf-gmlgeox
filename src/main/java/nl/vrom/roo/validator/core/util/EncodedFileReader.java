package nl.vrom.roo.validator.core.util;

import nl.vrom.roo.validator.core.ValidatorMessageBundle;
import org.apache.xerces.impl.io.UTF8Reader;

import java.io.*;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//import com.sun.org.apache.xerces.internal.impl.io.UTF8Reader;

/**
 * This class is meant for reading XML files and determining the encoding.
 * It signals incompatibilities in file encoding and XML encoding and also verifies
 * the case that UTF=8 encoding is specified (or assumed) and the usage of UTF-8 characters.
 *
 * @author rdool
 *
 */
/**
 * @author rdool
 *
 */
public class EncodedFileReader extends Reader {

	private static final Pattern FIRST_LINE_PATTERN = Pattern
			.compile("^<\\?xml.*encoding( )?\\=( )?(\"[^\"]*\"|'[^']*').*\\?>.*$");
	private static final Pattern ENCODING_DEF_PATTERN = Pattern.compile("encoding( )?\\=( )?(\"[^\"]*\"|'[^']*')");

	private static final String ISO_8859_1 = "ISO-8859-1";
	private static final String UTF_8 = "UTF-8";
	private static final String UTF_16LE = "UTF-16LE";
	private static final String UTF_16BE = "UTF-16BE";
	private static final String UTF_16 = "UTF-16";

	private boolean byteOrderMarkDetected;
	private String detectedFileEncoding;
	private String detectedXMLEncoding;
	private String detectedEncoding;
	private boolean encodingMissing;
	private boolean encodingProblemDetected;
	private boolean xmlEncodingNotSupported;
	private boolean fileEncodingNotSupported;
	private final File inputFile;
	private Reader localReader;
	private EncodingProblemLocation problemLocation;

	private List<String> supportedEncodingsList;

	/**
	 * Constructor
	 * @param inputFile the input file to be read
	 */
	public EncodedFileReader(File inputFile) {
		this.inputFile = inputFile;
	}

	/**
	 * Checks whether the given encoding is supported by this reader
	 * @param encodingSynonym the encoding to check
	 * @return true if supported
	 */
	public boolean isEncodingSupported(String encodingSynonym) {

		// Is the charset supported by the operating system?
		if (!Charset.isSupported(encodingSynonym)) {
			return false;
		}

		return getGenericEncoding(encodingSynonym) != null;
	}

	/**
	 * Get the encoding synonym supported by this reader giving an encoding synonym
	 * @param encodingSynonym the given encoding synonym
	 * @return the synonym supported by this reader
	 */
	public String getGenericEncoding(String encodingSynonym) {

		if (supportedEncodingsList == null) {
			return encodingSynonym;
		}

		if (encodingSynonym == null) {
			return null;
		}

		String encodingToCheck = encodingSynonym.replace('_', '-').toUpperCase(Locale.getDefault());

		return supportedEncodingsList.contains(encodingToCheck) ? encodingToCheck : null;
	}

	/**
	 * Get a comma separated list of all supported encoding synonyms
	 * @return a comma separated list of all supported encoding synonyms
	 */
	public String getSupportedEncodings() {

		// Create a set of analogous encodings
		Set<String> supportedEncodingSet = new TreeSet<String>(supportedEncodingsList);

		String supportedEncodings = null;
		for (String encoding : supportedEncodingSet) {
			if (supportedEncodings == null) {
				supportedEncodings = encoding;
			} else {
				supportedEncodings += ", " + encoding;
			}
		}

		return supportedEncodings;
	}

	/** {@inheritDoc} */
	@Override
	public void close() throws IOException {
		getFileReader().close();
	}

	/** {@inheritDoc} */
	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		return getFileReader().read(cbuf, off, len);
	}


	/**
	 * Gets the detected encoding synonym belonging to the current input file
	 * @return the detected encoding synonym
	 * @throws IOException
	 */
	public String getDetectedEncoding() throws IOException {
		if (detectedEncoding == null) {
			getFileReader();
		}
		return detectedEncoding;
	}

	/**
	 * Get an array of parameters giving information about encoding conflicts
	 * @return an array of parameters giving information about encoding conflicts
	 * @throws IOException
	 */
	public Object[] getEncodingConflictParameters() throws IOException {
		getDetectedEncoding();

		return new Object[] { detectedFileEncoding, detectedXMLEncoding == null ? "" : detectedXMLEncoding,
				getSupportedEncodings() };
	}

	/**
	 * is there any encoding problem detected?
	 * @return true if there is an encoding problem
	 */
	public boolean isEncodingProblemDetected() {
		return encodingProblemDetected;
	}

	// Get a reader suitable for this encoding and skipping byte order marks
	private BufferedReader getEncodedReader(BufferedInputStream inputStream, String encoding) throws IOException {

		Reader inputReader = new InputStreamReader(inputStream, encoding);

		BufferedReader reader = new BufferedReader(inputReader);
		if (byteOrderMarkDetected) {
			// Read the byte order mark
			reader.read();
		}

		return reader;
	}

	private BufferedInputStream getBufferedInputStream() throws FileNotFoundException {

		return new BufferedInputStream(new FileInputStream(inputFile));
	}

	/*
	 * Gets a reader of the input file. This method checks an XML input file according to the encoding prescriptions of
	 * W3C: http://www.w3.org/TR/xml/#charencoding
	 */
	private Reader getFileReader() throws IOException {

		if (detectedEncoding == null) {
			determineFileEncoding();
			determineXMLEncoding();
			determineFinalEncoding();
		}

		if (localReader == null) {
			localReader = getEncodedReader(getBufferedInputStream(), detectedEncoding);
		}
		return localReader;
	}

	// Checks whether determined file encoding and XML encoding are supported
	private boolean isDeterminedEncodingsValid() {

		if (!isEncodingSupported(detectedFileEncoding)) {
			fileEncodingNotSupported = true;
			return false;
		}

		if (detectedXMLEncoding != null && !isEncodingSupported(detectedXMLEncoding)) {
			xmlEncodingNotSupported = true;
			return false;
		}

		return true;
	}

	/**
	 * Determine the concluding encoding of this XML file.
	 * First detect whether the encodings of File and XML belong to the supported encodings.
	 * If it's the case try to match the encodings.
	 * In case of serious problems a flag is set: @see #isEncodingProblemDetected()
	 *
	 * Matching encodings are:
	 * <table>
	 * <th>File encoding</th><th>XML encoding</th><th>Final concluded encoding</th><th>Comments</th>
	 * <dl>
	 * <dd>UTF-8</dd><dd>supported encoding</dd><dd>UTF-8</dd><td>W3C specifies that UTF-8 file encoding rules</td>
	 * </dl>
	 * <dl>
	 * <dd>no encoding</dd><dd>no encoding</dd><dd>UTF-8</dd><td>W3C specifies fatal error. VROM specifies UTF-8</td>
	 * </dl>
	 * <dl>
	 * <dd>no encoding</dd><dd>the supported XML encoding</dd><dd>the supported XML encoding</dd><td>W3C specifies the given XML encoding</td>
	 * </dl>
	 * </table>
	 *
	 * W3C specifications:
	 * <b>
	 * Although an XML processor is required to read only entities in the UTF-8 and UTF-16 encodings, it is
	 * recognized that other encodings are used around the world, and it may be desired for XML processors
	 * to read entities that use them. In the absence of external character encoding information (such as
	 * MIME headers), parsed entities which are stored in an encoding other than UTF-8 or UTF-16 MUST begin
	 * with a text declaration (see 4.3.1 The Text Declaration) containing an encoding declaration:
	 * </b>
	 * <b>
	 * In the absence of information provided by an external transport protocol (e.g. HTTP or MIME), it is a
	 * fatal error for an entity including an encoding declaration to be presented to the XML processor in
	 * an encoding other than that named in the declaration, or for an entity which begins with neither a
	 * Byte Order Mark nor an encoding declaration to use an encoding other than UTF-8. Note that since
	 * ASCII is a subset of UTF-8, ordinary ASCII entities do not strictly need an encoding declaration.
	 * </b>
	 *
	 * @throws IOException
	 */
	private void determineFinalEncoding() throws IOException {

		detectedEncoding = detectedFileEncoding;

		if (!isDeterminedEncodingsValid()) {
			encodingProblemDetected = true;
			return;
		}

		String genericXMLEncoding = getGenericEncoding(detectedXMLEncoding);
		String genericFileEncoding = getGenericEncoding(detectedFileEncoding);
		if (UTF_8.equals(genericFileEncoding)) {

			// ISO-8859-1 and ISO-8859-15 are acceptable encodings (fit in UTF-8).
			// No XML encoding means UTF-8 in this case.
			return;
		} else if (genericFileEncoding.startsWith(UTF_16)) { // NOPMD - Intentionally left empty

			/*
			 * Ignore UTF-16 No XML encoding is an acceptable value. UTF-16 is also an acceptable value. UTF-16BE for
			 * file encoding must match with UTF-16BE (or UTF-16) for XML encoding. Same for UTF-16LE.
			 */
			return;
		} else {
			// file encoding detected ISO-8859-1. Still could be UTF-8 or another encoding.
			if (genericXMLEncoding == null) {
				// Although W3C assumes this an error VROM has decided that when the
				// encoding is unclear it is equal to UTF-8.
				detectedEncoding = UTF_8;
				encodingMissing = true;
			} else if (genericXMLEncoding.startsWith(UTF_16)) {
				// Mismatch
				encodingProblemDetected = true;
			} else {
				detectedEncoding = genericXMLEncoding;
			}
		}
	}

	// Determine the encoding given in the first XML row
	private void determineXMLEncoding() throws FileNotFoundException, IOException {

		BufferedReader testReader = null;
		try {
			testReader = getEncodedReader(getBufferedInputStream(), detectedFileEncoding);

			String firstLine = testReader.readLine();

			if (firstLine != null && FIRST_LINE_PATTERN.matcher(firstLine).matches()) {
				Matcher matcher = ENCODING_DEF_PATTERN.matcher(firstLine);
				if (matcher.find()) {
					// Encoding piece found.
					String encodingDefParts = matcher.group();

					if (encodingDefParts != null) {

						encodingDefParts = encodingDefParts.replace(" ", "");

						String[] encodingDefPieces = encodingDefParts.split("=");

						// for example encodingDefPieces[1] = "ISO-8859-1"
						String encodingIncludingQuotes = encodingDefPieces[1];

						// Remove the surrounding quotes
						detectedXMLEncoding = encodingIncludingQuotes.replace("\"", "").replace("'", "");
					}
				}
			}
		} finally {
			if (testReader != null) {
				try {
					testReader.close();
				} catch (IOException e) { // NOPMD - Intentionally left empty
				}
			}
		}

	}

	// Determine the encoding by reading the first characters of the
	// file in byte mode by trying to find byte order marks
	private void determineFileEncoding() throws IOException {
		// Set default encoding
		detectedFileEncoding = ISO_8859_1;

		BufferedInputStream inputStream = null;

		try {
			inputStream = getBufferedInputStream();

			inputStream.mark(3);

			int[] verifyEncodingBuf = new int[3];

			int result = 0;
			int index;
			for (index = 0; index < 3; index++) {

				result = inputStream.read();

				if (result == -1) {
					break;
				} else {
					verifyEncodingBuf[index] = result;
				}
			}

			inputStream.reset();

			if (index == 0) {
				// Not enough bytes available to determine any encoding type.
				return;
			}

			// Assume a byte order mark is present.

			int startingBytes = verifyEncodingBuf[0] << 16 | verifyEncodingBuf[1] << 8 | verifyEncodingBuf[2];
			if (startingBytes == 0xefbbbf) {
				byteOrderMarkDetected = true;
				detectedFileEncoding = UTF_8;
				return;
			}

			startingBytes = verifyEncodingBuf[0] << 8 | verifyEncodingBuf[1];
			if (startingBytes == 0xfeff) {
				byteOrderMarkDetected = true;
				detectedFileEncoding = UTF_16BE;
				return;
			}

			if (startingBytes == 0xfffe) {
				byteOrderMarkDetected = true;
				detectedFileEncoding = UTF_16LE;
				return;
			}

			// No ByteOrderMark present.

			if (verifyEncodingBuf[0] == 0 && verifyEncodingBuf[1] != 0) {
				detectedFileEncoding = UTF_16BE;
				return;
			}

			if (verifyEncodingBuf[0] != 0 && verifyEncodingBuf[1] == 0) {
				detectedFileEncoding = UTF_16LE;
				return;
			}

		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) { // NOPMD Intentionally left empty
				}
			}
		}
	}

	/**
	 * Try to find encoding problems by reading the whole file with the concluded encoding
	 * (if no conflicts detected).
	 * This only checks whether a conclusion about UTF-8 encoding can be justified.
	 *
	 * @throws IOException
	 */
	public void checkEncodingProblems() throws IOException {

		if (problemLocation != null && problemLocation.problemDetected) {
			return;
		}

		if (detectedEncoding == null || encodingProblemDetected) {
			return;
		}

		if (UTF_8.equals(detectedEncoding)) {

			UTF8Reader utf8Reader = null;
			FileInputStream fis = null;
			PositionDeterminingInputStream myInputStream = null;
			try {
				fis = new FileInputStream(inputFile);
				myInputStream = new PositionDeterminingInputStream(new BufferedInputStream(fis));
				utf8Reader = new UTF8Reader(myInputStream);

				while (utf8Reader.read() != -1) {
					; // loop executes reading
				}

				problemLocation = new EncodingProblemLocation();
			} catch (UTFDataFormatException e) {
				problemLocation = new EncodingProblemLocation(myInputStream);
			} catch (CharConversionException e) {
				problemLocation = new EncodingProblemLocation(myInputStream);
			} finally {
				if (utf8Reader != null) {
					utf8Reader.close();
				}
				if (fis != null) {
					fis.close();
				}
			}
		}
	}

	/**
	 * Gets the problem information if present
	 * @return a text message describing the problems or null if no problem
	 */
	public String getProblemInfo() {

		try {
			getDetectedEncoding();
			checkEncodingProblems();
		} catch (IOException e) {
			return ValidatorMessageBundle.getMessage("validator.core.validation.encoding.exception", e.getMessage());
		}

		if (problemLocation == null) {
			return null;
		}

		if (problemLocation.problemDetected) {

			int row = problemLocation.getRow();
			int col = problemLocation.getCol();
			int character = problemLocation.getLastChar();
			int charpos = problemLocation.getCharCount();
			String lastRow = problemLocation.getLastRow();
			if (lastRow.length() > 50) {
				lastRow = lastRow.substring(lastRow.length() - 50);
			}

			return ValidatorMessageBundle.getMessage("validator.core.validation.encoding.utf8.wrong-character", row,
					col, charpos, character, lastRow );
		} else {
			return null;
		}
	}

	/**
	 * Checks whether the XML header row contains supported encodings
	 * @return true if encoding supported
	 */
	public boolean isXmlEncodingNotSupported() {

		return xmlEncodingNotSupported;
	}

	/**
	 * Does the file contains byte order marks of supported encodings (or no marks at all) ?
	 * @return true if encoding supported
	 */
	public boolean isFileEncodingNotSupported() {

		return fileEncodingNotSupported;
	}

	/**
	 * Indication of known status about the current reader.
	 *
	 * @return true if no encoding is present, false otherwise
	 */
	public boolean isEncodingMissing() {
		return encodingMissing;
	}

	/**
	 * Sets the supported encodings.
	 * @param supportedEncodingsList the list of encodings that is supported
	 */
	public void setSupportedEncodingsList(List<String> supportedEncodingsList) {
		this.supportedEncodingsList = supportedEncodingsList;
	}

	/**
	 * Class contains information about wrong characters in a file that violates
	 * encoding definitions
	 *
	 * @author rdool
	 *
	 */
	public static class EncodingProblemLocation {

		private final int col;
		private final int row;
		private final int charCount;
		private final int lastChar;
		private final String lastRow;
		private final boolean problemDetected;

		public EncodingProblemLocation() {
			this.col = 0;
			this.row = 0;
			this.charCount = 0;
			this.lastChar = 0;
			this.lastRow = null;
			this.problemDetected = false;
		}

		public EncodingProblemLocation(PositionDeterminingInputStream inp) {
			this.col = inp.getCol();
			this.row = inp.getRow();
			this.charCount = inp.getCharCount();
			this.lastChar = inp.getLastChar();
			this.lastRow = inp.getLastRow();
			this.problemDetected = true;
		}

		public int getCol() {
			return col;
		}

		public int getRow() {
			return row;
		}

		public int getCharCount() {
			return charCount;
		}

		public int getLastChar() {
			return lastChar;
		}

		public String getLastRow() {
			return lastRow;
		}

		public boolean hasProblem() {
			return problemDetected;
		}
	}

	// Inputstream suitable for detecting character problems
	private static class PositionDeterminingInputStream extends InputStream {

		private final InputStream fis;
		private int col = 1;
		private int row = 1;
		private int charCount = 0;
		private int lastChar;
		private StringBuffer rowBuilder;

		public PositionDeterminingInputStream(InputStream fis) {
			this.fis = fis;
		}

		/**
		 * @see java.io.InputStream#read()
		 */
		@Override
		public int read() throws IOException {

			if (lastChar == '\n') {
				row++;
				col = 0;
			}

			lastChar = fis.read();
			col++;
			charCount++;
			buildRow();

			return lastChar;
		}

		private void buildRow() {

			if (lastChar == '\n') {
				rowBuilder = null;
				return;
			}

			if (rowBuilder == null) {
				rowBuilder = new StringBuffer(1);
			} else if (rowBuilder.length() > 256) {
				rowBuilder = new StringBuffer(rowBuilder.substring(128));
			}
			rowBuilder.append((char) lastChar);
		}

		public String getLastRow() {
			return rowBuilder == null ? "" : rowBuilder.toString();
		}

		/**
		 * @see java.io.InputStream#available()
		 */
		@Override
		public int available() throws IOException {
			return fis.available();
		}

		/**
		 * @see java.io.InputStream#close()
		 */
		@Override
		public void close() throws IOException {
			fis.close();
		}

		/**
		 * @return the col
		 */
		public int getCol() {
			return col;
		}

		/**
		 * @return the row
		 */
		public int getRow() {
			return row;
		}

		/**
		 * @return the charCount
		 */
		public int getCharCount() {
			return charCount;
		}

		/**
		 * @return the lastChar
		 */
		public int getLastChar() {
			return lastChar;
		}}

}
