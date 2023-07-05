#!/bin/bash

DAEMON=false
DISPLAY_MODE="GUI"
PRIVACY_CONTEXT=2
MAX_TABS=8

while [[ $# -gt 0 ]]; do
  case $1 in
    -D|--daemon)
      DAEMON=true
      shift # past argument
      ;;
    -SV|--supervised)
      DISPLAY_MODE="SUPERVISED"
      shift # past argument
      ;;
    -HL|--headless)
      DISPLAY_MODE="HEADLESS"
      shift # past argument
      ;;
    -pc|--privacy)
      PRIVACY_CONTEXT="$2"
      shift # past argument
      shift # past value
      ;;
    -mt|--max-tabs)
      MAX_TABS="$2"
      shift # past argument
      shift # past value
      ;;
    -*|--*)
      echo "Unknown option $1"
      exit 1
      ;;
    *)
      shift # past argument
      ;;
  esac
done

function find_best_java() {
  # not working on centos
#  echo "Trying to find Java 11 which is the best supported version ..."
#  UPDATE_ALTER=$(command -v update-alternatives)
#  if [[ -e $UPDATE_ALTER ]]; then
#    echo "Installed Java: "
#    update-alternatives --list java
#    JAVA=$(update-alternatives --list java | grep java-11)
#  fi

  if [[ ! -e $JAVA ]]; then
    JAVA=$(command -v java)
  fi

  if [[ -e $JAVA ]]; then
    JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
    export JAVA_HOME=$JAVA_HOME
    JAVA="$JAVA_HOME/bin/java"
  fi

  if [[ ! -e $JAVA ]]; then
    echo "Java not found"
    exit 0
  fi
}

function choose_best_log_dir() {
    if [ -z "$APP_LOG_HOME" ]; then
      APP_LOG_HOME="logs"
    fi
    mkdir -p "$APP_LOG_HOME"

    LOG_DIR="$APP_LOG_HOME/amazon"
    if [[ -e $LOG_DIR ]]; then
      COUNT=$(ls -l "$APP_LOG_HOME" | grep -c "$TLD")
      mv "$LOG_DIR" "$LOG_DIR.$COUNT"
    fi
    mkdir -p "$LOG_DIR"

    export LOG_DIR=$LOG_DIR
}

choose_best_log_dir
echo "Log dir: $LOG_DIR"

echo
find_best_java
echo "Java to use: "
echo "$JAVA"

JAR=$(find . -name "exotic-amazon*.jar")

APP_OPTS=(
-D"logging.dir=$LOG_DIR"
-D"privacy.context.number=$PRIVACY_CONTEXT"
-D"browser.max.active.tabs=$MAX_TABS"
-D"browser.display.mode=$DISPLAY_MODE"
)

if [[ -e "$LOGBACK_CONFIG_FILE_LOCATION" ]]; then
  APP_OPTS=("${APP_OPTS[@]}" -D"logging.config=$LOGBACK_CONFIG_FILE_LOCATION")
fi

if [[ -e "$APP_TMP_DIR" ]]; then
  APP_OPTS=("${APP_OPTS[@]}" -D"app.tmp.dir=$APP_TMP_DIR")
fi

EXEC_CALL=(
"$JAVA"
-Dproc_EXOTIC_AMZ
"-Xms2G" "-Xmx10g" "-XX:+HeapDumpOnOutOfMemoryError"
"-XX:-OmitStackTraceInFastThrow"
"-XX:ErrorFile=$HOME/java_error_in_exotic_amazon_%p.log"
"-XX:HeapDumpPath=$HOME/java_error_in_exotic_amazon.hprof"
-D"java.awt.headless=true"
"${APP_OPTS[@]}"
-D"loader.main=ai.platon.exotic.amazon.starter.CrawlStarterKt"
-cp "$JAR" org.springframework.boot.loader.PropertiesLauncher
-pc "$PRIVACY_CONTEXT"
-mt "$MAX_TABS"
)

LOGOUT=/dev/null
PID="$LOG_DIR/exotic-amazon.pid"
if $DAEMON; then
  exec "${EXEC_CALL[@]}" >> "$LOGOUT" 2>&1 &
  echo $! > "$PID"
else
  echo "${EXEC_CALL[@]}"
  exec "${EXEC_CALL[@]}"
fi
