---
title: "CREATE Statements"
weight: 4
type: docs
aliases:
  - /dev/table/sql/create.html
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

# CREATE Statements

CREATE statements are used to register a table/view/function into current or specified [Catalog]({{< ref "docs/dev/table/catalogs" >}}). A registered table/view/function can be used in SQL queries.

Flink SQL supports the following CREATE statements for now:

- CREATE TABLE
- [CREATE OR] REPLACE TABLE
- CREATE CATALOG
- CREATE DATABASE
- CREATE VIEW
- CREATE FUNCTION
- CREATE MODEL

## Run a CREATE statement

{{< tabs "create" >}}
{{< tab "Java" >}}

CREATE statements can be executed with the `executeSql()` method of the `TableEnvironment`. The `executeSql()` method returns 'OK' for a successful CREATE operation, otherwise will throw an exception.

The following examples show how to run a CREATE statement in `TableEnvironment`.

{{< /tab >}}
{{< tab "Scala" >}}
CREATE statements can be executed with the `executeSql()` method of the `TableEnvironment`. The `executeSql()` method returns 'OK' for a successful CREATE operation, otherwise will throw an exception.

The following examples show how to run a CREATE statement in `TableEnvironment`.
{{< /tab >}}
{{< tab "Python" >}}

CREATE statements can be executed with the `execute_sql()` method of the `TableEnvironment`. The `execute_sql()` method returns 'OK' for a successful CREATE operation, otherwise will throw an exception.

The following examples show how to run a CREATE statement in `TableEnvironment`.

{{< /tab >}}
{{< tab "SQL CLI" >}}

CREATE statements can be executed in [SQL CLI]({{< ref "docs/dev/table/sqlClient" >}}).

The following examples show how to run a CREATE statement in SQL CLI.

{{< /tab >}}
{{< /tabs >}}

{{< tabs "0b1b298a-b92f-4f95-8d06-49544b487b75" >}}
{{< tab "Java" >}}
```java
TableEnvironment tableEnv = TableEnvironment.create(...);

// SQL query with a registered table
// register a table named "Orders"
tableEnv.executeSql("CREATE TABLE Orders (`user` BIGINT, product STRING, amount INT) WITH (...)");
// run a SQL query on the Table and retrieve the result as a new Table
Table result = tableEnv.sqlQuery(
  "SELECT product, amount FROM Orders WHERE product LIKE '%Rubber%'");

// Execute insert SQL with a registered table
// register a TableSink
tableEnv.executeSql("CREATE TABLE RubberOrders(product STRING, amount INT) WITH (...)");
// run an insert SQL on the Table and emit the result to the TableSink
tableEnv.executeSql(
  "INSERT INTO RubberOrders SELECT product, amount FROM Orders WHERE product LIKE '%Rubber%'");
```
{{< /tab >}}
{{< tab "Scala" >}}
```scala
val tableEnv = TableEnvironment.create(...)

// SQL query with a registered table
// register a table named "Orders"
tableEnv.executeSql("CREATE TABLE Orders (`user` BIGINT, product STRING, amount INT) WITH (...)")
// run a SQL query on the Table and retrieve the result as a new Table
val result = tableEnv.sqlQuery(
  "SELECT product, amount FROM Orders WHERE product LIKE '%Rubber%'")

// Execute insert SQL with a registered table
// register a TableSink
tableEnv.executeSql("CREATE TABLE RubberOrders(product STRING, amount INT) WITH ('connector.path'='/path/to/file' ...)")
// run an insert SQL on the Table and emit the result to the TableSink
tableEnv.executeSql(
  "INSERT INTO RubberOrders SELECT product, amount FROM Orders WHERE product LIKE '%Rubber%'")
```
{{< /tab >}}
{{< tab "Python" >}}
```python
table_env = TableEnvironment.create(...)

# SQL query with a registered table
# register a table named "Orders"
table_env.execute_sql("CREATE TABLE Orders (`user` BIGINT, product STRING, amount INT) WITH (...)");
# run a SQL query on the Table and retrieve the result as a new Table
result = table_env.sql_query(
  "SELECT product, amount FROM Orders WHERE product LIKE '%Rubber%'");

# Execute an INSERT SQL with a registered table
# register a TableSink
table_env.execute_sql("CREATE TABLE RubberOrders(product STRING, amount INT) WITH (...)")
# run an INSERT SQL on the Table and emit the result to the TableSink
table_env \
    .execute_sql("INSERT INTO RubberOrders SELECT product, amount FROM Orders WHERE product LIKE '%Rubber%'")
```
{{< /tab >}}
{{< tab "SQL CLI" >}}
```sql
Flink SQL> CREATE TABLE Orders (`user` BIGINT, product STRING, amount INT) WITH (...);
[INFO] Table has been created.

Flink SQL> CREATE TABLE RubberOrders (product STRING, amount INT) WITH (...);
[INFO] Table has been created.

Flink SQL> INSERT INTO RubberOrders SELECT product, amount FROM Orders WHERE product LIKE '%Rubber%';
[INFO] Submitting SQL update statement to the cluster...
```
{{< /tab >}}
{{< /tabs >}}

