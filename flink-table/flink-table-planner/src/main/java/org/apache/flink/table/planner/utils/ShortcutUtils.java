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

package org.apache.flink.table.planner.utils;

import org.apache.flink.annotation.Internal;
import org.apache.flink.table.api.TableConfig;
import org.apache.flink.table.delegation.Planner;
import org.apache.flink.table.expressions.CallExpression;
import org.apache.flink.table.expressions.ResolvedExpression;
import org.apache.flink.table.functions.FunctionDefinition;
import org.apache.flink.table.planner.calcite.FlinkContext;
import org.apache.flink.table.planner.calcite.FlinkTypeFactory;
import org.apache.flink.table.planner.delegation.PlannerBase;
import org.apache.flink.table.planner.expressions.RexNodeExpression;
import org.apache.flink.table.planner.functions.bridging.BridgingSqlFunction;
import org.apache.flink.table.planner.functions.utils.TableSqlFunction;

import org.apache.calcite.plan.Context;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlOperatorBinding;
import org.apache.calcite.tools.RelBuilder;

import javax.annotation.Nullable;

/**
 * Utilities for quick access of commonly used instances (like {@link FlinkTypeFactory}) without
 * long chains of getters or casting like {@code (FlinkTypeFactory)
 * agg.getCluster.getTypeFactory()}.
 */
@Internal
public final class ShortcutUtils {

    public static FlinkTypeFactory unwrapTypeFactory(SqlOperatorBinding operatorBinding) {
        return unwrapTypeFactory(operatorBinding.getTypeFactory());
    }

    public static FlinkTypeFactory unwrapTypeFactory(RelNode relNode) {
        return unwrapTypeFactory(relNode.getCluster());
    }

    public static FlinkTypeFactory unwrapTypeFactory(RelOptCluster cluster) {
        return unwrapTypeFactory(cluster.getTypeFactory());
    }

    public static FlinkTypeFactory unwrapTypeFactory(RelDataTypeFactory typeFactory) {
        return (FlinkTypeFactory) typeFactory;
    }

    public static FlinkTypeFactory unwrapTypeFactory(RelBuilder relBuilder) {
        return unwrapTypeFactory(relBuilder.getTypeFactory());
    }

    public static FlinkTypeFactory unwrapTypeFactory(Planner planner) {
        return ((PlannerBase) planner).plannerContext().getTypeFactory();
    }

    public static FlinkContext unwrapContext(RelBuilder relBuilder) {
        return unwrapContext(relBuilder.getCluster());
    }

    public static FlinkContext unwrapContext(RelNode relNode) {
        return unwrapContext(relNode.getCluster());
    }

    public static FlinkContext unwrapContext(RelOptCluster cluster) {
        return unwrapContext(cluster.getPlanner());
    }

    public static FlinkContext unwrapContext(RelOptPlanner planner) {
        return unwrapContext(planner.getContext());
    }

    public static FlinkContext unwrapContext(RelOptRuleCall call) {
        return unwrapContext(call.getPlanner());
    }

    public static FlinkContext unwrapContext(Context context) {
        return context.unwrap(FlinkContext.class);
    }

    public static TableConfig unwrapTableConfig(RelNode relNode) {
        return unwrapContext(relNode).getTableConfig();
    }

    public static TableConfig unwrapTableConfig(RelOptRuleCall relOptRuleCall) {
        return unwrapContext(relOptRuleCall.getPlanner()).getTableConfig();
    }

    public static TableConfig unwrapTableConfig(RelOptCluster relOptCluster) {
        return unwrapContext(relOptCluster.getPlanner()).getTableConfig();
    }

    public static ClassLoader unwrapClassLoader(RelNode relNode) {
        return unwrapContext(relNode).getClassLoader();
    }

    public static @Nullable FunctionDefinition unwrapFunctionDefinition(
            ResolvedExpression expression) {
        // Table API expression
        if (expression instanceof CallExpression) {
            final CallExpression callExpression = (CallExpression) expression;
            return callExpression.getFunctionDefinition();
        }

        // SQL expression
        if (!(expression instanceof RexNodeExpression)) {
            return null;
        }
        final RexNodeExpression rexNodeExpression = (RexNodeExpression) expression;
        if (!(rexNodeExpression.getRexNode() instanceof RexCall)) {
            return null;
        }
        return unwrapFunctionDefinition(rexNodeExpression.getRexNode());
    }

    public static @Nullable FunctionDefinition unwrapFunctionDefinition(RexNode rexNode) {
        if (!(rexNode instanceof RexCall)) {
            return null;
        }
        final RexCall call = (RexCall) rexNode;
        if (!(call.getOperator() instanceof BridgingSqlFunction)) {
            // legacy
            if (call.getOperator() instanceof TableSqlFunction) {
                return ((TableSqlFunction) call.getOperator()).udtf();
            }
            return null;
        }
        return ((BridgingSqlFunction) call.getOperator()).getDefinition();
    }

    public static @Nullable BridgingSqlFunction unwrapBridgingSqlFunction(RexCall call) {
        final SqlOperator operator = call.getOperator();
        if (operator instanceof BridgingSqlFunction) {
            return (BridgingSqlFunction) operator;
        }
        return null;
    }

    private ShortcutUtils() {
        // no instantiation
    }
}
