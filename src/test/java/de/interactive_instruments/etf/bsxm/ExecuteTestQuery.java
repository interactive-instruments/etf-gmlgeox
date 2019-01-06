/**
 * Copyright 2010-2019 interactive instruments GmbH
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.basex.core.BaseXException;
import org.basex.core.Context;
import org.basex.core.cmd.XQuery;
import org.basex.query.QueryException;

public final class ExecuteTestQuery {
	/** Database context. */
	private static Context context = new Context();
	private static String[] xqFiles = {"test.xq"};

	/**
	 * Runs the example code.
	 * @param args (ignored) command-line arguments
	 * @throws IOException if an error occurs while serializing the results
	 * @throws QueryException if an error occurs while evaluating the query
	 * @throws BaseXException if a database command fails
	 */
	public static void main(final String[] args) throws IOException, QueryException {
		// Evaluate the specified XQuery
		for (String file : xqFiles) {
			String query = new String(Files.readAllBytes(Paths.get(file)));
			System.out.println(file + ":");
			System.out.println(new XQuery(query).execute(context));
			System.out.println();
		}
	}
}
