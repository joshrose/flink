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
package org.apache.flink.table.planner.plan.batch.sql

import org.apache.flink.table.api._
import org.apache.flink.table.api.config.OptimizerConfigOptions
import org.apache.flink.table.connector.ChangelogMode
import org.apache.flink.table.planner.runtime.utils.JavaUserDefinedScalarFunctions.NonDeterministicUdf
import org.apache.flink.table.planner.runtime.utils.TestSinkUtil
import org.apache.flink.table.planner.utils.{TableFunc1, TableTestBase}
import org.apache.flink.table.types.DataType

import org.junit.jupiter.api.Test

import java.sql.Timestamp

class DagOptimizationTest extends TableTestBase {
  private val util = batchTestUtil()
  util.addTableSource[(Int, Long, String)]("MyTable", 'a, 'b, 'c)
  util.addTableSource[(Int, Long, String)]("MyTable1", 'd, 'e, 'f)

  val STRING: DataType = DataTypes.STRING
  val LONG: DataType = DataTypes.BIGINT
  val INT: DataType = DataTypes.INT
  val DOUBLE: DataType = DataTypes.DOUBLE
  val TIMESTAMP: DataType = DataTypes.TIMESTAMP(3)

  @Test
  def testSingleSink1(): Unit = {
    val table = util.tableEnv.sqlQuery("SELECT c, COUNT(a) AS cnt FROM MyTable GROUP BY c")
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "appendSink",
      List("c", "cnt"),
      List(STRING, LONG),
      ChangelogMode.insertOnly()
    )
    util.verifyExecPlanInsert(table, "appendSink")
  }

  @Test
  def testSingleSink2(): Unit = {
    val table1 = util.tableEnv.sqlQuery("SELECT a as a1, b FROM MyTable WHERE a <= 10")
    util.tableEnv.createTemporaryView("table1", table1)
    val table2 = util.tableEnv.sqlQuery("SELECT a, b, c FROM MyTable WHERE a >= 0")
    util.tableEnv.createTemporaryView("table2", table2)
    val table3 = util.tableEnv.sqlQuery("SELECT a AS a2, c FROM table2 WHERE b >= 5")
    util.tableEnv.createTemporaryView("table3", table3)
    val table4 = util.tableEnv.sqlQuery("SELECT a AS a3, c as c1 FROM table2 WHERE b < 5")
    util.tableEnv.createTemporaryView("table4", table4)
    val table5 = util.tableEnv.sqlQuery("SELECT a1, b, c as c2 FROM table1, table3 WHERE a1 = a2")
    util.tableEnv.createTemporaryView("table5", table5)
    val table6 = util.tableEnv.sqlQuery("SELECT a1, b, c1 FROM table4, table5 WHERE a1 = a3")

    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "appendSink",
      List("a1", "b", "c1"),
      List(INT, LONG, STRING),
      ChangelogMode.insertOnly()
    )
    util.verifyExecPlanInsert(table6, "appendSink")
  }

  @Test
  def testSingleSink3(): Unit = {
    util.addDataStream[(Int, Long, String, Double, Boolean)]("MyTable2", 'a, 'b, 'c, 'd, 'e)
    val table1 = util.tableEnv.sqlQuery("SELECT a AS a1, b as b1 FROM MyTable WHERE a <= 10")
    util.tableEnv.createTemporaryView("table1", table1)
    val table2 = util.tableEnv.sqlQuery("SELECT a, b1 FROM table1, MyTable2 WHERE a = a1")
    util.tableEnv.createTemporaryView("table2", table2)
    val table3 = util.tableEnv.sqlQuery("SELECT * FROM table1 UNION ALL SELECT * FROM table2")

    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "appendSink",
      List("a1", "b1"),
      List(INT, LONG),
      ChangelogMode.insertOnly()
    )
    util.verifyExecPlanInsert(table3, "appendSink")
  }

