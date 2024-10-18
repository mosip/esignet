#!/bin/sh
# pod name
kubectl -n captcha logs -f $1 | grep -v "/v1/captcha/actuator/health" | grep -v "/v1/captcha/actuator/prometheus"