#!/bin/sh
set -eu

CHROMEDRIVER_BIN="${CHROMEDRIVER_BIN:-/usr/bin/chromedriver}"
CHROME_BIN="${CHROME_BIN:-/usr/bin/chromium}"

echo "=== versions ==="
java -version 2>&1 | head -1
"$CHROME_BIN" --version
"$CHROMEDRIVER_BIN" --version

start_driver() {
  "$CHROMEDRIVER_BIN" --port=9515 --whitelisted-ips= >/tmp/chromedriver.log 2>&1 &
  DRIVER_PID=$!
  i=0
  while [ "$i" -lt 30 ]; do
    if curl -sf http://127.0.0.1:9515/status >/dev/null 2>&1; then
      return 0
    fi
    i=$((i + 1))
    sleep 0.2
  done
  echo "ChromeDriver did not become ready"
  cat /tmp/chromedriver.log
  return 1
}

stop_driver() {
  if [ -n "${DRIVER_PID:-}" ]; then
    kill "$DRIVER_PID" >/dev/null 2>&1 || true
    wait "$DRIVER_PID" >/dev/null 2>&1 || true
    DRIVER_PID=
  fi
}

new_session() {
  name="$1"
  args_json="$2"
  timeout_sec="$3"
  body=$(printf '{"capabilities":{"alwaysMatch":{"browserName":"chrome","goog:chromeOptions":{"binary":"%s","args":%s}}}}' "$CHROME_BIN" "$args_json")
  echo "=== $name ==="
  echo "$body"
  stop_driver
  start_driver
  set +e
  resp=$(curl -sS -m "$timeout_sec" -H 'Content-Type: application/json' -d "$body" http://127.0.0.1:9515/session)
  code=$?
  set -e
  echo "$resp"
  if [ "$code" -ne 0 ]; then
    echo "RESULT $name: FAIL curl_exit=$code (timeout or connect error)"
    cat /tmp/chromedriver.log || true
    stop_driver
    return 1
  fi
  case "$resp" in
    *'"sessionId"'*|*'sessionId'*)
      sid=$(echo "$resp" | sed -n 's/.*"sessionId":"\([^"]*\)".*/\1/p' | head -1)
      echo "RESULT $name: PASS session=$sid"
      if [ -n "$sid" ]; then
        curl -sS -m 5 -X DELETE "http://127.0.0.1:9515/session/$sid" >/dev/null || true
      fi
      stop_driver
      return 0
      ;;
    *)
      echo "RESULT $name: FAIL"
      cat /tmp/chromedriver.log || true
      stop_driver
      return 1
      ;;
  esac
}

OLD_ARGS='["--use-fake-ui-for-media-stream","--use-fake-device-for-media-stream","--enable-media-stream","--headless=new","--disable-gpu","--window-size=1920x1080","--no-sandbox","--disable-dev-shm-usage","--remote-debugging-port=0"]'
FIXED_ARGS='["--use-fake-ui-for-media-stream","--use-fake-device-for-media-stream","--enable-media-stream","--headless=new","--disable-gpu","--window-size=1920,1080","--no-sandbox","--disable-dev-shm-usage","--disable-setuid-sandbox","--remote-allow-origins=*"]'

fail=0
new_session "testriq-old-flags" "$OLD_ARGS" 20 || fail=1
new_session "fixed-flags" "$FIXED_ARGS" 20 || fail=1
exit "$fail"
