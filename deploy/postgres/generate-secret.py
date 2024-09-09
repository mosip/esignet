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

# Check if the 'esignet' namespace exists
namespace = "esignet"
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
postgres_secret_name = "esignet-postgres-postgresql"
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