{{< top >}}

## CREATE TABLE

The following grammar gives an overview about the available syntax:

```text
CREATE TABLE [IF NOT EXISTS] [catalog_name.][db_name.]table_name
  (
    { <physical_column_definition> | <metadata_column_definition> | <computed_column_definition> }[ , ...n]
    [ <watermark_definition> ]
    [ <table_constraint> ][ , ...n]
  )
  [COMMENT table_comment]
  [PARTITIONED BY (partition_column_name1, partition_column_name2, ...)]
  [ <distribution> ]
  WITH (key1=val1, key2=val2, ...)
  [ LIKE source_table [( <like_options> )] | AS select_query ]
   
<physical_column_definition>:
  column_name column_type [ <column_constraint> ] [COMMENT column_comment]
  
<column_constraint>:
  [CONSTRAINT constraint_name] PRIMARY KEY NOT ENFORCED

<table_constraint>:
  [CONSTRAINT constraint_name] PRIMARY KEY (column_name, ...) NOT ENFORCED

<metadata_column_definition>:
  column_name column_type METADATA [ FROM metadata_key ] [ VIRTUAL ]

<computed_column_definition>:
  column_name AS computed_column_expression [COMMENT column_comment]

<watermark_definition>:
  WATERMARK FOR rowtime_column_name AS watermark_strategy_expression

<source_table>:
  [catalog_name.][db_name.]table_name

<like_options>:
{
   { INCLUDING | EXCLUDING } { ALL | CONSTRAINTS | DISTRIBUTION | PARTITIONS }
 | { INCLUDING | EXCLUDING | OVERWRITING } { GENERATED | OPTIONS | WATERMARKS } 
}[, ...]

<distribution>:
{
    DISTRIBUTED BY [ { HASH | RANGE } ] (bucket_column_name1, bucket_column_name2, ...) [INTO n BUCKETS]
  | DISTRIBUTED INTO n BUCKETS
}

```

The statement above creates a table with the given name. If a table with the same name already exists
in the catalog, an exception is thrown.

### Columns

**Physical / Regular Columns**

Physical columns are regular columns known from databases. They define the names, the types, and the
order of fields in the physical data. Thus, physical columns represent the payload that is read from
and written to an external system. Connectors and formats use these columns (in the defined order)
to configure themselves. Other kinds of columns can be declared between physical columns but will not
influence the final physical schema.

The following statement creates a table with only regular columns:

```sql
CREATE TABLE MyTable (
  `user_id` BIGINT,
  `name` STRING
) WITH (
  ...
);
```

**Metadata Columns**

Metadata columns are an extension to the SQL standard and allow to access connector and/or format specific
fields for every row of a table. A metadata column is indicated by the `METADATA` keyword. For example,
a metadata column can be be used to read and write the timestamp from and to Kafka records for time-based
operations. The [connector and format documentation]({{< ref "docs/connectors/table/overview" >}}) lists the
available metadata fields for every component. However, declaring a metadata column in a table's schema
is optional.

The following statement creates a table with an additional metadata column that references the metadata field `timestamp`:

```sql
CREATE TABLE MyTable (
  `user_id` BIGINT,
  `name` STRING,
  `record_time` TIMESTAMP_LTZ(3) METADATA FROM 'timestamp'    -- reads and writes a Kafka record's timestamp
) WITH (
  'connector' = 'kafka'
  ...
);
```

Every metadata field is identified by a string-based key and has a documented data type. For example,
the Kafka connector exposes a metadata field with key `timestamp` and data type `TIMESTAMP_LTZ(3)`
that can be used for both reading and writing records.

In the example above, the metadata column `record_time` becomes part of the table's schema and can be
transformed and stored like a regular column:

```sql
INSERT INTO MyTable SELECT user_id, name, record_time + INTERVAL '1' SECOND FROM MyTable;
```

For convenience, the `FROM` clause can be omitted if the column name should be used as the identifying metadata key:

```sql
CREATE TABLE MyTable (
  `user_id` BIGINT,
  `name` STRING,
  `timestamp` TIMESTAMP_LTZ(3) METADATA    -- use column name as metadata key
) WITH (
  'connector' = 'kafka'
  ...
);
```

For convenience, the runtime will perform an explicit cast if the data type of the column differs from
the data type of the metadata field. Of course, this requires that the two data types are compatible.

```sql
CREATE TABLE MyTable (
  `user_id` BIGINT,
  `name` STRING,
  `timestamp` BIGINT METADATA    -- cast the timestamp as BIGINT
) WITH (
  'connector' = 'kafka'
  ...
);
```

