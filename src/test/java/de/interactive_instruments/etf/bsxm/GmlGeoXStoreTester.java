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
package de.interactive_instruments.etf.bsxm;

import java.io.Externalizable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.basex.query.QueryException;
import org.basex.query.QueryModule;

/** @author Johannes Echterhoff (echterhoff at interactive-instruments dot de) */
public class GmlGeoXStoreTester extends QueryModule {

    public static final String STORE_DIR_NAME = "XqueryModuleStore";

    public GmlGeoXStoreTester() {}

    @Requires(Permission.NONE)
    public void storeQueryModule(Object module, String moduleId)
            throws QueryException {

        // parameter checks TBD

        Externalizable ext = (Externalizable) module;

        final String tempDirPath = System.getProperty("java.io.tmpdir");
        final File tempDir = new File(tempDirPath, STORE_DIR_NAME);

        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        final File tempFile = new File(tempDir, moduleId + ".tmp");

        if (tempFile.exists()) {
            tempFile.delete();
        }

        try (FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
            ext.writeExternal(objectOutputStream);
        } catch (IOException e) {
            throw new QueryException("Exception occurred while storing query module. " + e.getMessage());
        }
    }

    @Requires(Permission.NONE)
    public void restoreQueryModule(Object module, String moduleId)
            throws QueryException {

        // parameter checks TBD

        Externalizable ext = (Externalizable) module;

        final String tempDirPath = System.getProperty("java.io.tmpdir");
        final File tempDir = new File(tempDirPath, STORE_DIR_NAME);
        final File tempFile = new File(tempDir, moduleId + ".tmp");

        try (FileInputStream fileInputStream = new FileInputStream(tempFile);
                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);) {
            ext.readExternal(objectInputStream);
        } catch (IOException | ClassNotFoundException e) {
            throw new QueryException("Exception occurred while restoring query module. " + e.getMessage());
        }
    }
}
