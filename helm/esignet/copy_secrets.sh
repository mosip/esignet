#!/bin/bash
# Copy secrets from other namespaces
# DST_NS: Destination namespace
  COPY_UTIL=../esignet/copy_cm_func.sh
  #DST_NS=esignet
  $COPY_UTIL secret esignet-captcha esignet config-server
