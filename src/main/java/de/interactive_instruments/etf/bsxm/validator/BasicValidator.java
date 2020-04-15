/**
 * Copyright 2010-2020 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.interactive_instruments.etf.bsxm.validator;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.operation.valid.IsValidOp;
import com.vividsolutions.jts.operation.valid.TopologyValidationError;

import org.deegree.gml.GMLVersion;

import de.interactive_instruments.etf.bsxm.geometry.IIGeometryValidator;

/**
 *
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 */
final class BasicValidator implements Validator {

    @Override
    public int getId() {
        return 0;
    }

    @Override
    public void validate(final ElementContext elementContext, final ValidationResult result) {
        final Geometry jtsGeom = elementContext.getJtsGeometry(result);
        boolean jtsValid = false;
        if (jtsGeom == null) {
            result.failSilently();
        } else {
            try {
                final IsValidOp ivo = new IsValidOp(jtsGeom);
                final TopologyValidationError topError = ivo.getValidationError();
                if (topError != null) {
                    // Optimization: ivo.isValid() is the same as topError==null. Otherwise
                    // the validation is performed twice in case of geometry problems.
                    result.addError(elementContext,
                            Message.translate("gmlgeox.validation.geometry.jts." + topError.getErrorType()), jtsGeom,
                            topError.getCoordinate());
                } else {
                    jtsValid = true;
                }
            } catch (final IllegalArgumentException e) {
                result.addError(elementContext,
                        Message.translate("gmlgeox.validation.geometry.unsupported", e.getMessage()));
            }
        }

        final BasicValidatorGMLEventHandler eventHandler = new BasicValidatorGMLEventHandler(elementContext, result,
                elementContext.gmlVersion == GMLVersion.GML_31);
        final IIGeometryValidator validator = new IIGeometryValidator(eventHandler,
                elementContext.deegreeTransformer.getGeometryFactory());
        // Deegree3 based validation. Errors are collected through the BasicValidatorGMLEventHandler
        validator.validateGeometry(elementContext.deegreeGeom, jtsValid);
    }
}
