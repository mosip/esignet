#!/bin/sh
# Copy configmaps from other namespaces
# DST_NS: Destination namespace 

COPY_UTIL=./copy_cm_func.sh
DST_NS=esignet

$COPY_UTIL configmap global default $DST_NS 
$COPY_UTIL configmap artifactory-share artifactory $DST_NS 
$COPY_UTIL configmap config-server-share config-server $DST_NS
$COPY_UTIL configmap softhsm-esignet-share softhsm $DST_NS
