/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.planner.plan.nodes.exec.stream;

import org.apache.flink.table.planner.plan.nodes.exec.testutils.RestoreTestBase;
import org.apache.flink.table.test.program.TableTestProgram;

import java.util.List;

/** Restore tests for {@link StreamExecProcessTableFunction}. */
public class ProcessTableFunctionRestoreTests extends RestoreTestBase {

    protected ProcessTableFunctionRestoreTests() {
        super(StreamExecProcessTableFunction.class);
    }

    @Override
    public List<TableTestProgram> programs() {
        return List.of(
                ProcessTableFunctionTestPrograms.PROCESS_ROW_SEMANTIC_TABLE_RESTORE,
                ProcessTableFunctionTestPrograms.PROCESS_MULTI_STATE_RESTORE,
                ProcessTableFunctionTestPrograms.PROCESS_NAMED_TIMERS_RESTORE,
                ProcessTableFunctionTestPrograms.PROCESS_MULTI_INPUT_RESTORE,
                ProcessTableFunctionTestPrograms.PROCESS_MAP_STATE_RESTORE,
                ProcessTableFunctionTestPrograms.PROCESS_UPDATING_OUTPUT_UPSERT_RESTORE);
    }
}
