#!/bin/bash

#installs the pre-requisites.
set -e

echo "Replacing public url placeholder with public url"

rpCmd="s/_PUBLIC_URL_/${OIDC_UI_PUBLIC_URL}/g"
grep -rl '_PUBLIC_URL_' $base_path | xargs sed -i $rpCmd

echo "Replacing completed."

exec "$@"
