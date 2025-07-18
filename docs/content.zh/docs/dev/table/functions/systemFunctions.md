---
title: "系统（内置）函数"
weight: 32
type: docs
aliases:
  - /zh/dev/table/functions/systemFunctions.html
---
<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# 系统（内置）函数

Flink Table API & SQL 为用户提供了一组内置的数据转换函数。本页简要介绍了它们。如果你需要的函数尚不支持，你可以实现
[用户自定义函数]({{< ref "docs/dev/table/functions/udfs" >}})。如果你觉得这个函数够通用，请
<a href="https://issues.apache.org/jira/secure/CreateIssue!default.jspa">创建一个 Jira issue</a>并详细
说明。

标量函数
----------------

标量函数将零、一个或多个值作为输入并返回单个值作为结果。

### 比较函数

{{< sql_functions_zh "comparison" >}}

### 逻辑函数

{{< sql_functions_zh "logical" >}}

### 算术函数

{{< sql_functions_zh "arithmetic" >}}

### 字符串函数

{{< sql_functions_zh "string" >}}

### 时间函数

{{< sql_functions_zh "temporal" >}}

### 条件函数

{{< sql_functions_zh "conditional" >}}

### 类型转换函数

{{< sql_functions_zh "conversion" >}}

### 集合函数

{{< sql_functions_zh "collection" >}}

### JSON 函数

JSON 函数使用符合 ISO/IEC TR 19075-6 SQL标准的 JSON 路径表达式。 它们的语法受到 ECMAScript 的启发，并
采用了 ECMAScript 的许多功能，但不是其子集或超集。

路径表达式有宽容模式和严格模式两种模式。 当不指定时，默认使用严格模式。
严格模式旨在从 Schema 的角度检查数据，并且只要数据不符合路径表达式就会抛出错误。 但是像`JSON_VALUE`的函数
允许定义遇到错误时的后备行为。 另一方面，宽容模式更加宽容，并将错误转换为空序列。

特殊字符`$`表示 JSON 路径中的根节点。 路径可以访问属性（`$.a`）， 数组元素 (`$.a[0].b`)，或遍历数组中的
所有元素 (`$.a[*].b`)。

已知限制：
* 目前，并非宽容模式的所有功能都被正确支持。 这是一个上游的错误（CALCITE-4717）。无法保证行为符合标准。

{{< sql_functions_zh "json" >}}

### Variant 函数

{{< sql_functions_zh "variant" >}}

### 值构建函数

{{< sql_functions_zh "valueconstruction" >}}

### 值获取函数

{{< sql_functions_zh "valueaccess" >}}

### 分组函数

{{< sql_functions_zh "grouping" >}}

### 哈希函数

{{< sql_functions_zh "hashfunctions" >}}

### 辅助函数

{{< sql_functions_zh "auxiliary" >}}

聚合函数
-------------------

聚合函数将所有的行作为输入，并返回单个聚合值作为结果。

{{< sql_functions_zh "aggregate" >}}

时间间隔单位和时间点单位标识符
---------------------------------------

下表列出了时间间隔单位和时间点单位标识符。

对于 Table API，请使用 `_` 代替空格（例如 `DAY_TO_HOUR`）。
Plural works for SQL only.

| 时间间隔单位                   | 时间点单位                        |
|:-------------------------|:-----------------------------|
| `MILLENNIUM`             |                              |
| `CENTURY`                |                              |
| `DECADE`                 |                              |
| `YEAR(S)`                | `YEAR`                       |
| `YEAR(S) TO MONTH(S)`    |                              |
| `QUARTER(S)`             | `QUARTER`                    |
| `MONTH(S)`               | `MONTH`                      |
| `WEEK(S)`                | `WEEK`                       |
| `DAY(S)`                 | `DAY`                        |
| `DAY(S) TO HOUR(S)`      |                              |
| `DAY(S) TO MINUTE(S)`    |                              |
| `DAY(S) TO SECOND(S)`    |                              |
| `HOUR(S)`                | `HOUR`                       |
| `HOUR(S) TO MINUTE(S)`   |                              |
| `HOUR(S) TO SECOND(S)`   |                              |
| `MINUTE(S)`              | `MINUTE`                     |
| `MINUTE(S) TO SECOND(S)` |                              |
| `SECOND(S)`              | `SECOND`                     |
| `MILLISECOND`            | `MILLISECOND`                |
| `MICROSECOND`            | `MICROSECOND`                |
| `NANOSECOND`             |                              |
| `EPOCH`                  |                              |
| `DOY` _（仅适用SQL）_         |                              |
| `DOW` _（仅适用SQL）_         |                              |
| `ISODOW` _（仅适用SQL）_      |                              |
| `ISOYEAR` _（仅适用SQL）_     |                              |
|                          | `SQL_TSI_YEAR` _（仅适用SQL）_    |
|                          | `SQL_TSI_QUARTER` _（仅适用SQL）_ |
|                          | `SQL_TSI_MONTH` _（仅适用SQL）_   |
|                          | `SQL_TSI_WEEK` _（仅适用SQL）_    |
|                          | `SQL_TSI_DAY` _（仅适用SQL）_     |
|                          | `SQL_TSI_HOUR` _（仅适用SQL）_    |
|                          | `SQL_TSI_MINUTE` _（仅适用SQL）_  |
|                          | `SQL_TSI_SECOND ` _（仅适用SQL）_ |

