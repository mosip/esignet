
# Global configmap

## Introduction
Global configmap: Global configmap contains the list of neccesary details to be used throughout the namespaces of the cluster for common details.

## Copy command
```sh
Copy global_configmap.yaml.sample to global_configmap.yaml.
```

## Update the domain names in global_configmap.yaml and run.

```sh
kubectl apply -f global_configmap.yaml
```
