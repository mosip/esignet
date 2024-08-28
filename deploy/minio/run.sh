# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes

kubectl minio tenant create minio-tenant-1 \
    --servers 1                              \
    --volumes 4                              \
    --capacity 8Gi                           \
    --namespace minio-tenant-1               \
    --storage-class gp2
