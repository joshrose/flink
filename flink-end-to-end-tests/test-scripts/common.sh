#!/usr/bin/env bash
################################################################################
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
################################################################################

# Enable this line when developing a new end-to-end test
#set -Eexuo pipefail
set -o pipefail

if [[ -z "${FLINK_DIR:-}" ]]; then
    echo "FLINK_DIR needs to point to a Flink distribution directory"
    exit 1
fi

if [ -z "$FLINK_LOG_DIR" ] ; then
    export FLINK_LOG_DIR="$FLINK_DIR/log"
fi

case "$(uname -s)" in
    Linux*)     OS_TYPE=linux;;
    Darwin*)    OS_TYPE=mac;;
    CYGWIN*)    OS_TYPE=cygwin;;
    MINGW*)     OS_TYPE=mingw;;
    *)          OS_TYPE="UNKNOWN:${unameOut}"
esac

export EXIT_CODE=0
export TASK_SLOTS_PER_TM_HA=4

echo "Flink dist directory: $FLINK_DIR"

TEST_ROOT=`pwd -P`
TEST_INFRA_DIR="$END_TO_END_DIR/test-scripts/"
cd $TEST_INFRA_DIR
TEST_INFRA_DIR=`pwd -P`
cd $TEST_ROOT

source "${TEST_INFRA_DIR}/common_utils.sh"
source "${FLINK_DIR}/bin/bash-java-utils.sh"

if [[ -z "${FLINK_CONF_DIR:-}" ]]; then
    FLINK_CONF_DIR="$FLINK_DIR/conf"
fi
FLINK_CONF=${FLINK_CONF_DIR}/config.yaml
setJavaRun "$FLINK_CONF"

# Flatten the configuration file config.yaml to enable end-to-end test cases which will modify 
# it directly through shell scripts.
output=$(updateAndGetFlinkConfiguration "${FLINK_CONF_DIR}" "${FLINK_DIR}/bin" "${FLINK_DIR}/lib" -flatten)
echo "$output" > $FLINK_CONF

NODENAME=${NODENAME:-"localhost"}

# REST_PROTOCOL and CURL_SSL_ARGS can be modified in common_ssl.sh if SSL is activated
# they should be used in curl command to query Flink REST API
REST_PROTOCOL="http"
CURL_SSL_ARGS=""
source "${TEST_INFRA_DIR}/common_ssl.sh"

function set_hadoop_classpath {
  YARN_CLASSPATH_LOCATION="${TEST_INFRA_DIR}/../../flink-yarn-tests/target/yarn.classpath";
  if [ ! -f $YARN_CLASSPATH_LOCATION ]; then
    echo "File '$YARN_CLASSPATH_LOCATION' does not exist."
    exit 1
  fi
  export HADOOP_CLASSPATH=`cat $YARN_CLASSPATH_LOCATION`
}

function print_mem_use_osx {
    declare -a mem_types=("active" "inactive" "wired down")
    used=""
    for mem_type in "${mem_types[@]}"
    do
       used_type=$(vm_stat | grep "Pages ${mem_type}:" | awk '{print $NF}' | rev | cut -c 2- | rev)
       let used_type="(${used_type}*4096)/1024/1024"
       used="$used $mem_type=${used_type}MB"
    done
    let mem=$(sysctl -n hw.memsize)/1024/1024
    echo "Memory Usage: ${used} total=${mem}MB"
}

function print_mem_use {
    if [[ "$OS_TYPE" == "mac" ]]; then
        print_mem_use_osx
    else
        free -m | awk 'NR==2{printf "Memory Usage: used=%sMB total=%sMB %.2f%%\n", $3,$2,$3*100/$2 }'
    fi
}

BACKUP_FLINK_DIRS="conf lib plugins"

function backup_flink_dir() {
    mkdir -p "${TEST_DATA_DIR}/tmp/backup"
    # Note: not copying all directory tree, as it may take some time on some file systems.
    for dirname in ${BACKUP_FLINK_DIRS}; do
        cp -r "${FLINK_DIR}/${dirname}" "${TEST_DATA_DIR}/tmp/backup/"
    done
}

function revert_flink_dir() {

    for dirname in ${BACKUP_FLINK_DIRS}; do
        if [ -d "${TEST_DATA_DIR}/tmp/backup/${dirname}" ]; then
            rm -rf "${FLINK_DIR}/${dirname}"
            mv "${TEST_DATA_DIR}/tmp/backup/${dirname}" "${FLINK_DIR}/"
        fi
    done

    rm -r "${TEST_DATA_DIR}/tmp/backup"

    REST_PROTOCOL="http"
    CURL_SSL_ARGS=""
}

