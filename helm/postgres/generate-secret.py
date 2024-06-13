import base64

# Prompt the user for the password
password = input("Enter the db-dbuser-password: ")

# Encode the password in base64
db_commons_base64_password = base64.b64encode(password.encode()).decode()

# Create the YAML content
db_commons_yaml_content = f"""
apiVersion: v1
kind: Secret
metadata:
  name: db-common-secrets
  namespace: postgres
type: Opaque
data:
  db-dbuser-password: {db_commons_base64_password}
"""

# Write the YAML content to a file
with open("db-common-secrets.yaml", "w") as file:
    file.write(db_commons_yaml_content)


# Prompt the user for the postgres password
postgres_password = input("Enter postgres user password: ")

# Encode the password in base64
postgres_base64_password = base64.b64encode(postgres_password.encode()).decode()

# Create the YAML content
postgres_yaml_content = f"""
apiVersion: v1
kind: Secret
metadata:
  name: postgres-postgresql
  namespace: postgres
type: Opaque
data:
  postgres-password: {postgres_base64_password}
"""

# Write the YAML content to a file
with open("postgres-postgresql.yaml", "w") as file:
    file.write(postgres_yaml_content)
