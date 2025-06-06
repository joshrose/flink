/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.state.forst.fs.filemapping;

import org.apache.flink.core.fs.Path;

public class FileOwnershipDecider {

    public static final String SST_SUFFIX = ".sst";

    private FileOwnershipDecider() {}

    public static FileOwnership decideForNewFile(Path filePath) {
        // local files are always privately owned by DB
        return shouldAlwaysBeLocal(filePath)
                ? FileOwnership.PRIVATE_OWNED_BY_DB
                : FileOwnership.SHAREABLE_OWNED_BY_DB;
    }

    public static FileOwnership decideForRestoredFile(Path filePath) {
        // local files are always privately owned by DB
        return shouldAlwaysBeLocal(filePath)
                ? FileOwnership.PRIVATE_OWNED_BY_DB
                : FileOwnership.NOT_OWNED;
    }

    public static boolean isSstFile(Path filePath) {
        return filePath.getName().endsWith(SST_SUFFIX);
    }

    public static boolean shouldAlwaysBeLocal(Path filePath, FileOwnership fileOwnership) {
        return !isSstFile(filePath) && fileOwnership != FileOwnership.NOT_OWNED;
    }

    public static boolean shouldAlwaysBeLocal(Path filePath) {
        return !isSstFile(filePath);
    }
}