By default, the planner assumes that a metadata column can be used for both reading and writing. However,
in many cases an external system provides more read-only metadata fields than writable fields. Therefore,
it is possible to exclude metadata columns from persisting using the `VIRTUAL` keyword.

```sql
CREATE TABLE MyTable (
  `timestamp` BIGINT METADATA,       -- part of the query-to-sink schema
  `offset` BIGINT METADATA VIRTUAL,  -- not part of the query-to-sink schema
  `user_id` BIGINT,
  `name` STRING,
) WITH (
  'connector' = 'kafka'
  ...
);
```

In the example above, the `offset` is a read-only metadata column and excluded from the query-to-sink
schema. Thus, source-to-query schema (for `SELECT`) and query-to-sink (for `INSERT INTO`) schema differ:

```text
source-to-query schema:
MyTable(`timestamp` BIGINT, `offset` BIGINT, `user_id` BIGINT, `name` STRING)

query-to-sink schema:
MyTable(`timestamp` BIGINT, `user_id` BIGINT, `name` STRING)
```

**Computed Columns**

Computed columns are virtual columns that are generated using the syntax `column_name AS computed_column_expression`.

A computed column evaluates an expression that can reference other columns declared in the same table.
Both physical columns and metadata columns can be accessed. The column itself is not physically stored
within the table. The column's data type is derived automatically from the given expression and does
not have to be declared manually.

The planner will transform computed columns into a regular projection after the source. For optimization
or [watermark strategy push down]({{< ref "docs/dev/table/sourcesSinks" >}}), the evaluation might be spread
across operators, performed multiple times, or skipped if not needed for the given query.

For example, a computed column could be defined as:
```sql
CREATE TABLE MyTable (
  `user_id` BIGINT,
  `price` DOUBLE,
  `quantity` DOUBLE,
  `cost` AS price * quantity  -- evaluate expression and supply the result to queries
) WITH (
  'connector' = 'kafka'
  ...
);
```

The expression may contain any combination of columns, constants, or functions. The expression cannot
contain a subquery.