  @Test
  def testSingleSink4(): Unit = {
    val table1 = util.tableEnv.sqlQuery("SELECT a as a1, b FROM MyTable WHERE a <= 10")
    util.tableEnv.createTemporaryView("table1", table1)
    val table2 = util.tableEnv.sqlQuery("SELECT a, b, c FROM MyTable WHERE a >= 0")
    util.tableEnv.createTemporaryView("table2", table2)
    val table3 = util.tableEnv.sqlQuery("SELECT a AS a2, c FROM table2 WHERE b >= 5")
    util.tableEnv.createTemporaryView("table3", table3)
    val table4 = util.tableEnv.sqlQuery("SELECT a AS a3, c AS c1 FROM table2 WHERE b < 5")
    util.tableEnv.createTemporaryView("table4", table4)
    val table5 = util.tableEnv.sqlQuery("SELECT a1, b, c AS c2 from table1, table3 WHERE a1 = a2")
    util.tableEnv.createTemporaryView("table5", table5)
    val table6 = util.tableEnv.sqlQuery("SELECT a3, b as b1, c1 FROM table4, table5 WHERE a1 = a3")
    util.tableEnv.createTemporaryView("table6", table6)
    val table7 = util.tableEnv.sqlQuery("SELECT a1, b1, c1 FROM table1, table6 WHERE a1 = a3")

    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink",
      List("a", "b", "c"),
      List(INT, LONG, STRING),
      ChangelogMode.insertOnly()
    )
    util.verifyExecPlanInsert(table7, "sink")
  }

  @Test
  def testSingleSinkWithUDTF(): Unit = {
    util.addTableSource[(Int, Long, Int, String, Long)]("MyTable2", 'i, 'j, 'k, 'l, 'm)
    util.addTemporarySystemFunction("split", new TableFunc1)

    val sqlQuery =
      """
        |select * from
        |    (SELECT * FROM MyTable, MyTable1, MyTable2 WHERE b = e AND a = i) t,
        |    LATERAL TABLE(split(c)) as T(s)
      """.stripMargin

    val table = util.tableEnv.sqlQuery(sqlQuery)
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink",
      List("a", "b", "c", "d", "e", "f", "i", "j", "k", "l", "m", "s"),
      List(
        INT,
        LONG,
        STRING,
        INT,
        LONG,
        STRING,
        INT,
        LONG,
        INT,
        STRING,
        LONG,
        STRING
      ),
      ChangelogMode.insertOnly()
    )
    util.verifyExecPlanInsert(table, "sink")
  }

  @Test
  def testSingleSinkSplitOnUnion(): Unit = {
    val sqlQuery = "SELECT SUM(a) AS total_sum FROM " +
      "(SELECT a, c FROM MyTable UNION ALL SELECT d, f FROM MyTable1)"
    val table = util.tableEnv.sqlQuery(sqlQuery)
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink",
      List("total_sum"),
      List(INT),
      ChangelogMode.insertOnly()
    )
    util.verifyExecPlanInsert(table, "sink")
  }

  @Test
  def testMultiSinks1(): Unit = {
    val stmtSet = util.tableEnv.createStatementSet()
    util.tableEnv.getConfig.set(
      OptimizerConfigOptions.TABLE_OPTIMIZER_REUSE_OPTIMIZE_BLOCK_WITH_DIGEST_ENABLED,
      Boolean.box(true))
    val table1 = util.tableEnv.sqlQuery("SELECT SUM(a) AS sum_a, c FROM MyTable GROUP BY c")
    util.tableEnv.createTemporaryView("table1", table1)
    val table2 = util.tableEnv.sqlQuery("SELECT SUM(sum_a) AS total_sum FROM table1")
    val table3 = util.tableEnv.sqlQuery("SELECT MIN(sum_a) AS total_min FROM table1")

    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink1",
      List("total_sum"),
      List(INT),
      ChangelogMode.insertOnly()
    )
    stmtSet.addInsert("sink1", table2)

    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink2",
      List("total_min"),
      List(INT),
      ChangelogMode.insertOnly()
    )
    stmtSet.addInsert("sink2", table3)

    util.verifyExecPlan(stmtSet)
  }

  @Test
  def testMultiSinks2(): Unit = {
    val stmtSet = util.tableEnv.createStatementSet()
    util.tableEnv.getConfig
      .set(OptimizerConfigOptions.TABLE_OPTIMIZER_UNIONALL_AS_BREAKPOINT_ENABLED, Boolean.box(true))
    util.addTableSource[(Int, Long, String, Double, Boolean)]("MyTable2", 'a, 'b, 'c, 'd, 'e)

    val table1 = util.tableEnv.sqlQuery("SELECT a as a1, b as b1 FROM MyTable WHERE a <= 10")
    util.tableEnv.createTemporaryView("table1", table1)
    val table2 = util.tableEnv.sqlQuery("SELECT a, b1 from table1, MyTable2 where a = a1")
    util.tableEnv.createTemporaryView("table2", table2)
    val table3 = util.tableEnv.sqlQuery("SELECT * FROM table1 UNION ALL SELECT * FROM table2")

    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink1",
      List("a", "b1"),
      List(INT, LONG),
      ChangelogMode.insertOnly()
    )
    stmtSet.addInsert("sink1", table3)

    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink2",
      List("a", "b1"),
      List(INT, LONG),
      ChangelogMode.insertOnly()
    )
    stmtSet.addInsert("sink2", table3)

    util.verifyExecPlan(stmtSet)
  }

  @Test
  def testMultiSinks3(): Unit = {
    val stmtSet = util.tableEnv.createStatementSet()
    util.tableEnv.getConfig.set(
      OptimizerConfigOptions.TABLE_OPTIMIZER_UNIONALL_AS_BREAKPOINT_ENABLED,
      Boolean.box(false))
    util.addTableSource[(Int, Long, String, Double, Boolean)]("MyTable2", 'a, 'b, 'c, 'd, 'e)

    val table1 = util.tableEnv.sqlQuery("SELECT a AS a1, b AS b1 FROM MyTable WHERE a <= 10")
    util.tableEnv.createTemporaryView("table1", table1)
    val table2 = util.tableEnv.sqlQuery("SELECT a, b1 FROM table1, MyTable2 WHERE a = a1")
    util.tableEnv.createTemporaryView("table2", table2)
    val table3 = util.tableEnv.sqlQuery("SELECT * FROM table1 UNION ALL SELECT * FROM table2")

    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink1",
      List("a", "b1"),
      List(INT, LONG),
      ChangelogMode.insertOnly()
    )
    stmtSet.addInsert("sink1", table2)

    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink2",
      List("a", "b1"),
      List(INT, LONG),
      ChangelogMode.insertOnly()
    )
    stmtSet.addInsert("sink2", table3)

    util.verifyExecPlan(stmtSet)
  }

  @Test
  def testMultiSinks4(): Unit = {
    val stmtSet = util.tableEnv.createStatementSet()
    util.tableEnv.getConfig.set(
      OptimizerConfigOptions.TABLE_OPTIMIZER_REUSE_OPTIMIZE_BLOCK_WITH_DIGEST_ENABLED,
      Boolean.box(true))

    val table1 = util.tableEnv.sqlQuery("SELECT a as a1, b FROM MyTable WHERE a <= 10")
    util.tableEnv.createTemporaryView("table1", table1)
    val table2 = util.tableEnv.sqlQuery("SELECT a, b, c FROM MyTable WHERE a >= 0")
    util.tableEnv.createTemporaryView("table2", table2)
    val table3 = util.tableEnv.sqlQuery("SELECT a as a2, c FROM table2 WHERE b >= 5")
    util.tableEnv.createTemporaryView("table3", table3)
    val table4 = util.tableEnv.sqlQuery("SELECT a as a3, c as c1 FROM table2 WHERE b < 5")
    util.tableEnv.createTemporaryView("table4", table4)
    val table5 = util.tableEnv.sqlQuery("SELECT a1, b, c as c2 FROM table1, table3 WHERE a1 = a2")
    util.tableEnv.createTemporaryView("table5", table5)
    val table6 = util.tableEnv.sqlQuery("SELECT a1, b, c1 FROM table4, table5 WHERE a1 = a3")

    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink1",
      List("a1", "b", "c2"),
      List(INT, LONG, STRING),
      ChangelogMode.insertOnly()
    )
    stmtSet.addInsert("sink1", table5)

    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink2",
      List("a1", "b", "c1"),
      List(INT, LONG, STRING),
      ChangelogMode.insertOnly()
    )
    stmtSet.addInsert("sink2", table6)

    util.verifyExecPlan(stmtSet)
  }

  @Test
  def testMultiSinks5(): Unit = {
    val stmtSet = util.tableEnv.createStatementSet()
    util.tableEnv.getConfig.set(
      OptimizerConfigOptions.TABLE_OPTIMIZER_REUSE_OPTIMIZE_BLOCK_WITH_DIGEST_ENABLED,
      Boolean.box(true))
    // test with non-deterministic udf
    util.addTemporarySystemFunction("random_udf", new NonDeterministicUdf())
    val table1 = util.tableEnv.sqlQuery("SELECT random_udf(a) AS a, c FROM MyTable")
    util.tableEnv.createTemporaryView("table1", table1)
    val table2 = util.tableEnv.sqlQuery("SELECT SUM(a) AS total_sum FROM table1")
    val table3 = util.tableEnv.sqlQuery("SELECT MIN(a) AS total_min FROM table1")

    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink1",
      List("total_sum"),
      List(INT),
      ChangelogMode.insertOnly()
    )
    stmtSet.addInsert("sink1", table2)

    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink2",
      List("total_min"),
      List(INT),
      ChangelogMode.insertOnly()
    )
    stmtSet.addInsert("sink2", table3)

    util.verifyExecPlan(stmtSet)
  }

  @Test
  def testMultiLevelViews(): Unit = {
    val stmtSet = util.tableEnv.createStatementSet()
    util.tableEnv.getConfig.set(
      OptimizerConfigOptions.TABLE_OPTIMIZER_UNIONALL_AS_BREAKPOINT_ENABLED,
      Boolean.box(false))

    val table1 = util.tableEnv.sqlQuery("SELECT a, b, c FROM MyTable WHERE c LIKE '%hello%'")
    util.tableEnv.createTemporaryView("TempTable1", table1)
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink1",
      List("a", "b", "c"),
      List(INT, LONG, STRING),
      ChangelogMode.insertOnly())
    stmtSet.addInsert("sink1", table1)

    val table2 = util.tableEnv.sqlQuery("SELECT a, b, c FROM MyTable WHERE c LIKE '%world%'")
    util.tableEnv.createTemporaryView("TempTable2", table2)

    val sqlQuery =
      """
        |SELECT b, COUNT(a) AS cnt FROM (
        | (SELECT * FROM TempTable1)
        | UNION ALL
        | (SELECT * FROM TempTable2)
        |) t
        |GROUP BY b
      """.stripMargin
    val table3 = util.tableEnv.sqlQuery(sqlQuery)
    util.tableEnv.createTemporaryView("TempTable3", table3)

    val table4 = util.tableEnv.sqlQuery("SELECT b, cnt FROM TempTable3 WHERE b < 4")
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink2",
      List("b", "cnt"),
      List(LONG, LONG),
      ChangelogMode.insertOnly())
    stmtSet.addInsert("sink2", table4)

    val table5 = util.tableEnv.sqlQuery("SELECT b, cnt FROM TempTable3 WHERE b >=4 AND b < 6")
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink3",
      List("b", "cnt"),
      List(LONG, LONG),
      ChangelogMode.insertOnly())
    stmtSet.addInsert("sink3", table5)

    util.verifyExecPlan(stmtSet)
  }

  @Test
  def testMultiSinksWithUDTF(): Unit = {
    val stmtSet = util.tableEnv.createStatementSet()
    util.tableEnv.getConfig.set(
      OptimizerConfigOptions.TABLE_OPTIMIZER_UNIONALL_AS_BREAKPOINT_ENABLED,
      Boolean.box(false))
    util.addTemporarySystemFunction("split", new TableFunc1)
    val sqlQuery1 =
      """
        |SELECT  a, b - MOD(b, 300) AS b, c FROM MyTable
        |WHERE b >= UNIX_TIMESTAMP('${startTime}')
      """.stripMargin
    val table1 = util.tableEnv.sqlQuery(sqlQuery1)
    util.tableEnv.createTemporaryView("table1", table1)

    val sqlQuery2 =
      "SELECT a, b, c1 AS c FROM table1, LATERAL TABLE(split(c)) AS T(c1) WHERE c <> '' "
    val table2 = util.tableEnv.sqlQuery(sqlQuery2)
    util.tableEnv.createTemporaryView("table2", table2)

    val sqlQuery3 = "SELECT a, b, COUNT(DISTINCT c) AS total_c FROM table2 GROUP BY a, b"
    val table3 = util.tableEnv.sqlQuery(sqlQuery3)
    util.tableEnv.createTemporaryView("table3", table3)

    val sqlQuery4 = "SELECT a, total_c FROM table3 UNION ALL SELECT a, 0 AS total_c FROM table1"
    val table4 = util.tableEnv.sqlQuery(sqlQuery4)
    util.tableEnv.createTemporaryView("table4", table4)

    val sqlQuery5 = "SELECT * FROM table4 WHERE a > 50"
    val table5 = util.tableEnv.sqlQuery(sqlQuery5)
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink1",
      List("a", "total_c"),
      List(INT, LONG),
      ChangelogMode.insertOnly())
    stmtSet.addInsert("sink1", table5)

    val sqlQuery6 = "SELECT * FROM table4 WHERE a < 50"
    val table6 = util.tableEnv.sqlQuery(sqlQuery6)
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink2",
      List("a", "total_c"),
      List(INT, LONG),
      ChangelogMode.insertOnly())
    stmtSet.addInsert("sink2", table6)

    util.verifyExecPlan(stmtSet)
  }

  @Test
  def testMultiSinksSplitOnUnion1(): Unit = {
    val stmtSet = util.tableEnv.createStatementSet()
    util.tableEnv.getConfig.set(
      OptimizerConfigOptions.TABLE_OPTIMIZER_UNIONALL_AS_BREAKPOINT_ENABLED,
      Boolean.box(false))

    val table =
      util.tableEnv.sqlQuery("SELECT a, c FROM MyTable UNION ALL SELECT d, f FROM MyTable1")
    util.tableEnv.createTemporaryView("TempTable", table)

    val table1 = util.tableEnv.sqlQuery("SELECT SUM(a) AS total_sum FROM TempTable")
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink1",
      List("total_sum"),
      List(INT),
      ChangelogMode.insertOnly())
    stmtSet.addInsert("sink1", table1)

    val table3 = util.tableEnv.sqlQuery("SELECT MIN(a) AS total_min FROM TempTable")
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink2",
      List("total_min"),
      List(INT),
      ChangelogMode.insertOnly())
    stmtSet.addInsert("sink2", table3)

    util.verifyExecPlan(stmtSet)
  }

  @Test
  def testMultiSinksSplitOnUnion2(): Unit = {
    val stmtSet = util.tableEnv.createStatementSet()
    util.tableEnv.getConfig.set(
      OptimizerConfigOptions.TABLE_OPTIMIZER_REUSE_OPTIMIZE_BLOCK_WITH_DIGEST_ENABLED,
      Boolean.box(true))
    util.tableEnv.getConfig.set(
      OptimizerConfigOptions.TABLE_OPTIMIZER_UNIONALL_AS_BREAKPOINT_ENABLED,
      Boolean.box(false))
    util.addTableSource[(Int, Long, String)]("MyTable2", 'a, 'b, 'c)

    val sqlQuery1 =
      """
        |SELECT a, c FROM MyTable
        |UNION ALL
        |SELECT d, f FROM MyTable1
        |UNION ALL
        |SELECT a, c FROM MyTable2
      """.stripMargin
    val table = util.tableEnv.sqlQuery(sqlQuery1)
    util.tableEnv.createTemporaryView("TempTable", table)

    val table1 = util.tableEnv.sqlQuery("SELECT SUM(a) AS total_sum FROM TempTable")
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink1",
      List("total_sum"),
      List(INT),
      ChangelogMode.insertOnly())
    stmtSet.addInsert("sink1", table1)

    val table2 = util.tableEnv.sqlQuery("SELECT MIN(a) AS total_min FROM TempTable")
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink2",
      List("total_min"),
      List(INT),
      ChangelogMode.insertOnly())
    stmtSet.addInsert("sink2", table2)

    val sqlQuery2 = "SELECT a FROM (SELECT a, c FROM MyTable UNION ALL SELECT d, f FROM MyTable1)"
    val table3 = util.tableEnv.sqlQuery(sqlQuery2)
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink3",
      List("a"),
      List(INT),
      ChangelogMode.insertOnly())
    stmtSet.addInsert("sink3", table3)

    util.verifyExecPlan(stmtSet)
  }

  @Test
  def testMultiSinksSplitOnUnion3(): Unit = {
    val stmtSet = util.tableEnv.createStatementSet()
    util.tableEnv.getConfig.set(
      OptimizerConfigOptions.TABLE_OPTIMIZER_UNIONALL_AS_BREAKPOINT_ENABLED,
      Boolean.box(false))
    util.addTableSource[(Int, Long, String)]("MyTable2", 'a, 'b, 'c)

    val sqlQuery1 = "SELECT a, c FROM MyTable UNION ALL SELECT d, f FROM MyTable1"
    val table = util.tableEnv.sqlQuery(sqlQuery1)
    util.tableEnv.createTemporaryView("TempTable", table)

    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink1",
      List("a", "c"),
      List(INT, STRING),
      ChangelogMode.insertOnly())
    stmtSet.addInsert("sink1", table)

    val sqlQuery2 = "SELECT a, c FROM TempTable UNION ALL SELECT a, c FROM MyTable2"
    val table1 = util.tableEnv.sqlQuery(sqlQuery2)
    util.tableEnv.createTemporaryView("TempTable1", table1)

    val table2 = util.tableEnv.sqlQuery("SELECT SUM(a) AS total_sum FROM TempTable1")
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink2",
      List("total_sum"),
      List(INT),
      ChangelogMode.insertOnly())
    stmtSet.addInsert("sink2", table2)

    val table3 = util.tableEnv.sqlQuery("SELECT MIN(a) AS total_min FROM TempTable1")
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink3",
      List("total_min"),
      List(INT),
      ChangelogMode.insertOnly())
    stmtSet.addInsert("sink3", table3)

    util.verifyExecPlan(stmtSet)
  }

  @Test
  def testMultiSinksSplitOnUnion4(): Unit = {
    val stmtSet = util.tableEnv.createStatementSet()
    util.tableEnv.getConfig.set(
      OptimizerConfigOptions.TABLE_OPTIMIZER_UNIONALL_AS_BREAKPOINT_ENABLED,
      Boolean.box(false))
    util.addTableSource[(Int, Long, String)]("MyTable2", 'a, 'b, 'c)

    val sqlQuery =
      """
        |SELECT a, c FROM MyTable
        |UNION ALL
        |SELECT d, f FROM MyTable1
        |UNION ALL
        |SELECT a, c FROM MyTable2
      """.stripMargin
    val table = util.tableEnv.sqlQuery(sqlQuery)
    util.tableEnv.createTemporaryView("TempTable", table)

    val table1 = util.tableEnv.sqlQuery("SELECT SUM(a) AS total_sum FROM TempTable")
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink1",
      List("total_sum"),
      List(INT),
      ChangelogMode.insertOnly())
    stmtSet.addInsert("sink1", table1)

    val table2 = util.tableEnv.sqlQuery("SELECT MIN(a) AS total_min FROM TempTable")
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink2",
      List("total_min"),
      List(INT),
      ChangelogMode.insertOnly())
    stmtSet.addInsert("sink2", table2)

    util.verifyExecPlan(stmtSet)
  }

  @Test
  def testMultiSinksWithWindow(): Unit = {
    val stmtSet = util.tableEnv.createStatementSet()
    util.addTableSource[(Int, Double, Int, Timestamp)]("MyTable2", 'a, 'b, 'c, 'ts)
    val sqlQuery1 =
      """
        |SELECT
        |    a,
        |    SUM (CAST (c AS DOUBLE)) AS sum_c,
        |    TUMBLE_END(ts, INTERVAL '15' SECOND) AS `time`,
        |    TUMBLE_START(ts, INTERVAL '15' SECOND) AS window_start,
        |    TUMBLE_END (ts, INTERVAL '15' SECOND) AS window_end
        |FROM
        |    MyTable2
        |GROUP BY
        |    TUMBLE (ts, INTERVAL '15' SECOND), a
      """.stripMargin

    val sqlQuery2 =
      """
        |SELECT
        |    a,
        |    SUM (CAST (c AS DOUBLE)) AS sum_c,
        |    TUMBLE_END(ts, INTERVAL '15' SECOND) AS `time`
        |FROM
        |    MyTable2
        |GROUP BY
        |    TUMBLE (ts, INTERVAL '15' SECOND), a
      """.stripMargin

    val table1 = util.tableEnv.sqlQuery(sqlQuery1)
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink1",
      List("a", "sum_c", "time", "window_start", "window_end"),
      List(INT, DOUBLE, TIMESTAMP, TIMESTAMP, DataTypes.TIMESTAMP(3)),
      ChangelogMode.insertOnly()
    )
    stmtSet.addInsert("sink1", table1)

    val table2 = util.tableEnv.sqlQuery(sqlQuery2)
    TestSinkUtil.addValuesSink(
      util.tableEnv,
      "sink2",
      List("a", "sum_c", "time"),
      List(INT, DOUBLE, DataTypes.TIMESTAMP(3)),
      ChangelogMode.insertOnly()
    )
    stmtSet.addInsert("sink2", table2)

    util.verifyExecPlan(stmtSet)
  }

}
