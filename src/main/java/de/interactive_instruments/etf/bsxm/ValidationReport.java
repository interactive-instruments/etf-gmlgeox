/**
 * Copyright 2010-2016 interactive instruments GmbH
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
package de.interactive_instruments.etf.bsxm;

import java.util.ArrayList;
import java.util.List;

import nl.vrom.roo.validator.core.ValidatorMessage;

/**
 * @author Johannes Echterhoff (echterhoff <at> interactive-instruments <dot> de)
 *
 */
public final class ValidationReport {

	private String validationResult = "VVV";
	private List<ValidatorMessage> validatorMessages = new ArrayList<ValidatorMessage>();

	public ValidationReport() {

	}

	public ValidationReport(String validationResult, List<ValidatorMessage> validatorMessages) {
		this.validationResult = validationResult;
		this.validatorMessages = validatorMessages;
	}

	/**
	 * @return the validationResult
	 */
	public String getValidationResult() {
		return validationResult;
	}

	/**
	 * @return the validatorMessages
	 */
	public List<ValidatorMessage> getValidatorMessages() {
		return validatorMessages;
	}

	public boolean isValid() {
		return validationResult.toLowerCase().indexOf('f') == -1;
	}

}
