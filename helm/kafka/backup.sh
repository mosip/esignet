#!/bin/sh
# backup kafka via Velero
## Usage: ./backup.sh kubeconfig
chk_status(){
    RESOURCE=$1
    count=$( velero $1 get  | grep -Ec 'New|InProgress' )
    if [ $count -gt 0 ]; then
        echo "$(tput setaf 1) Previous velero $RESOURCE job is still in either 'New' state or 'InProgress' state; $(tput sgr0)";
        printf "%s Please wait till velero job is either completed or failed.\n You can troubleshoot the issue; EXITING %s" $(tput setaf 1) $(tput sgr0)
        exit 1;
    fi
}
read_user_input(){
    if [ $# -lt 2 ]; then
        echo "$(tput setaf 1) Variable & Message arguments not passed to read_user_input function; EXITING $(tput sgr0)";
        exit 1;
    fi
    if [ $# -gt 2 ]; then
        DEFAULT=$3;                            ## default values for $VAR variable
    fi
    VAR=$1;                                    ## variable name
    MSG=$2;                                    ## message to be printed for the given variable
    read -p "  Provide $MSG : " $VAR;
    TEMP=$( eval "echo \${$VAR}" );            ## save $VAR values to a temporary variable
    eval ${VAR}=${TEMP:-$DEFAULT};             ## set $VAR value to $DEFAULT if $TEMP is empty, else set $VAR value to $TEMP
    if [ -z $( eval "echo \${$VAR}" ) ]; then
        echo "$(tput setaf 1) $MSG not provided; EXITING $(tput sgr0)";
        exit 1;
    fi
    DEFAULT='';                               ## reset `DEFAULT` variable to empty string
}

print_heading(){
    HEADING=$1
    tput setaf 3
    printf '\n_%*.0s' $(( $(tput cols)*10/100  -1 )) "" | tr " " "=" | tr "_" " "
    echo -n " $HEADING "
    printf '%*.0s \n\n' $(( $(tput cols) - ${#HEADING} - $(tput cols)*10/100 -5 ))  "" | tr " " "="
    tput sgr0
}

chk_exit_status(){
    /bin/sh -c "$1"
    EXIT_STATUS=$?
    if [ $EXIT_STATUS -gt 0 ]; then
        END_MSG='EXITING';
        if [ "$2" = "skip" ]; then
             shift
             END_MSG='SKIPPING';
             RETURN='TRUE';
        fi
        shift
        for msg in "${@}";do
            echo "$(tput setaf 1)  $msg; $END_MSG $(tput sgr0)";
        done
        if [ "$RETURN" = "TRUE" ]; then
             return "1";
        fi
        exit 1;
    fi
    return "0";
}

## The script starts from here
### Cluster
HEADING="Check Cluster Config File"
print_heading "$HEADING";  ## calling print_heading function
chk_exit_status "[ $# -eq 1 ]" "Kubernetes Cluster config file not provided"
chk_exit_status "[ -f $1 ]" "Kubernetes Cluster config file not found"
echo "$(tput setaf 2)  Kubernetes Cluster file found $(tput sgr0)"

### check whether MINIO client ( mc ), kubectl, & velero is installed
HEADING="Check packages installed"
print_heading "$HEADING";  ## calling print_heading function
chk_exit_status "which mc > /dev/null" "MINIO Client ( mc ) not installed";
chk_exit_status "which velero > /dev/null" "Velero is not installed";
chk_exit_status "which kubectl > /dev/null" "kubectl is not installed";
echo "$(tput setaf 2)  kubectl, minio client (mc), & velero packages are already installed !!! $(tput sgr0)"

K8S_CONFIG_FILE=$1
export KUBECONFIG=$K8S_CONFIG_FILE

### S3 / MINIO
HEADING="S3 Setup"
print_heading "$HEADING";  ## calling print_heading function
read_user_input s3_server "S3 server";           ## calling read_user_input function
read_user_input s3_access_key "S3 access key";
read_user_input s3_secret_key "S3 secret key";
read_user_input s3_region "S3 region ( Default region = minio )" "minio";

# set S3 alias
s3_alias=s3_server
echo -n "  "
CMD="mc alias set $s3_alias $s3_server $s3_access_key $s3_secret_key --api S3v2"
chk_exit_status "$CMD" "Not able to reach S3 SERVER"

# create velero bucket, Ignore if already exist
bucket=$s3_alias/velero
#chk_exit_status "mc ls $bucket" "skip" "Not able to access bucket $bucket";
echo -n "  "
CMD="mc mb --ignore-existing $bucket"
chk_exit_status "$CMD" "Not able to create bucket on S3 SERVER"


# check velero is already deployed on the cluster
HEADING="Velero install"
print_heading "$HEADING";  ## calling print_heading function

printf "[default]\naws_access_key_id = %s\naws_secret_access_key = %s\n"    "$s3_access_key" "$s3_secret_key" > credentials-velero

CMD="kubectl --kubeconfig=$K8S_CONFIG_FILE get deployment/velero -n velero"
chk_exit_status "$CMD" "skip" "Velero is not deployed on this cluster"
STATUS=$?
if ! [ "$STATUS" = "0" ];then
    velero install \
        --provider aws \
        --plugins velero/velero-plugin-for-aws:v1.2.1 \
        --bucket velero \
        --secret-file ./credentials-velero \
        --backup-location-config region=$s3_region,s3ForcePathStyle="true",s3Url=$s3_server \
        --use-volume-snapshots=false \
        --default-volumes-to-restic \
        --kubeconfig $K8S_CONFIG_FILE \
        --use-restic | sed 's/^/   /g'
fi
echo "$(tput setaf 2)  Velero deployed $(tput sgr0)"

NO_OF_RETRIES=5
HEADING="Check BackupStorageLocations validity"
print_heading "$HEADING";  ## calling print_heading function
for i in $(seq 1 $NO_OF_RETRIES); do
    echo "$(tput setaf 6)  [Trying : $i ] $(tput sgr 0)";
    printf "\tPlease wait for 5 seconds;\n";
    sleep 5;
    BackupStorageLocationValid=$( kubectl --kubeconfig=$K8S_CONFIG_FILE logs deployment/velero -n velero |grep -c "BackupStorageLocations is valid" 2>&1 & );
    printf "\tBackupStorageLocationValid = %s" $BackupStorageLocationValid;
    if [ "$BackupStorageLocationValid" -eq 0 ]; then
        printf "%s\n\tBackupStorageLocation is invalid; Trying to connect S3 again %s\n" $(tput setaf 1) $(tput sgr0);
        if [ $i -eq $NO_OF_RETRIES ]; then
            printf "%s\n\tUnable to connect to S3 bucket; EXITING %s" $(tput setaf 1) $(tput sgr0);
            printf "%s\n\tPlease check whether S3 bucket is accessible from kubernetes cluster \n\tAnd also check S3 login credentials %s\n" $(tput setaf 4) $(tput sgr0);
            exit 1;
        fi
        continue;
    fi
    printf "%s\n\tBackupStorageLocation is valid !!!%s\n" $(tput setaf 2) $(tput sgr0);
    break;
done

## create backup operation
HEADING="Create Backup"
print_heading "$HEADING";  ## calling print_heading function
chk_status backup          ## calling chk_status function to check any backup or restore jon is in New/InProgress state

read_user_input SERVICE "k8s service to be taken for backup ";
read_user_input NAMESPACE "k8s service Namespace";
BACKUP_NAME="$SERVICE-$( date +'%d-%m-%Y-%H-%M' )"

#### Check whether all $SERVICE.$NAMESPACE pods are up
echo -e "$(tput setaf 2)  [ Check whether all $SERVICE.$NAMESPACE pods are up ] $(tput sgr0)\n"
for name in zookeeper kafka; do
    CMD="kubectl --kubeconfig=$K8S_CONFIG_FILE -n $NAMESPACE wait --for=condition=ready pod --timeout=30s -l app.kubernetes.io/name=$name";
    chk_exit_status "$CMD" "The $name pods failed to be ready by 5 minutes.";
done

echo "$(tput setaf 2)  [ Creating Backup ] $(tput sgr0)"
printf "\t"
velero backup create "$BACKUP_NAME" \
    --default-volumes-to-restic \
    --selector app.kubernetes.io/instance="$SERVICE" \
    --include-namespaces "$NAMESPACE" \
    --kubeconfig "$K8S_CONFIG_FILE" \
    --wait

HEADING="List Backup"
print_heading "$HEADING";  ## calling print_heading function
printf "%s\n  [ List Backups ] %s" $(tput setaf 2) $(tput sgr0)
velero --kubeconfig $K8S_CONFIG_FILE backup get | sed 's/^/\t/g'