{{< top >}}

列函数
---------------------------------------

列函数用于选择或丢弃表的列。

{{< hint info >}}
列函数仅在 Table API 中使用。
{{< /hint >}}

| 语法                      | 描述                         |
| :----------------------- | :--------------------------- |
| withColumns(...)         | 选择指定的列                   |
| withoutColumns(...)      | 选择除指定列以外的列            |
| withAllColumns()    | select all columns (like `SELECT *` in SQL) |

详细语法如下：

```text
列函数:
    withColumns(columnExprs)
    withoutColumns(columnExprs)
    withAllColumns()

多列表达式:
    columnExpr [, columnExpr]*

单列表达式:
    columnRef | columnIndex to columnIndex | columnName to columnName

列引用:
    columnName(The field name that exists in the table) | columnIndex(a positive integer starting from 1)
```
列函数的用法如下表所示（假设我们有一个包含 5 列的表：`(a: Int, b: Long, c: String, d:String, e: String)`）：

| 接口 | 用法举例 | 描述 |
|-|-|-|
| withColumns($(*)) | select(withColumns($("*")))  = select($("a"), $("b"), $("c"), $("d"), $("e")) | 全部列 |
| withColumns(m to n) | select(withColumns(range(2, 4))) = select($("b"), $("c"), $("d")) | 第 m 到第 n 列 |
| withColumns(m, n, k)  | select(withColumns(lit(1), lit(3), $("e"))) = select($("a"), $("c"), $("e")) | 第 m、n、k 列 |
| withColumns(m, n to k)  | select(withColumns(lit(1), range(3, 5))) = select($("a"), $("c"), $("d"), $("e")) |  以上两种用法的混合 |
| withoutColumns(m to n) | select(withoutColumns(range(2, 4))) = select($("a"), $("e")) |  不选从第 m 到第 n 列 |
| withoutColumns(m, n, k) | select(withoutColumns(lit(1), lit(3), lit(5))) = select($("b"), $("d")) |  不选第 m、n、k 列 |
| withoutColumns(m, n to k) | select(withoutColumns(lit(1), range(3, 5))) = select($("b")) |  以上两种用法的混合 |

列函数可用于所有需要列字段的地方，例如 `select、groupBy、orderBy、UDFs` 等函数，例如：

{{< tabs "402fe551-5fb9-4b17-bd64-e05cbd56b4cc" >}}
{{< tab "Java" >}}
```java
table
    .groupBy(withColumns(range(1, 3)))
    .select(withColumns(range("a", "b")), myUDAgg(myUDF(withColumns(range(5, 20)))));
```
{{< /tab >}}
{{< tab "Scala" >}}
```scala
table
    .groupBy(withColumns(range(1, 3)))
    .select(withColumns('a to 'b), myUDAgg(myUDF(withColumns(5 to 20))))
```
{{< /tab >}}
{{< tab "Python" >}}
```python
table
    .group_by(with_columns(range_(1, 3)))
    .select(with_columns(range_('a', 'b')), myUDAgg(myUDF(with_columns(range_(5, 20)))))
```
{{< /tab >}}
{{< /tabs >}}

{{< top >}}

Named Arguments
---------------------------------------

By default, values and expressions are mapped to a function's arguments based on the position in the function call,
for example `f(42, true)`. All functions in both SQL and Table API support position-based arguments.

If the function declares a static signature, named arguments are available as a convenient alternative.
The framework is able to reorder named arguments and consider optional arguments accordingly, before passing them
into the function call. Thus, the order of arguments doesn't matter when calling a function and optional arguments
don't have to be provided.

In `DESCRIBE FUNCTION` and documentation a static signature is indicated by the `=>` assignment operator,
for example `f(left => INT, right => BOOLEAN)`. Note that not every function supports named arguments. Named
arguments are not available for signatures that are overloaded, use varargs, or any other kind of input type strategy.
User-defined functions with a single `eval()` method usually qualify for named arguments.

Named arguments can be used as shown below:

{{< tabs "902fe991-5fb9-4b17-ae99-f05cbd48b4dd" >}}
{{< tab "SQL" >}}
```text
SELECT MyUdf(input => my_column, threshold => 42)
```
{{< /tab >}}
{{< tab "Table API" >}}
```java
table.select(
  call(
    MyUdf.class,
    $("my_column").asArgument("input"),
    lit(42).asArgument("threshold")
  )
);
```
{{< /tab >}}
{{< /tabs >}}

{{< top >}}
