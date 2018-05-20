/*
 * Copyright 2018 Mr Duy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duy.ccppcompiler.packagemanager.repo;

import android.content.Context;
import android.support.annotation.NonNull;

import com.duy.ccppcompiler.packagemanager.Environment;
import com.duy.ccppcompiler.packagemanager.IPackageRepository;
import com.duy.ccppcompiler.packagemanager.RepoParser;
import com.duy.ccppcompiler.packagemanager.model.PackageInfo;
import com.duy.common.DLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import static com.duy.ccppcompiler.packagemanager.RepoUtils.replaceMacro;

/**
 * Created by Duy on 20-May-18.
 */
public class LocalPackageRepository implements IPackageRepository {
    private static final String TAG = "LocalPackageRepository";
    private File mInstalledDir;

    public LocalPackageRepository(Context context) {
        mInstalledDir = new File(Environment.getInstalledPackageDir(context));
    }

    @Override
    @NonNull
    public List<PackageInfo> getPackages() {
        RepoParser parser = new RepoParser();
        return parser.parseRepoXml(getInstalledPackagesData(mInstalledDir), mInstalledDir.getAbsolutePath());
    }

    /**
     * @param dir - the directory contains installed package
     * @return xml content of all installed package
     */
    public String getInstalledPackagesData(File dir) {
        if (dir.isDirectory()) {
            StringBuilder sb = new StringBuilder();
            sb.append("<repo>").append("\n");

            FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    String lowercaseName = name.toLowerCase();
                    return lowercaseName.endsWith(".pkgdesc");
                }
            };

            for (String filePath : dir.list(filter)) {
                if (DLog.DEBUG) DLog.d(TAG, "filePath = " + filePath);
                File f = new File(dir + "/" + filePath);
                try {
                    FileInputStream fin = new FileInputStream(f);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
                    String line = null;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    reader.close();
                } catch (IOException e) {
                    System.err.println(TAG + " getRepoXmlFromDir() IO error " + e);
                }
            }
            sb.append("</repo>");
            if (DLog.DEBUG) {
                System.out.println(TAG + " installed xml = " + replaceMacro(sb.toString()));
            }
            return replaceMacro(sb.toString());
        }
        return null;
    }

}