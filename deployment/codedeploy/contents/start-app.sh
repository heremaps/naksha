#!/bin/bash

# Set instance specific parameters
. ./set-instance-params.sh

# Set application specific parameters
. ./set-app-params.sh

# Set Auth keys for application
. ./set-auth-keys.sh

if [ ! -d log ]; then
  mkdir log
fi

export XYZ_CONFIG_PATH
XYZ_CONFIG_PATH=$(pwd)/.xyz-hub/
echo "XYZ_CONFIG_PATH : $XYZ_CONFIG_PATH"
echo "EC2_INSTANCE_NAME : $EC2_INSTANCE_NAME"
echo "EC2_ENV : $EC2_ENV"
echo "-Xms : $JVM_XMS"
echo "-Xmx : $JVM_XMX"
OTEL_RESOURCE_ATTRIBUTES=service.name=${EC2_INSTANCE_NAME},service.namespace=Naksha-${EC2_ENV_UPPER} \
  java -javaagent:/home/admin/aws-opentelemetry/aws-opentelemetry-agent.jar \
  -server -Xms${JVM_XMS} -Xmx${JVM_XMX} -Xss1024k \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:+UseZGC \
  -XX:+UseNUMA \
  -XX:+UseTLAB -XX:+AlwaysPreTouch \
  -XX:+ExplicitGCInvokesConcurrent \
  --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  -Xlog:gc*:file=log/xyz_gc.log \
  -XX:ErrorFile=log/xyz_hs_err_pid%p.log \
  -XX:LogFile=log/xyz_hotspot.log \
  -cp xyz-hub-service.jar com.here.xyz.hub.Service 1> log/xyz_stdout.txt 2> log/xyz_stderr.txt