Computed columns are commonly used in Flink for defining [time attributes]({{< ref "docs/dev/table/concepts/time_attributes" >}})
in `CREATE TABLE` statements.
- A [processing time attribute]({{< ref "docs/dev/table/concepts/time_attributes" >}}#processing-time)
can be defined easily via `proc AS PROCTIME()` using the system's `PROCTIME()` function.
- An [event time attribute]({{< ref "docs/dev/table/concepts/time_attributes" >}}#event-time) timestamp
can be pre-processed before the `WATERMARK` declaration. For example, the computed column can be used
if the original field is not `TIMESTAMP(3)` type or is nested in a JSON string.

Similar to virtual metadata columns, computed columns are excluded from persisting. Therefore, a computed
column cannot be the target of an `INSERT INTO` statement. Thus, source-to-query schema (for `SELECT`)
and query-to-sink (for `INSERT INTO`) schema differ:

```text
source-to-query schema:
MyTable(`user_id` BIGINT, `price` DOUBLE, `quantity` DOUBLE, `cost` DOUBLE)

query-to-sink schema:
MyTable(`user_id` BIGINT, `price` DOUBLE, `quantity` DOUBLE)
```

### `WATERMARK`

The `WATERMARK` clause defines the event time attributes of a table and takes the form `WATERMARK FOR rowtime_column_name AS watermark_strategy_expression`.

The  `rowtime_column_name` defines an existing column that is marked as the event time attribute of the table. The column must be of type `TIMESTAMP(3)` and be a top-level column in the schema. It may be a computed column.

The `watermark_strategy_expression` defines the watermark generation strategy. It allows arbitrary non-query expression, including computed columns, to calculate the watermark. The expression return type must be TIMESTAMP(3), which represents the timestamp since the Epoch.
The returned watermark will be emitted only if it is non-null and its value is larger than the previously emitted local watermark (to preserve the contract of ascending watermarks). The watermark generation expression is evaluated by the framework for every record.
The framework will periodically emit the largest generated watermark. If the current watermark is still identical to the previous one, or is null, or the value of the returned watermark is smaller than that of the last emitted one, then no new watermark will be emitted.
Watermark is emitted in an interval defined by [`pipeline.auto-watermark-interval`]({{< ref "docs/deployment/config" >}}#pipeline-auto-watermark-interval) configuration.
If watermark interval is `0ms`, the generated watermarks will be emitted per-record if it is not null and greater than the last emitted one.

When using event time semantics, tables must contain an event time attribute and watermarking strategy.

Flink provides several commonly used watermark strategies.

- Strictly ascending timestamps: `WATERMARK FOR rowtime_column AS rowtime_column`.

  Emits a watermark of the maximum observed timestamp so far. Rows that have a timestamp bigger to the max timestamp are not late.

- Ascending timestamps: `WATERMARK FOR rowtime_column AS rowtime_column - INTERVAL '0.001' SECOND`.

  Emits a watermark of the maximum observed timestamp so far minus 1. Rows that have a timestamp bigger or equal to the max timestamp are not late.

- Bounded out of orderness timestamps: `WATERMARK FOR rowtime_column AS rowtime_column - INTERVAL 'string' timeUnit`.

  Emits watermarks, which are the maximum observed timestamp minus the specified delay, e.g., `WATERMARK FOR rowtime_column AS rowtime_column - INTERVAL '5' SECOND` is a 5 seconds delayed watermark strategy.

```sql
CREATE TABLE Orders (
    `user` BIGINT,
    product STRING,
    order_time TIMESTAMP(3),
    WATERMARK FOR order_time AS order_time - INTERVAL '5' SECOND
) WITH ( . . . );
```

### `PRIMARY KEY`

Primary key constraint is a hint for Flink to leverage for optimizations. It tells that a column or a set of columns of a table or a view are unique and they **do not** contain null.
Neither of columns in a primary can be nullable. Primary key therefore uniquely identify a row in a table.

Primary key constraint can be either declared along with a column definition (a column constraint) or as a single line (a table constraint).
For both cases, it should only be declared as a singleton. If you define multiple primary key constraints at the same time, an exception would be thrown.

**Validity Check**

SQL standard specifies that a constraint can either be `ENFORCED` or `NOT ENFORCED`. This controls if the constraint checks are performed on the incoming/outgoing data.
Flink does not own the data therefore the only mode we want to support is the `NOT ENFORCED` mode.
It is up to the user to ensure that the query enforces key integrity.

Flink will assume correctness of the primary key by assuming that the columns nullability is aligned with the columns in primary key. Connectors should ensure those are aligned.

**Notes:** In a CREATE TABLE statement, creating a primary key constraint will alter the columns nullability, that means, a column with primary key constraint is not nullable.

### `PARTITIONED BY`

Partition the created table by the specified columns. A directory is created for each partition if this table is used as a filesystem sink.

### `DISTRIBUTED`

Buckets enable load balancing in an external storage system by splitting data into disjoint subsets. These subsets group rows with potentially "infinite" keyspace into smaller and more manageable chunks that allow for efficient parallel processing.

Bucketing depends heavily on the semantics of the underlying connector. However, a user can influence the bucketing behavior by specifying the number of buckets, the bucketing algorithm, and (if the algorithm allows it) the columns which are used for target bucket calculation.

All bucketing components (i.e. bucket number, distribution algorithm, bucket key columns) are
optional from a SQL syntax perspective. 

Given the following SQL statements:

```sql
-- Example 1
CREATE TABLE MyTable (uid BIGINT, name STRING) DISTRIBUTED BY HASH(uid) INTO 4 BUCKETS;

-- Example 2
CREATE TABLE MyTable (uid BIGINT, name STRING) DISTRIBUTED BY (uid) INTO 4 BUCKETS;

-- Example 3
CREATE TABLE MyTable (uid BIGINT, name STRING) DISTRIBUTED BY (uid);

-- Example 4
CREATE TABLE MyTable (uid BIGINT, name STRING) DISTRIBUTED INTO 4 BUCKETS;
```

Example 1 declares a hash function on a fixed number of 4 buckets (i.e. HASH(uid) % 4 = target
bucket). Example 2 leaves the selection of an algorithm up to the connector. Additionally, 
Example 3 leaves the number of buckets up  to the connector. 
In contrast, Example 4 only defines the number of buckets.

### `WITH` Options

Table properties used to create a table source/sink. The properties are usually used to find and create the underlying connector.

The key and value of expression `key1=val1` should both be string literal. See details in [Connect to External Systems]({{< ref "docs/connectors/table/overview" >}}) for all the supported table properties of different connectors.

**Notes:** The table name can be of three formats: 1. `catalog_name.db_name.table_name` 2. `db_name.table_name` 3. `table_name`. For `catalog_name.db_name.table_name`, the table would be registered into metastore with catalog named "catalog_name" and database named "db_name"; for `db_name.table_name`, the table would be registered into the current catalog of the execution table environment and database named "db_name"; for `table_name`, the table would be registered into the current catalog and database of the execution table environment.

**Notes:** The table registered with `CREATE TABLE` statement can be used as both table source and table sink, we can not decide if it is used as a source or sink until it is referenced in the DMLs.

### `LIKE`

The `LIKE` clause is a variant/combination of SQL features (Feature T171, "LIKE clause in table definition" and Feature T173, "Extended LIKE clause in table definition"). The clause can be used to create a table based on a definition of an existing table. Additionally, users
can extend the original table or exclude certain parts of it. In contrast to the SQL standard the clause must be defined at the top-level of a CREATE statement. That is because the clause applies to multiple parts of the definition and not only to the schema part.

You can use the clause to reuse (and potentially overwrite) certain connector properties or add watermarks to tables defined externally. For example, you can add a watermark to a table defined in Apache Hive. 

Consider the example statement below:
```sql
CREATE TABLE Orders (
    `user` BIGINT,
    product STRING,
    order_time TIMESTAMP(3)
) WITH ( 
    'connector' = 'kafka',
    'scan.startup.mode' = 'earliest-offset'
);

CREATE TABLE Orders_with_watermark (
    -- Add watermark definition
    WATERMARK FOR order_time AS order_time - INTERVAL '5' SECOND 
) WITH (
    -- Overwrite the startup-mode
    'scan.startup.mode' = 'latest-offset'
)
LIKE Orders;
```

The resulting table `Orders_with_watermark` will be equivalent to a table created with a following statement:
```sql
CREATE TABLE Orders_with_watermark (
    `user` BIGINT,
    product STRING,
    order_time TIMESTAMP(3),
    WATERMARK FOR order_time AS order_time - INTERVAL '5' SECOND 
) WITH (
    'connector' = 'kafka',
    'scan.startup.mode' = 'latest-offset'
);
```

The merging logic of table features can be controlled with `like options`.

You can control the merging behavior of:

* CONSTRAINTS - constraints such as primary and unique keys
* GENERATED - computed columns
* METADATA - metadata columns
* OPTIONS - connector options that describe connector and format properties
* DISTRIBUTION - distribution definition
* PARTITIONS - partition of the tables
* WATERMARKS - watermark declarations

with three different merging strategies:

* INCLUDING - Includes the feature of the source table, fails on duplicate entries, e.g. if an option with the same key exists in both tables.
* EXCLUDING - Does not include the given feature of the source table.
* OVERWRITING - Includes the feature of the source table, overwrites duplicate entries of the source table with properties of the new table, e.g. if an option with the same key exists in both tables, the one from the current statement will be used.

Additionally, you can use the `INCLUDING/EXCLUDING ALL` option to specify what should be the strategy if there was no specific strategy defined, i.e. if you use `EXCLUDING ALL INCLUDING WATERMARKS` only the watermarks will be included from the source table.

Example:
```sql
-- A source table stored in a filesystem
CREATE TABLE Orders_in_file (
    `user` BIGINT,
    product STRING,
    order_time_string STRING,
    order_time AS to_timestamp(order_time_string)
    
)
PARTITIONED BY (`user`) 
WITH ( 
    'connector' = 'filesystem',
    'path' = '...'
);

-- A corresponding table we want to store in kafka
CREATE TABLE Orders_in_kafka (
    -- Add watermark definition
    WATERMARK FOR order_time AS order_time - INTERVAL '5' SECOND 
) WITH (
    'connector' = 'kafka',
    ...
)
LIKE Orders_in_file (
    -- Exclude everything besides the computed columns which we need to generate the watermark for.
    -- We do not want to have the partitions or filesystem options as those do not apply to kafka. 
    EXCLUDING ALL
    INCLUDING GENERATED
);
```

If you provide no like options, `INCLUDING ALL OVERWRITING OPTIONS` will be used as a default.

**NOTE** You cannot control the behavior of merging physical columns. Those will be merged as if you applied the `INCLUDING` strategy.

**NOTE** The `source_table` can be a compound identifier. Thus, it can be a table from a different catalog or database: e.g. `my_catalog.my_db.MyTable` specifies table `MyTable` from catalog `MyCatalog` and database `my_db`; `my_db.MyTable` specifies table `MyTable` from current catalog and database `my_db`.

### `AS select_statement`

Tables can also be created and populated by the results of a query in one create-table-as-select (CTAS) statement. CTAS is the simplest and fastest way to create and insert data into a table with a single command.

There are two parts in CTAS, the SELECT part can be any [SELECT query]({{< ref "docs/dev/table/sql/queries/overview" >}}) supported by Flink SQL. The CREATE part takes the resulting schema from the SELECT part and creates the target table. Similar to `CREATE TABLE`, CTAS requires the required options of the target table must be specified in WITH clause.

The creating table operation of CTAS depends on the target Catalog. For example, Hive Catalog creates the physical table in Hive automatically. But the in-memory catalog registers the table metadata in the client's memory where the SQL is executed.

Consider the example statement below:

```sql
CREATE TABLE my_ctas_table
WITH (
    'connector' = 'kafka',
    ...
)
AS SELECT id, name, age FROM source_table WHERE mod(id, 10) = 0;
```

The resulting table `my_ctas_table` will be equivalent to create the table and insert the data with the following statement:
```sql
CREATE TABLE my_ctas_table (
    id BIGINT,
    name STRING,
    age INT
) WITH (
    'connector' = 'kafka',
    ...
);
 
INSERT INTO my_ctas_table SELECT id, name, age FROM source_table WHERE mod(id, 10) = 0;
```

The `CREATE` part allows you to specify explicit columns. The resulting table schema will contain the columns defined in the `CREATE` part first followed by the columns from the `SELECT` part. Columns named in both parts, in the `CREATE` and `SELECT` parts, keep the same column position as defined in the `SELECT` part. The data type of `SELECT` columns can also be overridden if specified in the `CREATE` part.

Consider the example statement below:

```sql
CREATE TABLE my_ctas_table (
    desc STRING,
    quantity DOUBLE,   
    cost AS price * quantity,
    WATERMARK FOR order_time AS order_time - INTERVAL '5' SECOND,
) WITH (
    'connector' = 'kafka',
    ...
) AS SELECT id, price, quantity, order_time FROM source_table;
```

The resulting table `my_ctas_table` will be equivalent to create the following table and insert the data with the following statement:

```
CREATE TABLE my_ctas_table (
    desc STRING,
    cost AS price * quantity,
    id BIGINT,
    price DOUBLE,
    quantity DOUBLE,
    order_time TIMESTAMP(3),
    WATERMARK FOR order_time AS order_time - INTERVAL '5' SECOND
) WITH (
    'connector' = 'kafka',
    ...
);

INSERT INTO my_ctas_table (id, price, quantity, order_time)
    SELECT id, price, quantity, order_time FROM source_table;
```

The `CREATE` part also lets you specify primary keys and distribution strategies. Notice that primary keys work only on `NOT NULL` columns. Currently, primary keys only allow you to define columns from the `SELECT` part which may be `NOT NULL`. The `CREATE` part does not allow `NOT NULL` column definitions.

Consider the example statement below where `id` is a not null column in the `SELECT` part:

```sql
CREATE TABLE my_ctas_table (
    PRIMARY KEY (id) NOT ENFORCED
) DISTRIBUTED BY (id) INTO 4 buckets 
AS SELECT id, name FROM source_table;
```

The resulting table `my_ctas_table` will be equivalent to create the following table and insert the data with the following statement:

```
CREATE TABLE my_ctas_table (
    id BIGINT NOT NULL PRIMARY KEY NOT ENFORCED,
    name STRING 
) DISTRIBUTED BY (id) INTO 4 buckets;

INSERT INTO my_ctas_table SELECT id, name FROM source_table;
```

`CTAS` also allows you to reorder the columns defined in the `SELECT` part by specifying all column names without data types in the `CREATE` part. This feature is equivalent to the `INSERT INTO` statement.
The columns specified must match the names and number of columns in the `SELECT` part. This definition cannot be combined with new columns, which requires defining data types.

Consider the example statement below:

```sql
CREATE TABLE my_ctas_table (
    order_time, price, quantity, id
) WITH (
    'connector' = 'kafka',
    ...
) AS SELECT id, price, quantity, order_time FROM source_table;
```

The resulting table `my_ctas_table` will be equivalent to create the following table and insert the data with the following statement:

```
CREATE TABLE my_ctas_table (
    order_time TIMESTAMP(3),
    price DOUBLE,
    quantity DOUBLE,
    id BIGINT
) WITH (
    'connector' = 'kafka',
    ...
);

INSERT INTO my_ctas_table (order_time, price, quantity, id)
    SELECT id, price, quantity, order_time FROM source_table;
```

**Note:** CTAS has these restrictions:
* Does not support creating a temporary table yet.
* Does not support creating partitioned table yet.

**Note:** By default, CTAS is non-atomic which means the table created won't be dropped automatically if occur errors while inserting data into the table.

#### Atomicity

If you want to enable atomicity for CTAS, then you should make sure:
* The sink has implemented the atomicity semantics for CTAS. You may refer to the doc for the corresponding connector sink to know the atomicity semantics is available or not. For devs who want to implement the atomicity semantics, please refer to the doc [SupportsStaging]({{< ref "docs/dev/table/sourcesSinks" >}}#sink-abilities).
* Set option [table.rtas-ctas.atomicity-enabled]({{< ref "docs/dev/table/config" >}}#table-rtas-ctas-atomicity-enabled) to `true`.

{{< top >}}

## [CREATE OR] REPLACE TABLE
```sql
[CREATE OR] REPLACE TABLE [catalog_name.][db_name.]table_name
  [(
    { <physical_column_definition> | <metadata_column_definition> | <computed_column_definition> }[ , ...n]
    [ <watermark_definition> ]
    [ <table_constraint> ][ , ...n]
  )]
[COMMENT table_comment]
[ <distribution> ]
WITH (key1=val1, key2=val2, ...)
AS select_query
```

**Note:** RTAS has the following semantic:
* REPLACE TABLE AS SELECT statement: the target table to be replaced must exist, otherwise, an exception will be thrown.
* CREATE OR REPLACE TABLE AS SELECT statement: the target table to be replaced will be created if it does not exist; if it does exist, it'll be replaced.

Tables can be replaced(or created) and populated by the results of a query in one [CREATE OR] REPLACE TABLE AS SELECT(RTAS) statement. RTAS is the simplest and fastest way to replace(or create) and insert data into a table with a single command.

There are two parts in RTAS: the SELECT part can be any [SELECT query]({{< ref "docs/dev/table/sql/queries/overview" >}}) supported by Flink SQL, the `REPLACE TABLE` part takes the resulting schema from the `SELECT` part and replace the target table. Similar to `CREATE TABLE` and `CTAS`, RTAS requires the required options of the target table must be specified in WITH clause.

Consider the example statement below:

```sql
REPLACE TABLE my_rtas_table
WITH (
    'connector' = 'kafka',
    ...
)
AS SELECT id, name, age FROM source_table WHERE mod(id, 10) = 0;
```

The `REPLACE TABLE AS SELECT` statement is equivalent to first drop the table, then create the table and insert the data with the following statement:
```sql
DROP TABLE my_rtas_table;

CREATE TABLE my_rtas_table (
    id BIGINT,
    name STRING,
    age INT
) WITH (
    'connector' = 'kafka',
    ...
);
 
INSERT INTO my_rtas_table SELECT id, name, age FROM source_table WHERE mod(id, 10) = 0;
```

Similar to `CREATE TABLE AS`, `REPLACE TABLE AS` allows you to specify explicit columns, watermarks, primary keys and distribution strategies. The resulting table schema is built from the `CREATE` part first followed by the columns from the `SELECT` part. Columns named in both parts, in the `CREATE` and `SELECT` parts, keep the same column position as defined in the `SELECT` part. The data type of `SELECT` columns can also be overridden if specified in the `CREATE` part.

Consider the example statement below:

```sql
REPLACE TABLE my_rtas_table (
    desc STRING,
    quantity DOUBLE,   
    cost AS price * quantity,
    WATERMARK FOR order_time AS order_time - INTERVAL '5' SECOND,
    PRIMARY KEY (id) NOT ENFORCED
) DISTRIBUTED BY (id) INTO 4 buckets
AS SELECT id, price, quantity, order_time FROM source_table;
```

The resulting table `my_rtas_table` will be equivalent to create the following table and insert the data with the following statement:

```sql
DROP TABLE my_rtas_table;

CREATE TABLE my_rtas_table (
    desc STRING,
    cost AS price * quantity,
    id BIGINT NOT NULL PRIMARY KEY NOT ENFORCED,
    price DOUBLE,
    quantity DOUBLE,
    order_time TIMESTAMP(3),
    WATERMARK FOR order_time AS order_time - INTERVAL '5' SECOND
) WITH (
    'connector' = 'kafka',
    ...
);

INSERT INTO my_rtas_table (id, price, quantity, order_time)
    SELECT id, price, quantity, order_time FROM source_table;
```

**Note:** RTAS has these restrictions:

* Does not support replacing a temporary table yet.
* Does not support creating partitioned table yet.

**Note:** By default, RTAS is non-atomic which means the table won't be dropped or restored to its origin automatically if occur errors while inserting data into the table.
**Note:** RTAS will drop the table first, then create the table and insert the data. But if the table is in the in-memory catalog, dropping table will only remove it from the catalog without removing the data in the physical table. So, the data before executing RTAS statement will still exist.

### Atomicity

If you want to enable atomicity for RTAS, then you should make sure:
* The sink has implemented the atomicity semantics for RTAS. You may refer to the doc for the corresponding connector sink to know the atomicity semantics is available or not. For devs who want to implement the atomicity semantics, please refer to the doc [SupportsStaging]({{< ref "docs/dev/table/sourcesSinks" >}}#sink-abilities).
* Set option [table.rtas-ctas.atomicity-enabled]({{< ref "docs/dev/table/config" >}}#table-rtas-ctas-atomicity-enabled) to `true`.

{{< top >}}

## CREATE CATALOG

```sql
CREATE CATALOG [IF NOT EXISTS] catalog_name
  [COMMENT catalog_comment]
  WITH (key1=val1, key2=val2, ...)
```

Create a catalog with the given catalog properties. If a catalog with the same name already exists, an exception is thrown.

**IF NOT EXISTS**

If the catalog already exists, nothing happens.

**WITH OPTIONS**

Catalog properties used to store extra information related to this catalog.
The key and value of expression `key1=val1` should both be string literal.

Check out more details at [Catalogs]({{< ref "docs/dev/table/catalogs" >}}).

{{< top >}}

## CREATE DATABASE

```sql
CREATE DATABASE [IF NOT EXISTS] [catalog_name.]db_name
  [COMMENT database_comment]
  WITH (key1=val1, key2=val2, ...)
```

Create a database with the given database properties. If a database with the same name already exists in the catalog, an exception is thrown.

**IF NOT EXISTS**

If the database already exists, nothing happens.

**WITH OPTIONS**

Database properties used to store extra information related to this database.
The key and value of expression `key1=val1` should both be string literal.

{{< top >}}

## CREATE VIEW
```sql
CREATE [TEMPORARY] VIEW [IF NOT EXISTS] [catalog_name.][db_name.]view_name
  [( columnName [, columnName ]* )] [COMMENT view_comment]
  AS query_expression
```

Create a view with the given query expression. If a view with the same name already exists in the catalog, an exception is thrown.

**TEMPORARY**

Create temporary view that has catalog and database namespaces and overrides views.

**IF NOT EXISTS**

If the view already exists, nothing happens.

{{< top >}}

## CREATE FUNCTION
```sql
CREATE [TEMPORARY|TEMPORARY SYSTEM] FUNCTION 
  [IF NOT EXISTS] [catalog_name.][db_name.]function_name 
  AS identifier [LANGUAGE JAVA|SCALA|PYTHON] 
  [USING JAR '<path_to_filename>.jar' [, JAR '<path_to_filename>.jar']* ]
```

Create a catalog function that has catalog and database namespaces with the identifier and optional language tag. If a function with the same name already exists in the catalog, an exception is thrown.

If the language tag is JAVA/SCALA, the identifier is the full classpath of the UDF. For the implementation of Java/Scala UDF, please refer to [User-defined Functions]({{< ref "docs/dev/table/functions/udfs" >}}) for more details.

If the language tag is PYTHON, the identifier is the fully qualified name of the UDF, e.g. `pyflink.table.tests.test_udf.add`. For the implementation of Python UDF, please refer to [Python UDFs]({{< ref "docs/dev/python/table/udfs/python_udfs" >}}) for more details.

If the language tag is PYTHON, however the current program is written in Java/Scala or pure SQL, then you need to [configure the Python dependencies]({{< ref "docs/dev/python/dependency_management" >}}#python-dependency-in-javascala-program).

**TEMPORARY**

Create temporary catalog function that has catalog and database namespaces and overrides catalog functions.

**TEMPORARY SYSTEM**

Create temporary system function that has no namespace and overrides built-in functions

**IF NOT EXISTS**

If the function already exists, nothing happens.

**LANGUAGE JAVA\|SCALA\|PYTHON**

Language tag to instruct Flink runtime how to execute the function. Currently only JAVA, SCALA and PYTHON are supported, the default language for a function is JAVA. 

**USING**

Specifies the list of jar resources that contain the implementation of the function along with its dependencies. The jar should be located in a local or remote [file system]({{< ref "docs/deployment/filesystems/overview" >}}) such as hdfs/s3/oss which Flink current supports. 

<span class="label label-danger">Attention</span> Currently only JAVA, SCALA language support USING clause.

{{< top >}}

## CREATE MODEL
```sql
CREATE [TEMPORARY] MODEL [IF NOT EXISTS] [catalog_name.][db_name.]model_name
  [(
    { <input_column_definition> }[ , ...n]
    { <output_column_definition> }[ , ...n]
  )]
  [COMMENT model_comment]
  WITH (key1=val1, key2=val2, ...)

<input_column_definition>:
  column_name column_type [COMMENT column_comment]

<output_column_definition>:
  column_name column_type [COMMENT column_comment]
```

Create a model with optional input and output column definitions. If a model with the same name already exists in the catalog, an exception is thrown.

**TEMPORARY**

Create a temporary model that has catalog and database namespaces and overrides models.

**IF NOT EXISTS**

If the model already exists, nothing happens.

**Input/Output Columns**

The input columns define the features that will be used for model inference. The output columns define the predictions that the model will produce. Each column must have a name and data type. 

**WITH OPTIONS**

Model properties used to store extra information related to this model. The properties are usually used to find and create the underlying model provider.
The key and value of expression `key1=val1` should both be string literal.

**Note:** The model properties and supported model types may vary depending on the underlying model provider.

### Examples

The following examples illustrate the usage of the `CREATE MODEL` statements.

```sql
CREATE MODEL sentiment_analysis_model 
INPUT (text STRING COMMENT 'Input text for sentiment analysis') 
OUTPUT (sentiment STRING COMMENT 'Predicted sentiment (positive/negative/neutral/mixed)')
COMMENT 'A model for sentiment analysis of text'
WITH (
    'provider' = 'openai',
    'endpoint' = 'https://api.openai.com/v1/chat/completions',
    'api-key' = '<YOUR KEY>',
    'model'='gpt-3.5-turbo',
    'system-prompt' = 'Classify the text below into one of the following labels: [positive, negative, neutral, mixed]. Output only the label.'
);
```

{{< top >}}
