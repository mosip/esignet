import base64
import os

# Function to check if the namespace exists
def check_namespace(namespace):
    result = os.system(f"kubectl get namespace {namespace} > /dev/null 2>&1")
    if result != 0:
        print(f"Namespace '{namespace}' does not exist. Creating namespace...")
        os.system(f"kubectl create namespace {namespace}")
    else:
        print(f"Namespace '{namespace}' already exists.")

# Function to check if a secret already exists
def secret_exists(secret_name, namespace):
    result = os.system(f"kubectl get secret {secret_name} -n {namespace} > /dev/null 2>&1")
    return result == 0

# Function to create or update a secret
def create_or_update_secret(secret_name, namespace, data_key, password):
    base64_password = base64.b64encode(password.encode()).decode()
    yaml_content = f"""
apiVersion: v1
kind: Secret
metadata:
  name: {secret_name}
  namespace: {namespace}
type: Opaque
data:
  {data_key}: {base64_password}
"""
    yaml_file = f"{secret_name}.yaml"
    with open(yaml_file, "w") as file:
        file.write(yaml_content)
    print(f"'{secret_name}' secret YAML written to {yaml_file}.")
    if secret_exists(secret_name, namespace):
        print(f"Updating existing secret '{secret_name}'...")
        os.system(f"kubectl apply -f {yaml_file}")
    else:
        print(f"Creating new secret '{secret_name}'...")
        os.system(f"kubectl create -f {yaml_file} --save-config")

# Function to check if a ConfigMap already exists
def configmap_exists(configmap_name, namespace):
    result = os.system(f"kubectl get configmap {configmap_name} -n {namespace} > /dev/null 2>&1")
    return result == 0

# Function to create or update a ConfigMap
def create_or_update_configmap(configmap_name, namespace, postgres_host, postgres_port, db_user, db_name):
    yaml_content = f"""
apiVersion: v1
kind: ConfigMap
metadata:
  name: {configmap_name}
  namespace: {namespace}
  labels:
    app: postgres
data:
  database-host: "{postgres_host}"
  database-port: "{postgres_port}"
  database-username: "{db_user}"
  database-name: "{db_name}"
"""
    yaml_file = f"{configmap_name}.yaml"
    with open(yaml_file, "w") as file:
        file.write(yaml_content)
    print(f"'{configmap_name}' ConfigMap YAML written to {yaml_file}.")

    if configmap_exists(configmap_name, namespace):
        print(f"Updating existing ConfigMap '{configmap_name}'...")
        os.system(f"kubectl apply -f {yaml_file}")
    else:
        print(f"Creating new ConfigMap '{configmap_name}'...")
        os.system(f"kubectl create -f {yaml_file} --save-config")

# Main script logic
namespace = "postgres"
check_namespace(namespace)

# Handle db-dbuser-password secret
db_secret_name = "db-common-secrets"
if secret_exists(db_secret_name, namespace):
    overwrite = input(f"Secret '{db_secret_name}' already exists in namespace '{namespace}'. Overwrite? (y/n): ")
    if overwrite.lower() == 'y':
        password = input("Enter the db-dbuser-password: ")
        create_or_update_secret(db_secret_name, namespace, "db-dbuser-password", password)
    else:
        print(f"Skipping creation of '{db_secret_name}' secret.")
else:
    print(f"Creating secret '{db_secret_name}'...")
    password = input("Enter the db-dbuser-password: ")
    create_or_update_secret(db_secret_name, namespace, "db-dbuser-password", password)

# Handle postgres-password secret
postgres_secret_name = "postgres-postgresql"
if secret_exists(postgres_secret_name, namespace):
    overwrite = input(f"Secret '{postgres_secret_name}' already exists in namespace '{namespace}'. Overwrite? (y/n): ")
    if overwrite.lower() == 'y':
        postgres_password = input("Enter postgres user password: ")
        create_or_update_secret(postgres_secret_name, namespace, "postgres-password", postgres_password)
    else:
        print(f"Skipping creation of '{postgres_secret_name}' secret.")
else:
    print(f"Creating secret '{postgres_secret_name}'...")
    postgres_password = input("Enter postgres user password: ")
    create_or_update_secret(postgres_secret_name, namespace, "postgres-password", postgres_password)

# Handle ConfigMap creation for PostgreSQL
configmap_name = "postgres-config"
if configmap_exists(configmap_name, namespace):
    overwrite = input(f"ConfigMap '{configmap_name}' already exists in namespace '{namespace}'. Overwrite? (y/n): ")
    if overwrite.lower() == 'y':
        postgres_host = input("Enter PostgreSQL host: ")
        postgres_port = input("Enter PostgreSQL port: ")
        db_user = input("Enter DB user: ")
        db_name = input("Enter DB name: ")
        create_or_update_configmap(configmap_name, namespace, postgres_host, postgres_port, db_user, db_name)
    else:
        print(f"Skipping creation of '{configmap_name}' ConfigMap.")
else:
    print(f"Creating ConfigMap '{configmap_name}'...")
    postgres_host = input("Enter PostgreSQL host: ")
    postgres_port = input("Enter PostgreSQL port: ")
    db_user = "esignetuser"
    db_name = "mosip_esignet"
    create_or_update_configmap(configmap_name, namespace, postgres_host, postgres_port, db_user, db_name)
