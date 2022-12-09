#!/bin/sh
# Copy configmaps from other namespaces
# DST_NS: Destination namespace

COPY_UTIL=../../copy_cm_func.sh
DST_NS=idp


COPY_UTIL configmap global default $DST_NS
