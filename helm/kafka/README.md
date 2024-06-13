# Kafka

## Install
* Review `values.yaml`  
* Replication factor must not exceed number of nodes available.
* Install
```
$ ./install.sh
```

## Steps to Backup & Restore Bitnami Kafka manually

#### Prerequisites
* Install `zip` & `unzip` packages on both backup/restore control plane nodes.

#### Backup
* Set WebSub and Kafka replicas to zero for V2 based existing env using the below command.
  ```
  kubectl --kubeconfig /home/mosipuser/.kube/dmzcluster.config -n default scale --replicas=0 deploy consolidator-websub-service websub-service
  kubectl --kubeconfig /home/mosipuser/.kube/dmzcluster.config -n default scale --replicas=0 statefulset kafka kafka-zookeeper
  ```
* Once Websub & Kafka pods are completely terminated, go to `/srv/nfs/mosip/`.
  ```
  cd /srv/nfs/mosip
  ```
* Run the below command to zip DMZ Kafka directories.
  ```
  sudo zip -r dmzkafka-backup.zip $( ls -d $( kc2 get pv | awk '/kafka/{print "*" $1}'))
  ```
* Move the backup file to the machine where the restore cluster is present.
* Set the Kafka replicas to their original value via the below command.
  ```
  kubectl --kubeconfig /home/mosipuser/.kube/dmzcluster.config -n default scale --replicas=<no-of-replicas> statefulset kafka kafka-zookeeper
  ```
* Set the websub replicas to their original value via the below command.
  ```
  kubectl --kubeconfig /home/mosipuser/.kube/dmzcluster.config -n default scale --replicas= deploy consolidator-websub-service websub-service
  ```
* Restart the websub dependent services:
  * Kernel syncdata service
  * IDREPO services
  * IDA services
  * PMS services
  * RESIDENT services
  * PRINT service
  * Regproc notification service

#### Restore

* Deploy kafka on the restore cluster.
* Run the below command to remove k8s probes and set pod command to `bash -c sleep infinity`.
  ```
  kubectl -n kafka patch statefulset kafka-zookeeper kafka --type=json \
  -p='[
        {"op": "replace", "path": "/spec/template/spec/containers/0/command", "value": ["bash"]},
        {"op": "replace", "path": "/spec/template/spec/containers/0/args", "value": ["-c", "sleep infinity"]},
        {"op": "replace", "path": "/spec/template/spec/containers/0/livenessProbe", "value": null},
        {"op": "replace", "path": "/spec/template/spec/containers/0/readinessProbe", "value": null},
        {"op": "replace", "path": "/spec/template/spec/containers/0/startupProbe", "value": null}
  ]'
  ```
* Wait until all changes are applied and the pods are up and running.
* To clear the data, update N value (i.e., number of replicas) in the below command.
  ```
  for i in {0..N}; do kubectl -n kafka exec -it kafka-zookeeper-$i -- bash -c 'rm -rf /bitnami/zookeeper/data/*'; done
  for i in {0..N}; do kubectl -n kafka exec -it kafka-$i -- bash -c 'rm -rf /bitnami/kafka/data/*'; done
  ```
* Unzip the backup zip file via below command
  ```
  unzip dmzkafka-backup.zip  -d kafka/
  
  chown -R 1001:1001 kafka
  ```
* To copy data from backup, update N value (i.e., number of replicas) in the below command.
  ```
  ## copy zookeeper data
  for i in {0..N}; do
    echo "KAFKA ZOOKEEPER - $i";
    kubectl -n kafka cp kafka/default-data-kafka-zookeeper-$i-*/data  kafka-zookeeper-$i:/bitnami/zookeeper/;
    sleep 10;
    sync; echo 1 > /proc/sys/vm/drop_caches && sync; echo 2 > /proc/sys/vm/drop_caches && sync; echo 3 > /proc/sys/vm/drop_caches
    sleep 10;
  done

  ## copy kafka data
  for i in {0..N}; do
    echo "KAFKA - $i";
    kubectl -n kafka cp kafka/default-data-kafka-$i-*/data  kafka-$i:/bitnami/kafka/;
    sleep 10;
    sync; echo 1 > /proc/sys/vm/drop_caches && sync; echo 2 > /proc/sys/vm/drop_caches && sync; echo 3 > /proc/sys/vm/drop_caches
    sleep 10;
  done
  ```
* Once all data is copied to the pods, then update `logRetentionBytes` to `_-1` in values.yaml file.
* Delete kafka via helm, ensure not to delete the pv/pvc of kafka.
  ```
  helm -n kafka delete kafka
  ```
* Run `install.sh` to deploy kafka.
* Once all the kafka services are up, confirm the existence of topics, messages, etc., via the kafka-ui.