function add_optional_lib() {
    local lib_name=$1
    cp "$FLINK_DIR/opt/flink-${lib_name}"*".jar" "$FLINK_DIR/lib"
}

function add_optional_plugin() {
    # This is similar to add_optional_lib, but the jar would be copied to
    # Flink's plugins dir (the nested folder name does not matter).
    # Note: this may not work with some jars, as not all of them implement plugin api.
    # Please check the corresponding code of the jar.
    local plugin="$1"
    local plugin_dir="$FLINK_DIR/plugins/$plugin"

    mkdir -p "$plugin_dir"
    cp "$FLINK_DIR/opt/flink-$plugin"*".jar" "$plugin_dir"
}

function swap_planner_loader_with_planner_scala() {
  mv "$FLINK_DIR/lib/flink-table-planner-loader"*".jar" "$FLINK_DIR/opt"
  mv "$FLINK_DIR/opt/flink-table-planner_"*".jar" "$FLINK_DIR/lib"
}

function swap_planner_scala_with_planner_loader() {
  mv "$FLINK_DIR/opt/flink-table-planner-loader"*".jar" "$FLINK_DIR/lib"
  mv "$FLINK_DIR/lib/flink-table-planner_"*".jar" "$FLINK_DIR/opt"
}

function delete_config_key() {
    local config_key=$1
    sed -i -e "/^${config_key}: /d" $FLINK_CONF
}

function set_config_key() {
    local config_key=$1
    local value=$2
    delete_config_key ${config_key}
    echo "$config_key: $value" >> $FLINK_CONF
}

function create_ha_config() {

    # clean up the dir that will be used for zookeeper storage
    # (see high-availability.zookeeper.storageDir below)
    if [ -e $TEST_DATA_DIR/recovery ]; then
       echo "File ${TEST_DATA_DIR}/recovery exists. Deleting it..."
       rm -rf $TEST_DATA_DIR/recovery
    fi

    # create the masters file (only one currently).
    # This must have all the masters to be used in HA.
    echo "localhost:8081" > ${FLINK_DIR}/conf/masters

    # then move on to create the config.yaml
    #==============================================================================
    # Common
    #==============================================================================

    set_config_key "jobmanager.rpc.address" "localhost"
    set_config_key "jobmanager.rpc.port" "6123"
    set_config_key "jobmanager.memory.process.size" "1024m"
    set_config_key "taskmanager.memory.process.size" "1024m"
    set_config_key "taskmanager.numberOfTaskSlots" "${TASK_SLOTS_PER_TM_HA}"

    #==============================================================================
    # High Availability
    #==============================================================================

    set_config_key "high-availability.type" "zookeeper"
    set_config_key "high-availability.zookeeper.storageDir" "file://${TEST_DATA_DIR}/recovery/"
    set_config_key "high-availability.zookeeper.quorum" "localhost:2181"
    set_config_key "high-availability.zookeeper.path.root" "/flink"
    set_config_key "high-availability.cluster-id" "/test_cluster_one"

    #==============================================================================
    # Web Frontend
    #==============================================================================

    set_config_key "rest.port" "8081"

    set_config_key "queryable-state.server.ports" "9000-9009"
    set_config_key "queryable-state.proxy.ports" "9010-9019"
}

function get_node_ip {
    local ip_addr

    if [[ ${OS_TYPE} == "linux" ]]; then
        ip_addr=$(hostname -I)
    elif [[ ${OS_TYPE} == "mac" ]]; then
        ip_addr=$(
            ifconfig |
            grep -E "([0-9]{1,3}\.){3}[0-9]{1,3}" | # grep IPv4 addresses only
            grep -v 127.0.0.1 |                     # do not use 127.0.0.1 (to be consistent with hostname -I)
            awk '{ print $2 }' |                    # extract ip from row
            paste -sd " " -                         # combine everything to one line
        )
    else
        echo "Warning: Unsupported OS_TYPE '${OS_TYPE}' for 'get_node_ip'. Falling back to 'hostname -I' (linux)"
        ip_addr=$(hostname -I)
    fi

    echo ${ip_addr}
}

function start_ha_cluster {
    create_ha_config
    start_local_zk
    start_cluster
}

