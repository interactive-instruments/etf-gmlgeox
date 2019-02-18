package nl.vrom.roo.validator.core.dom4j.handlers;

import nl.vrom.roo.validator.core.ValidatorMessageBundle;
import org.deegree.geometry.primitive.Point;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * @author Johannes Echterhoff (echterhoff at interactive-instruments
 *         dot de)
 *
 */
public class ValidationUtil {

	public ValidationUtil() {
	}

	private static final NumberFormat COORD_FORMAT;
	
	static {
		NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH); 
		DecimalFormat df =  (DecimalFormat)nf;
		df.applyPattern("0.000#######");
		COORD_FORMAT = df;
	}

	public static String formatValue(double value) { // NOPMD - Method is not
														// empty
		return COORD_FORMAT.format(value);
	}

	public static String getProblemLocation(Point location) {

		String key = "validator.core.validation.geometry.problem-location";

		if (location.get2() == Double.NaN) {
			return ValidatorMessageBundle.getMessage(key,
					formatValue(location.get0()), formatValue(location.get1()));
		} else {
			return ValidatorMessageBundle.getMessage(key,
					formatValue(location.get0()), formatValue(location.get1()),
					formatValue(location.get2()));
		}
	}

	public static String getAffectedCoordinates(
			Object affectedGeometryParticles, Integer numberOfCharacters) {
		
		String out = null;
		
		if (affectedGeometryParticles instanceof org.deegree.geometry.standard.curvesegments.DefaultLineStringSegment) {

			org.deegree.geometry.standard.curvesegments.DefaultLineStringSegment segment = (org.deegree.geometry.standard.curvesegments.DefaultLineStringSegment) affectedGeometryParticles;

			StringBuffer buf = new StringBuffer("LINESTRING (");

			for (Point point : segment.getControlPoints()) {
				buf.append(formatValue(point.get0())).append(" ")
						.append(formatValue(point.get1())).append(",");
			}
			buf.deleteCharAt(buf.length()-1);
			buf.append(")");

			out = buf.toString();
		} else {
			out = affectedGeometryParticles.toString();
		}

		if(numberOfCharacters == null) {
			return out;
		} else {
			return out.substring(0, Math.min(out.length(), numberOfCharacters));
		}
	}

}