function start_local_zk {
    # Parses the zoo.cfg and starts locally zk.

    # This is almost the same code as the
    # /bin/start-zookeeper-quorum.sh without the SSH part and only running for localhost.

    while read server ; do
        server=$(echo -e "${server}" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//') # trim

        # match server.id=address[:port[:port]]
        if [[ $server =~ ^server\.([0-9]+)[[:space:]]*\=[[:space:]]*([^: \#]+) ]]; then
            id=${BASH_REMATCH[1]}
            address=${BASH_REMATCH[2]}

            if [ "${address}" != "localhost" ]; then
                echo "[ERROR] Parse error. Only available for localhost. Expected address 'localhost' but got '${address}'"
                exit 1
            fi
            ${FLINK_DIR}/bin/zookeeper.sh start $id
        else
            echo "[WARN] Parse error. Skipping config entry '$server'."
        fi
    done < <(grep "^server\." "${FLINK_DIR}/conf/zoo.cfg")
}

function wait_rest_endpoint_up {
  local query_url=$1
  local endpoint_name=$2
  local successful_response_regex=$3
  # wait at most 30 seconds until the endpoint is up
  local TIMEOUT=30
  for i in $(seq 1 ${TIMEOUT}); do
    local log_file=$(mktemp)
    # without the || true this would exit our script if the endpoint is not yet up
    QUERY_RESULT=$(curl -v ${CURL_SSL_ARGS} "$query_url" 2> ${log_file} || true)

    # ensure the response adapts with the successful regex
    if [[ ${QUERY_RESULT} =~ ${successful_response_regex} ]]; then
      echo "${endpoint_name} REST endpoint is up."
      return
    else
      echo "***************** Curl detailed output *****************"
      echo "QUERY_RESULT is ${QUERY_RESULT}"
      cat ${log_file}
      echo "********************************************************"
    fi

    # Remove the temporary file
    rm ${log_file}

    echo "Waiting for ${endpoint_name} REST endpoint to come up..."
    sleep 1
  done
  echo "${endpoint_name} REST endpoint has not started within a timeout of ${TIMEOUT} sec"
  exit 1
}

function reset_rocksdb_log_level {
  # After upgrading RocksDB to 8.10 in FLINK-35573, the log dir of RocksDB cannot be set to/dev/null.
  # To avoid the problem of large logs, we can set the log level of rocksdb to HEADER_LOG in e2e test,
  # and then continue to keep it under tm's log dir
  set_config_key "state.backend.rocksdb.log.level" "HEADER_LEVEL"
}

function wait_dispatcher_running {
  local query_url="${REST_PROTOCOL}://${NODENAME}:8081/taskmanagers"
  wait_rest_endpoint_up "${query_url}" "Dispatcher" "\{\"taskmanagers\":\[.+\]\}"
}

function start_cluster {
  reset_rocksdb_log_level
  "$FLINK_DIR"/bin/start-cluster.sh
  wait_dispatcher_running
}

function wait_sql_gateway_running {
  local query_url="${REST_PROTOCOL}://${NODENAME}:8083/info"
  wait_rest_endpoint_up "${query_url}" "SqlGateway" "Apache Flink"
}

function start_sql_gateway() {
  "$FLINK_DIR"/bin/sql-gateway.sh start
  wait_sql_gateway_running
}

function stop_sql_gateway() {
  "$FLINK_DIR"/bin/sql-gateway.sh stop
}

function start_taskmanagers {
    local tmnum=$1
    local c

    echo "Start ${tmnum} more task managers"
    for (( c=0; c<tmnum; c++ ))
    do
        $FLINK_DIR/bin/taskmanager.sh start
    done
}

function start_and_wait_for_tm {
  tm_query_result=`query_running_tms`
  # we assume that the cluster is running
  if ! [[ ${tm_query_result} =~ \{\"taskmanagers\":\[.*\]\} ]]; then
    echo "Your cluster seems to be unresponsive at the moment: ${tm_query_result}" 1>&2
    exit 1
  fi

  running_tms=`query_number_of_running_tms`
  ${FLINK_DIR}/bin/taskmanager.sh start
  wait_for_number_of_running_tms $((running_tms+1))
}

function query_running_tms {
  local url="${REST_PROTOCOL}://${NODENAME}:8081/taskmanagers"
  curl ${CURL_SSL_ARGS} -s "${url}"
}

function query_number_of_running_tms {
  query_running_tms | grep -o "id" | wc -l
}

function wait_for_number_of_running_tms {
  local TM_NUM_TO_WAIT=${1}
  local TIMEOUT_COUNTER=10
  local TIMEOUT_INC=4
  local TIMEOUT=$(( $TIMEOUT_COUNTER * $TIMEOUT_INC ))
  local TM_NUM_TEXT="Number of running task managers"
  for i in $(seq 1 ${TIMEOUT_COUNTER}); do
    local TM_NUM=`query_number_of_running_tms`
    if [ $((TM_NUM - TM_NUM_TO_WAIT)) -eq 0 ]; then
      echo "${TM_NUM_TEXT} has reached ${TM_NUM_TO_WAIT}."
      return
    else
      echo "${TM_NUM_TEXT} ${TM_NUM} is not yet ${TM_NUM_TO_WAIT}."
    fi
    sleep ${TIMEOUT_INC}
  done
  echo "${TM_NUM_TEXT} has not reached ${TM_NUM_TO_WAIT} within a timeout of ${TIMEOUT} sec"
  exit 1
}

function check_logs_for_errors {
  internal_check_logs_for_errors
}

# check logs for errors, the arguments are the additional allowed errors
function internal_check_logs_for_errors {
  echo "Checking for errors..."

  local additional_allowed_errors=()
  local index=0
  for error in "$@"; do
    additional_allowed_errors[index]="$error"
    index=$index+1
  done

  local default_allowed_errors=("GroupCoordinatorNotAvailableException" \
  "RetriableCommitFailedException" \
  "NoAvailableBrokersException" \
  "Async Kafka commit failed" \
  "DisconnectException" \
  "Cannot connect to ResourceManager right now" \
  "AskTimeoutException" \
  "Error while loading kafka-version.properties" \
  "WARN  org.apache.pekko.remote.transport.netty.NettyTransport" \
  "WARN  org.jboss.netty.channel.DefaultChannelPipeline" \
  "jvm-exit-on-fatal-error" \
  'INFO.*AWSErrorCode' \
  "RejectedExecutionException" \
  "An exception was thrown by an exception handler" \
  "java.lang.NoClassDefFoundError: org/apache/hadoop/yarn/exceptions/YarnException" \
  "java.lang.NoClassDefFoundError: org/apache/hadoop/conf/Configuration" \
  "org.apache.commons.beanutils.FluentPropertyBeanIntrospector.*Error when creating PropertyDescriptor.*org.apache.commons.configuration2.AbstractConfiguration.setProperty(java.lang.String,java.lang.Object)! Ignoring this property." \
  "Error while loading kafka-version.properties :null" \
  "[Terror] modules" \
  "HeapDumpOnOutOfMemoryError" \
  "error_prone_annotations" \
  "Error sending fetch request" \
  "WARN  org.apache.pekko.remote.ReliableDeliverySupervisor" \
  "Options.*error_*" \
  "not packaged with this application")

  local all_allowed_errors=("${default_allowed_errors[@]}" "${additional_allowed_errors[@]}")

  # generate the grep command
  local grep_command=""
  for error in "${all_allowed_errors[@]}"; do
    if [[ $grep_command == "" ]]; then
      grep_command="grep -rv \"$error\" $FLINK_LOG_DIR"
    else
      grep_command="$grep_command | grep -v \"$error\""
    fi
  done
  grep_command="$grep_command | grep -ic \"error\" || true"

  error_count=$(eval "$grep_command")
  if [[ ${error_count} -gt 0 ]]; then
    echo "Found error in log files; printing first 500 lines; see full logs for details:"
    find $FLINK_LOG_DIR/ -type f -exec head -n 500 {} \;
    EXIT_CODE=1
  else
    echo "No errors in log files."
  fi
}

# check logs for exceptions, the arguments are the additional allowed exceptions
function internal_check_logs_for_exceptions {
  echo "Checking for exceptions..."

  local additional_allowed_exceptions=()
  local index=0
  for exception in "$@"; do
    additional_allowed_exceptions[index]="$exception"
    index=$index+1
  done

  local default_allowed_exceptions=("GroupCoordinatorNotAvailableException" \
  "due to CancelTaskException" \
  "RetriableCommitFailedException" \
  "NoAvailableBrokersException" \
  "Async Kafka commit failed" \
  "DisconnectException" \
  "Cannot connect to ResourceManager right now" \
  "AskTimeoutException" \
  "WARN  org.apache.pekko.remote.transport.netty.NettyTransport" \
  "WARN  org.jboss.netty.channel.DefaultChannelPipeline" \
  'INFO.*AWSErrorCode' \
  "RejectedExecutionException" \
  "CancellationException" \
  "An exception was thrown by an exception handler" \
  "Caused by: java.lang.ClassNotFoundException: org.apache.hadoop.yarn.exceptions.YarnException" \
  "Caused by: java.lang.ClassNotFoundException: org.apache.hadoop.conf.Configuration" \
  "java.lang.NoClassDefFoundError: org/apache/hadoop/yarn/exceptions/YarnException" \
  "java.lang.NoClassDefFoundError: org/apache/hadoop/conf/Configuration" \
  "java.lang.Exception: Execution was suspended" \
  "java.io.InvalidClassException: org.apache.flink.formats.avro.typeutils.AvroSerializer" \
  "Caused by: java.lang.Exception: JobManager is shutting down" \
  "java.lang.Exception: Artificial failure" \
  "org.apache.flink.runtime.checkpoint.CheckpointException" \
  "org.apache.flink.runtime.JobException: Recovery is suppressed" \
  "WARN  org.apache.pekko.remote.ReliableDeliverySupervisor" \
  "RecipientUnreachableException" \
  "completeExceptionally" \
  "SerializedCheckpointException.unwrap")

  local all_allowed_exceptions=("${default_allowed_exceptions[@]}" "${additional_allowed_exceptions[@]}")

  # generate the grep command
  local grep_command=""
  for exception in "${all_allowed_exceptions[@]}"; do
    if [[ $grep_command == "" ]]; then
      grep_command="grep -rv \"$exception\" $FLINK_LOG_DIR"
    else
      grep_command="$grep_command | grep -v \"$exception\""
    fi
  done
  grep_command="$grep_command | grep -ic \"exception\" || true"

  exception_count=$(eval "$grep_command")
  if [[ ${exception_count} -gt 0 ]]; then
    echo "Found exception in log files; printing first 500 lines; see full logs for details:"
    find $FLINK_LOG_DIR/ -type f -exec head -n 500 {} \;
    EXIT_CODE=1
  else
    echo "No exceptions in log files."
  fi
}

function check_logs_for_exceptions() {
  internal_check_logs_for_exceptions
}

function check_logs_for_non_empty_out_files {
  echo "Checking for non-empty .out files..."
  # exclude reflective access warnings as these are expected (and currently unavoidable) on Java 9
  # exclude message about JAVA_TOOL_OPTIONS being set (https://bugs.openjdk.java.net/browse/JDK-8039152)
  if grep -ri -v \
    -e "WARNING: An illegal reflective access" \
    -e "WARNING: Illegal reflective access"\
    -e "WARNING: Please consider reporting"\
    -e "WARNING: Use --illegal-access"\
    -e "WARNING: All illegal access"\
    -e "Picked up JAVA_TOOL_OPTIONS"\
    $FLINK_LOG_DIR/*.out\
   | grep "." \
   > /dev/null; then
    echo "Found non-empty .out files; printing first 500 lines; see full logs for details:"
    find $FLINK_LOG_DIR/ -type f -name '*.out' -exec head -n 500 {} \;
    EXIT_CODE=1
  else
    echo "No non-empty .out files."
  fi
}

function shutdown_all {
  stop_cluster
  stop_sql_gateway
  # stop TMs which started by command: bin/taskmanager.sh start
  "$FLINK_DIR"/bin/taskmanager.sh stop-all
  tm_kill_all
  jm_kill_all
  gw_kill_all
}

function stop_cluster {
  "$FLINK_DIR"/bin/stop-cluster.sh

  # stop zookeeper only if there are processes running
  zookeeper_process_count=$(jps | grep -c 'FlinkZooKeeperQuorumPeer' || true)
  if [[ ${zookeeper_process_count} -gt 0 ]]; then
    echo "Stopping zookeeper..."
    "$FLINK_DIR"/bin/zookeeper.sh stop
  fi
}

function wait_for_job_state_transition {
  local job=$1
  local initial_state=$2
  local next_state=$3
    
  echo "Waiting for job ($job) to switch from state ${initial_state} to state ${next_state} ..."

  while : ; do
    N=$(grep -o "($job) switched from state ${initial_state} to ${next_state}" $FLINK_LOG_DIR/*standalonesession*.log* | tail -1)

    if [[ -z $N ]]; then
      sleep 1
    else
      break
    fi
  done
}

function wait_job_running {
  local TIMEOUT=10
  for i in $(seq 1 ${TIMEOUT}); do
    JOB_LIST_RESULT=$("$FLINK_DIR"/bin/flink list -r | grep "$1")

    if [[ "$JOB_LIST_RESULT" == "" ]]; then
      echo "Job ($1) is not yet running."
    else
      echo "Job ($1) is running."
      return
    fi
    sleep 1
  done
  echo "Job ($1) has not started within a timeout of ${TIMEOUT} sec"
  exit 1
}

function wait_job_terminal_state {
  local job=$1
  local expected_terminal_state=$2
  local log_file_name=${3:-standalonesession}

  echo "Waiting for job ($job) to reach terminal state $expected_terminal_state ..."

  while : ; do
    local N=$(grep -o "Job $job reached terminal state .*" $FLINK_LOG_DIR/*$log_file_name*.log* | tail -1 || true)
    if [[ -z $N ]]; then
      sleep 1
    else
      local actual_terminal_state=$(echo $N | sed -n 's/.*state \([A-Z]*\).*/\1/p')
      if [[ -z $expected_terminal_state ]] || [[ "$expected_terminal_state" == "$actual_terminal_state" ]]; then
        echo "Job ($job) reached terminal state $actual_terminal_state"
        break
      else
        echo "Job ($job) is in state $actual_terminal_state but expected $expected_terminal_state"
        exit 1
      fi
    fi
  done
}

function stop_with_savepoint {
  "$FLINK_DIR"/bin/flink stop -p $2 $1
}

function take_savepoint {
  "$FLINK_DIR"/bin/flink savepoint $1 $2
}

function cancel_job {
  "$FLINK_DIR"/bin/flink cancel $1
}

function check_result_hash {
  local error_code=0
  check_result_hash_no_exit "$@" || error_code=$?

  if [ "$error_code" != "0" ]
  then
    exit $error_code
  fi
}

function check_result_hash_no_exit {
  local name=$1
  local outfile_prefix=$2
  local expected=$3

  local actual
  if [ "`command -v md5`" != "" ]; then
    actual=$(LC_ALL=C sort $outfile_prefix* | md5 -q)
  elif [ "`command -v md5sum`" != "" ]; then
    actual=$(LC_ALL=C sort $outfile_prefix* | md5sum | awk '{print $1}')
  else
    echo "Neither 'md5' nor 'md5sum' binary available."
    return 2
  fi
  if [[ "$actual" != "$expected" ]]
  then
    echo "FAIL $name: Output hash mismatch.  Got $actual, expected $expected."
    echo "head hexdump of actual:"
    head $outfile_prefix* | hexdump -c
    return 1
  else
    echo "pass $name"
    # Output files are left behind in /tmp
  fi
  return 0
}

# This function starts the given number of task managers and monitors their processes.
# If a task manager process goes away a replacement is started.
function tm_watchdog {
  local expectedTm=$1
  while true;
  do
    runningTm=`jps | grep -Eo 'TaskManagerRunner|TaskManager' | wc -l`;
    count=$((expectedTm-runningTm))
    if (( count != 0 )); then
        start_taskmanagers ${count} > /dev/null
    fi
    sleep 5;
  done
}

# Kills all job manager.
function jm_kill_all {
  kill_all 'ClusterEntrypoint'
}

# Kills all task manager.
function tm_kill_all {
  kill_all 'TaskManagerRunner|TaskManager'
}

function gw_kill_all {
  kill_all 'SqlGateway'
}

# Kills all processes that match the given name.
function kill_all {
  local pid=`jps | grep -E "${1}" | cut -d " " -f 1 || true`
  kill ${pid} 2> /dev/null || true
  wait ${pid} 2> /dev/null || true
}

function kill_random_taskmanager {
  local pid=`jps | grep -E "TaskManagerRunner|TaskManager" | sort -R | head -n 1 | cut -d " " -f 1 || true`
  kill -9 "$pid"
  echo "TaskManager $pid killed."
}

function setup_flink_slf4j_metric_reporter() {
  METRIC_NAME_PATTERN="${1:-"*"}"
  set_config_key "metrics.reporter.slf4j.factory.class" "org.apache.flink.metrics.slf4j.Slf4jReporterFactory"
  set_config_key "metrics.reporter.slf4j.interval" "1 SECONDS"
  set_config_key "metrics.reporter.slf4j.filter.includes" "'*:${METRIC_NAME_PATTERN}'"
}

function get_job_exceptions {
  local job_id=$1
  local json=$(curl ${CURL_SSL_ARGS} -s ${REST_PROTOCOL}://${NODENAME}:8081/jobs/${job_id}/exceptions)

  echo ${json}
}

function get_job_metric {
  local job_id=$1
  local metric_name=$2

  local json=$(curl ${CURL_SSL_ARGS} -s ${REST_PROTOCOL}://${NODENAME}:8081/jobs/${job_id}/metrics?get=${metric_name})
  local metric_value=$(echo ${json} | sed -n 's/.*"value":"\(.*\)".*/\1/p')

  echo ${metric_value}
}

function get_metric_processed_records {
  OPERATOR=$1
  JOB_NAME="${2:-General purpose test job}"
  N=$(grep ".${JOB_NAME}.$OPERATOR.numRecordsIn:" $FLINK_LOG_DIR/*taskexecutor*.log* | sed 's/.* //g' | tail -1)
  if [ -z $N ]; then
    N=0
  fi
  echo $N
}

function get_num_metric_samples {
  OPERATOR=$1
  JOB_NAME="${2:-General purpose test job}"
  N=$(grep ".${JOB_NAME}.$OPERATOR.numRecordsIn:" $FLINK_LOG_DIR/*taskexecutor*.log* | wc -l)
  if [ -z $N ]; then
    N=0
  fi
  echo $N
}

function wait_oper_metric_num_in_records {
    OPERATOR=$1
    MAX_NUM_RECORDS="${2:-200}"
    JOB_NAME="${3:-General purpose test job}"
    NUM_METRICS=$(get_num_metric_samples ${OPERATOR} "${JOB_NAME}")
    OLD_NUM_METRICS=${4:-${NUM_METRICS}}
    local timeout="${5:-600}"
    local i=0
    # monitor the numRecordsIn metric of the state machine operator in the second execution
    # we let the test finish once the second restore execution has processed 200 records
    while : ; do
      NUM_METRICS=$(get_num_metric_samples ${OPERATOR} "${JOB_NAME}")
      NUM_RECORDS=$(get_metric_processed_records ${OPERATOR} "${JOB_NAME}")

      # only account for metrics that appeared in the second execution
      if (( $OLD_NUM_METRICS >= $NUM_METRICS )) ; then
        NUM_RECORDS=0
      fi

      if (( $NUM_RECORDS < $MAX_NUM_RECORDS )); then
        echo "Waiting for job to process up to ${MAX_NUM_RECORDS} records, current progress: ${NUM_RECORDS} records ..."
        sleep 1
        ((i++))
        if ((i > timeout)); then
            echo "A timeout occurred waiting for job to process up to ${MAX_NUM_RECORDS} records"
            exit 1
        fi
      else
        break
      fi
    done
}

function wait_num_of_occurence_in_logs {
    local text=$1
    local number=$2
    local logs=${3:-standalonesession}
    local timeout="${4:-600}"
    local i=0

    echo "Waiting for text ${text} to appear ${number} of times in logs..."

    while : ; do
      N=$(grep -E -o "${text}" $FLINK_LOG_DIR/*${logs}*.log* | wc -l)

      if [ -z $N ]; then
        N=0
      fi

      if (( N < number )); then
        sleep 1
        ((i++))
        if ((i > timeout)); then
            echo "A timeout occurred waiting for ${text} to appear ${number} of times in logs."
            exit 1
        fi
      else
        break
      fi
    done

}

function wait_num_checkpoints {
    JOB=$1
    NUM_CHECKPOINTS=$2
    local timeout="${3:-600}"
    local i=0

    echo "Waiting for job ($JOB) to have at least $NUM_CHECKPOINTS completed checkpoints ..."

    while : ; do
      N=$(grep -o "Completed checkpoint [1-9]* for job $JOB" $FLINK_LOG_DIR/*standalonesession*.log* | awk '{print $3}' | tail -1)

      if [ -z $N ]; then
        N=0
      fi

      if (( N < NUM_CHECKPOINTS )); then
        sleep 1
        ((i++))
        if ((i > timeout)); then
            echo "A timeout occurred waiting for job ($JOB) to have at least $NUM_CHECKPOINTS completed checkpoints ."
            exit 1
        fi
      else
        break
      fi
    done
}

# Starts the timer. Note that nested timers are not supported.
function start_timer {
    SECONDS=0
}

# prints the number of minutes and seconds that have elapsed since the last call to start_timer
function end_timer {
    duration=$SECONDS
    echo "$(($duration / 60)) minutes and $(($duration % 60)) seconds"
}

function clean_stdout_files {
    rm $FLINK_LOG_DIR/*.out
    echo "Deleted all stdout files under $FLINK_LOG_DIR/"
}

# Expect a string to appear in the log files of the task manager before a given timeout
# $1: expected string
# $2: timeout in seconds
function expect_in_taskmanager_logs {
    local expected="$1"
    local timeout=$2
    local i=0
    local logfile="$FLINK_LOG_DIR/flink*taskexecutor*log*"


    while ! grep "${expected}" ${logfile} > /dev/null; do
        sleep 1s
        ((i++))
        if ((i > timeout)); then
            echo "A timeout occurred waiting for '${expected}' to appear in the taskmanager logs"
            exit 1
        fi
    done
}

function wait_for_restart_to_complete {
    local base_num_restarts=$1
    local jobid=$2

    local current_num_restarts=${base_num_restarts}
    local expected_num_restarts=$((current_num_restarts + 1))

    echo "Waiting for restart to happen"
    while [[ ${current_num_restarts} -lt ${expected_num_restarts} ]]; do
        echo "Still waiting for restarts. Expected: $expected_num_restarts Current: $current_num_restarts"
        sleep 5
        current_num_restarts=$(get_job_metric ${jobid} "numRestarts")
        if [[ -z ${current_num_restarts} ]]; then
            current_num_restarts=${base_num_restarts}
        fi
    done
}

function find_latest_completed_checkpoint {
    local checkpoint_root_directory=$1
    # a completed checkpoint must contain the _metadata file
    local checkpoint_meta_file=$(ls -d ${checkpoint_root_directory}/chk-[1-9]*/_metadata | sort -Vr | head -n1)
    echo "$(dirname "${checkpoint_meta_file}")"
}

function retry_times() {
    retry_times_with_backoff_and_cleanup $1 $2 "$3" "true"
}

function retry_times_with_backoff_and_cleanup() {
    local retriesNumber=$1
    local backoff=$2
    local command="$3"
    local cleanup_command="$4"

    for i in $(seq 1 ${retriesNumber})
    do
        if ${command}; then
            return 0
        else
            ${cleanup_command}
        fi

        echo "Command: ${command} failed. Retrying..."
        sleep ${backoff}
    done

    echo "Command: ${command} failed ${retriesNumber} times."
    ${cleanup_command}
    return 1
}

function retry_times_with_exponential_backoff {
  local retries=$1
  shift

  local count=0
  echo "Executing command:" "$@"
  until "$@"; do
    exit=$?
    wait=$((2 ** $count))
    count=$(($count + 1))
    if [ $count -lt $retries ]; then
      echo "Retry $count/$retries exited $exit, retrying in $wait seconds..."
      sleep $wait
    else
      echo "Retry $count/$retries exited $exit, no more retries left."
      return $exit
    fi
  done
  return 0
}

JOB_ID_REGEX_EXTRACTOR=".*JobID ([0-9,a-f]*)"

function extract_job_id_from_job_submission_return() {
    if [[ $1 =~ $JOB_ID_REGEX_EXTRACTOR ]];
        then
            JOB_ID="${BASH_REMATCH[1]}";
        else
            JOB_ID=""
        fi
    echo "$JOB_ID"
}

kill_test_watchdog() {
    local watchdog_pid=$(cat $TEST_DATA_DIR/job_watchdog.pid)
    if kill -0 $watchdog_pid > /dev/null 2>&1; then
        echo "Stopping job timeout watchdog (with pid=$watchdog_pid)"
        kill $watchdog_pid
    else
        echo "No watchdog process with pid=$watchdog_pid present, anymore. No action required to clean the watchdog process up."
    fi
}

#
# NOTE: This function requires at least Bash version >= 4 due to the usage of $BASHPID. Mac OS in 2020 still ships 3.x
#
internal_run_with_timeout() {
  local timeout_in_seconds="$1"
  local on_failure="$2"
  local command_label="$3"
  local command="${@:4}"

  on_exit kill_test_watchdog

  (
      command_pid=$BASHPID
      (sleep "${timeout_in_seconds}" # set a timeout for this command
      echo "${command_label:-"The command '${command}'"} (pid: $command_pid) did not finish after $timeout_in_seconds seconds."
      eval "${on_failure}"
      kill "$command_pid") & watchdog_pid=$!
      echo $watchdog_pid > $TEST_DATA_DIR/job_watchdog.pid
      # invoke
      $command
  )
}

run_on_test_failure() {
  echo "Printing Flink logs and killing it:"
  cat $FLINK_LOG_DIR/*
}

run_test_with_timeout() {
  internal_run_with_timeout $1 run_on_test_failure "Test" ${@:2}
}

run_with_timeout() {
  internal_run_with_timeout $1 "" "" ${@:2}
}
