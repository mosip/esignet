#!/usr/local/bin/python3
# Script to initialize values in Keycloak

import os
import sys
import argparse
import secrets
import json
import yaml
import traceback
from keycloak import KeycloakAdmin
from keycloak.exceptions import raise_error_from_response, KeycloakError, KeycloakGetError
from keycloak.urls_patterns import URL_ADMIN_USER_REALM_ROLES

USER_ATTRIBUTE_MAPPER = "oidc-usermodel-attribute-mapper"
AUDIENCE_MAPPER = "oidc-audience-mapper"

def mapper_payload(mapper):
    """Build the Keycloak protocol-mapper body for one realm YAML mapper entry.

    Entries without protocol_mapper stay user-attribute mappers. An
    oidc-audience-mapper entry reads included_client_audience and/or
    included_custom_audience and adds that value to the access token aud.
    """
    protocol_mapper = mapper.get("protocol_mapper", USER_ATTRIBUTE_MAPPER)
    if protocol_mapper == AUDIENCE_MAPPER:
        config = {"access.token.claim": "true"}
        if mapper.get("included_client_audience"):
            config["included.client.audience"] = mapper["included_client_audience"]
        if mapper.get("included_custom_audience"):
            config["included.custom.audience"] = mapper["included_custom_audience"]
        if "included.client.audience" not in config and "included.custom.audience" not in config:
            raise ValueError(
                'audience mapper "%s" needs included_client_audience or included_custom_audience'
                % mapper.get("mapper_name")
            )
    else:
        config = {
            "id.token.claim": "true",
            "access.token.claim": "true",
            "userinfo.token.claim": "true",
            "multivalued": "",
            "aggregate.attrs": "",
            "user.attribute": mapper["mapper_user_attribute"],
            "claim.name": mapper["token_claim_name"],
            "jsonType.label": "String",
        }
    return {
        "protocol": "openid-connect",
        "config": config,
        "name": mapper["mapper_name"],
        "protocolMapper": protocol_mapper,
    }

class KeycloakSession:
    def __init__(self, realm, server_url, user, pwd, ssl_verify):
        self.keycloak_admin = KeycloakAdmin(server_url=server_url,
                                            username=user,
                                            password=pwd,
                                            realm_name=realm,
                                            verify=ssl_verify)
    def create_realm(self, realm, realm_config):
        payload = realm_config
        try:
            self.keycloak_admin.create_realm(payload, skip_exists=False)
        except KeycloakError as e:
            if e.response_code == 409:
                print('\tExists, updating %s' % realm)
                self.keycloak_admin.update_realm(realm, payload)
        except:
            raise

    def create_flow_to_auth_flow(self, realm, flow_alias, add_flow_payload, space):
        self.keycloak_admin.realm_name = realm
        payload = add_flow_payload

        URL = 'admin/realms/{realm-name}/authentication/flows/{flow_alias}/executions/flow'
        params_path = {"realm-name": self.keycloak_admin.realm_name, "flow_alias": flow_alias}
        try:
            response = self.keycloak_admin.connection.raw_post(
                URL.format(**params_path),
                data=json.dumps(payload)
            )
            raise_error_from_response(response, KeycloakGetError)

        except KeycloakError as e:
            if e.response_code == 409:
                print(space,'Flow "%s" already exists for flow "%s"; SKIPPING;' % (payload['alias'], flow_alias))
            else:
                raise

    def update_config_to_execution(self, realm, flow_alias,execution_displayName, update_config_payload, space):
        self.keycloak_admin.realm_name = realm
        payload = update_config_payload
        try:
            site_key=os.environ.get(payload['alias']+"-site-key") if os.environ.get(payload['alias']+"-site-key") else payload['config']['secret']
            secret_key=os.environ.get(payload['alias']+"-secret-key") if os.environ.get(payload['alias']+"-secret-key") else payload['config']['site.key']
            use_recaptcha_net=os.environ.get(payload['alias']+"-use-recaptcha-net") if os.environ.get(payload['alias']+"-use-recaptcha-net") else payload['config']['useRecaptchaNet']
            if site_key is None or site_key == '':
                print(space,'\033[91m'+"Environmental variable ",payload['alias']+"-site-key"," Not Found; EXITING")
                exit(1)
            if secret_key is None or secret_key == '':
                print(space,'\033[91m'+"Environmental variable ",payload['alias']+"-secret-key"," Not Found; EXITING")
                exit(1)
            if use_recaptcha_net is None or use_recaptcha_net == '':
                print(space,"Environmental variable ",payload['alias']+"-use-recaptcha-net"," Not Found; Setting empty value :")
                use_recaptcha_net=''
            payload['config']['secret']=secret_key
            payload['config']['site.key']=site_key
            payload['config']['useRecaptchaNet']=use_recaptcha_net

            # print(space, "Update config Payload : ", payload)
            print(space, "Update config Payload : ")
            GET_URL = 'admin/realms/{realm-name}/authentication/flows/{flow_alias}/executions'
            params_path = {"realm-name": self.keycloak_admin.realm_name, "flow_alias": flow_alias}
            GET_RESPONSE = self.keycloak_admin.connection.raw_get(
                GET_URL.format(**params_path)
            )
            AUTH_EXECUTION_LIST=GET_RESPONSE.json()
            for auth_execution in AUTH_EXECUTION_LIST:
                if payload is not None and auth_execution['displayName'] == execution_displayName:
                    URL = 'admin/realms/mosip/authentication/executions/{execution_alias_id}/config'
                    params_path = {"realm-name": self.keycloak_admin.realm_name, "execution_alias_id": auth_execution['id']}
                    response = self.keycloak_admin.connection.raw_post(
                        URL.format(**params_path),
                        data=json.dumps(payload)
                    )
                    raise_error_from_response(response, KeycloakGetError)
                    return
        except KeycloakError as e:
            if e.response_code == 204 or e.response_code == 201:
                print(space,'Execution for flow "%s" updated;' % execution_displayName)
            else:
                raise

    def update_execution_to_auth_flow(self, realm, flow_alias, execution, update_execution_payload=None, space=None):
        self.keycloak_admin.realm_name = realm
        payload = update_execution_payload
        try:
            GET_URL = 'admin/realms/{realm-name}/authentication/flows/{flow_alias}/executions'
            params_path = {"realm-name": self.keycloak_admin.realm_name, "flow_alias": flow_alias}
            GET_RESPONSE = self.keycloak_admin.connection.raw_get(
                GET_URL.format(**params_path)
            )
            AUTH_EXECUTION_LIST=GET_RESPONSE.json()
            for auth_execution in AUTH_EXECUTION_LIST:
                if payload is not None and auth_execution['displayName'] == execution['displayName']:
                    # print(space,'Execution for flow "%s" already exists; SKIPPING;' % auth_execution['displayName'])
                    URL = 'admin/realms/{realm-name}/authentication/flows/{flow_alias}/executions'
                    params_path = {"realm-name": self.keycloak_admin.realm_name, "flow_alias": flow_alias}
                    payload=({**auth_execution, **payload})
                    response = self.keycloak_admin.connection.raw_put(
                        URL.format(**params_path),
                        data=json.dumps(payload)
                    )
                    raise_error_from_response(response, KeycloakGetError)
                    return
        except KeycloakError as e:
            if e.response_code == 202:
                print(space,'Execution for flow "%s" updated;' % execution['displayName'])
            else:
                raise

    def create_execution_to_auth_flow(self, realm, flow_alias, add_execution_payload, space):
        self.keycloak_admin.realm_name = realm
        payload = add_execution_payload
        try:
            GET_URL = 'admin/realms/{realm-name}/authentication/flows/{flow_alias}/executions'
            params_path = {"realm-name": self.keycloak_admin.realm_name, "flow_alias": flow_alias}
            GET_RESPONSE = self.keycloak_admin.connection.raw_get(
                GET_URL.format(**params_path)
            )
            AUTH_EXECUTION_LIST=GET_RESPONSE.json()
            for auth_execution in AUTH_EXECUTION_LIST:
                if auth_execution['displayName'] == payload['displayName']:
                    print(space,'Execution for flow "%s" already exists; SKIPPING;' % add_execution_payload['displayName'])
                    raise_error_from_response(GET_RESPONSE, KeycloakError,None, True)
                    return

            URL = 'admin/realms/{realm-name}/authentication/flows/{flow_alias}/executions/execution'
            params_path = {"realm-name": self.keycloak_admin.realm_name, "flow_alias": flow_alias}

            response = self.keycloak_admin.connection.raw_post(
                URL.format(**params_path),
                data=json.dumps(payload)
            )
            raise_error_from_response(response, KeycloakGetError)

        except KeycloakError as e:
            if e.response_code == 409:
                print(space,'Execution for flow "%s" already exists; SKIPPING;' % add_execution_payload['displayName'])
            else:
                raise


    def create_authentication_flow(self, realm, auth_flow_payload):
        self.keycloak_admin.realm_name = realm
        executions=[]
        if 'authentication_executions' in auth_flow_payload:
           executions = auth_flow_payload.pop('authentication_executions')

        flows=[]
        if 'authentication_flows' in auth_flow_payload:
            flows = auth_flow_payload.pop('authentication_flows')

        payload = auth_flow_payload
        URL = 'admin/realms/{realm-name}/authentication/flows'
        params_path = {"realm-name": self.keycloak_admin.realm_name}
        try:
            response = self.keycloak_admin.connection.raw_post(URL.format(**params_path), data=json.dumps(payload))
            raise_error_from_response(response, KeycloakError)
        except KeycloakError as e:
            if e.response_code == 409:
                print('\t\t\tFlow "%s" already exists; SKIPPING;' % payload['alias'])
            else:
                raise
        for execution in executions:
            space="\t\t\t"
            print(space,'Adding execution : "%s" for "%s"' % (execution['displayName'], payload['alias']))
            update_config=None
            if 'update_config' in execution:
                update_config=execution.pop('update_config')
            update_execution={}
            if 'update_execution' in execution:
                update_execution=execution.pop('update_execution')
            self.create_execution_to_auth_flow(realm, payload['alias'], execution, space+"\t")
            self.update_execution_to_auth_flow(realm, payload['alias'], execution, update_execution, space+"\t")
            if update_config is not None:
                self.update_config_to_execution(realm, payload['alias'], execution['displayName'], update_config, space+"\t")

        for flow in flows:
            space="\t\t\t"
            print(space,'Adding flow : "%s" for "%s" ' % (flow['alias'], payload['alias']))
            flow_authentication_executions={}
            if 'authentication_executions' in flow:
                flow_authentication_executions=flow.pop('authentication_executions')
            flow_authentication_flows={}
            if 'authentication_flows' in flow:
                flow_authentication_flows=flow.pop('authentication_flows')
            update_flow_requirement={}
            if 'update_flow_requirement' in flow:
                update_flow_requirement=flow.pop('update_flow_requirement')

            ## create the flow
            self.create_flow_to_auth_flow(realm, payload['alias'], flow, space)

            ## update the requirement in flow
            print(space,'Updating requirement for flow : "%s"' % flow['displayName'])
            self.update_execution_to_auth_flow(realm, payload['alias'], flow, update_flow_requirement, space+"\t")

            for flow_execution in flow_authentication_executions:
                exec_space=space+'\t'
                print(exec_space,'Adding execution : "%s" to flow "%s" ' % (flow_execution['displayName'], flow['alias']))
                update_config=None
                if 'update_config' in flow_execution:
                    update_config=flow_execution.pop('update_config')
                self.create_execution_to_auth_flow(realm, flow['alias'], flow_execution, exec_space)
                if update_config is not None:
                    print(exec_space,'Updating execution config, "Google Captcha Site key & Secret Key" to execution : "%s" of flow "%s" ' % (flow_execution['displayName'], flow['alias']))
                    self.update_config_to_execution(realm, payload['alias'], flow_execution['displayName'], update_config, exec_space)

            for add_flow in flow_authentication_flows:
                flow_space=space+'\t'
                print(flow_space,'Adding flow : "%s" to flow "%s" ' % (add_flow['alias'], flow['alias']))
                update_execution={}
                if 'update_execution' in add_flow:
                    update_execution=add_flow.pop('update_execution')
                auth_flow_execs={}
                if 'authentication_executions' in add_flow:
                    auth_flow_execs=add_flow.pop('authentication_executions')

                self.create_flow_to_auth_flow(realm, flow['alias'], add_flow, flow_space+"\t")

                print(flow_space,'Updating requirement for flow : "%s"' % flow['displayName']  )
                self.update_execution_to_auth_flow(realm, flow['alias'], add_flow, update_execution, flow_space)


                for auth_flow_exec in auth_flow_execs:
                    update_execution={}
                    if 'update_execution' in auth_flow_exec:
                        update_execution=auth_flow_exec.pop('update_execution')
                    print(flow_space,'Adding execution : "%s" to flow "%s" ' % (auth_flow_exec['displayName'], flow['alias']))
                    self.create_execution_to_auth_flow(realm, add_flow['alias'], auth_flow_exec, flow_space+"\t")
                    self.update_execution_to_auth_flow(realm, add_flow['alias'], auth_flow_exec, update_execution, flow_space+'\t')



    def delete_realm(self, realm, skip_exists=False):
        self.keycloak_admin.realm_name = realm  # work around because otherwise client was getting created in master
        url = 'admin/realms/'+realm
        payload = {
        }
        try:
            print('\tDeleting realm %s' % realm)
            data_raw = self.keycloak_admin.connection.raw_delete(url, data=json.dumps(payload))
            return raise_error_from_response(data_raw, KeycloakGetError, skip_exists=skip_exists)

        except KeycloakError as e:
            if e.response_code == 404:
                print('\tRealm %s does not exists; SKIPPING;' % realm)

        except:
            self.keycloak_admin.realm_name = 'master'  # restore
            raise

    def delete_client(self, realm, client, skip_exists=False):
        self.keycloak_admin.realm_name = realm  # work around because otherwise client was getting created in master
        try:
            print('\tDeleting client %s' % client)
            client_id = self.keycloak_admin.get_client_id(client)
            data_raw = self.keycloak_admin.delete_client(client_id)

        except KeycloakError as e:
            if e.response_code == 404:
                print('\tClient %s does not exists; SKIPPING;' % client)

        except:
            self.keycloak_admin.realm_name = 'master'  # restore
            raise

    # del_sa_roles: remove roles from service account user
    def remove_sa_roles(self, realm, client, del_sa_roles=None):
        self.keycloak_admin.realm_name = realm
        try:
            roles = []  # Get full role reprentation of all roles
            for role in del_sa_roles:
                print("\t\tRole : %s" % role)
                role_rep = self.keycloak_admin.get_realm_role(role)
                roles.append(role_rep)
            client_id = self.keycloak_admin.get_client_id(client)
            user = self.keycloak_admin.get_client_service_account_user(client_id)
            params_path = {"realm-name": self.keycloak_admin.realm_name, "id": user["id"]}
            self.keycloak_admin.raw_delete(URL_ADMIN_USER_REALM_ROLES.format(**params_path), data=json.dumps(roles))
        except:
            self.keycloak_admin.realm_name = 'master' # restore
            raise

        self.keycloak_admin.realm_name = 'master' # restore

    def delete_realm_role(self, realm, role, skip_exists=False):
        self.keycloak_admin.realm_name = realm
        try:
            print("\tRole : %s" % role)
            role_rep = self.keycloak_admin.get_realm_role(role)
            URL_ADMIN_REALM_ROLE = 'admin/realms/{realm-name}/roles-by-id/{id}'
            payload = {
            }
            params_path = {"realm-name": self.keycloak_admin.realm_name, "id": role_rep["id"]}
            data_raw = self.keycloak_admin.raw_delete(URL_ADMIN_REALM_ROLE.format(**params_path), data=json.dumps(payload))
            return raise_error_from_response(data_raw, KeycloakGetError, skip_exists=skip_exists)

        except KeycloakError as e:
            if e.response_code == 404:
                print('\tRole %s does not exists; SKIPPING;' % role)
        except:
            self.keycloak_admin.realm_name = 'master' # restore
            raise

        self.keycloak_admin.realm_name = 'master' # restore

    def create_role(self, realm, role):
        print('\tCreating role %s for realm %s' % (role, realm))
        self.keycloak_admin.realm_name = realm  # work around because otherwise role was getting created in master
        self.keycloak_admin.create_realm_role({'name' : role, 'clientRole' : False}, skip_exists=True)
        self.keycloak_admin.realm_name = 'master' # restore

    def get_auth_flow_id(self, realm, alias):
        self.keycloak_admin.realm_name = realm  # work around because otherwise client was getting created in master
        try:
            payload = {}
            auth_id = None
            URL = 'admin/realms/{realm-name}/authentication/flows'
            params_path = {"realm-name": self.keycloak_admin.realm_name}
            data_raw = self.keycloak_admin.connection.raw_get(URL.format(**params_path), data=json.dumps(payload))
            for authflow in data_raw.json():
                if authflow['alias'] == alias:
                    auth_id = authflow['id']
            if auth_id == None:
                print("\tAuthflow id not found for alias ", alias)
                exit(1)
            return auth_id

        except:
            self.keycloak_admin.realm_name = 'master'  # restore
            raise

    def create_public_client(self, realm, client, redirect_urls, web_origins, direct_grant_flow_id, browser_flow_id):
        self.keycloak_admin.realm_name = realm  # work around because otherwise client was getting created in master
        payload = {
            "clientId": "mosip-toolkit-android-client",
            "baseUrl": "",
            "surrogateAuthRequired": False,
            "enabled": True,
            "alwaysDisplayInConsole": False,
            "clientAuthenticatorType": "client-secret",
            "redirectUris": redirect_urls,
            "webOrigins": web_origins,
            "bearerOnly": False,
            "consentRequired": False,
            "standardFlowEnabled": True,
            "implicitFlowEnabled": False,
            "directAccessGrantsEnabled": True,
            "serviceAccountsEnabled": False,
            "publicClient": True,
            "protocol": "openid-connect",
            "authenticationFlowBindingOverrides": {"direct_grant": direct_grant_flow_id, "browser": browser_flow_id},
            "fullScopeAllowed": True
        }
        try:
            print('\tCreating public client %s' % client)
            self.keycloak_admin.create_client(payload, skip_exists=False)  # If exists, update. So don't skip
        except KeycloakError as e:
            if e.response_code == 409:
                print('\tExists, updating %s' % client)
                client_id = self.keycloak_admin.get_client_id(client)
                self.keycloak_admin.update_client(client_id, payload)
        except:
            self.keycloak_admin.realm_name = 'master'  # restore
            raise

        self.keycloak_admin.realm_name = 'master'  # restore

    # sa_roles: service account roles
    def create_client(self, realm, client, secret, sa_roles=None):
        self.keycloak_admin.realm_name = realm  # work around because otherwise client was getting created in master
        payload = {
          "clientId" : client,
          "secret" : secret,
          "standardFlowEnabled": True,
          "serviceAccountsEnabled": True,
          "directAccessGrantsEnabled": True,
          "redirectUris": ['*'],
          "authorizationServicesEnabled": True
        }
        try:
            print('\tCreating client %s' % client)
            self.keycloak_admin.create_client(payload, skip_exists=False)  # If exists, update. So don't skip
        except KeycloakError as e:
            if e.response_code == 409:
                print('\tExists, updating %s' % client)
                client_id = self.keycloak_admin.get_client_id(client)
                self.keycloak_admin.update_client(client_id, payload)
        except:
            self.keycloak_admin.realm_name = 'master' # restore
            raise

        if len(sa_roles) == 0:  # Skip the below step
            self.keycloak_admin.realm_name = 'master' # restore
            return

        try:
            roles = [] # Get full role reprentation of all roles
            for role in sa_roles:
                role_rep = self.keycloak_admin.get_realm_role(role)
                roles.append(role_rep)
            client_id = self.keycloak_admin.get_client_id(client)
            user = self.keycloak_admin.get_client_service_account_user(client_id)
            params_path = {"realm-name": self.keycloak_admin.realm_name, "id": user["id"]}
            self.keycloak_admin.raw_post(URL_ADMIN_USER_REALM_ROLES.format(**params_path), data=json.dumps(roles))
        except:
            self.keycloak_admin.realm_name = 'master' # restore
            raise

        self.keycloak_admin.realm_name = 'master' # restore

    def create_mapper(self, realm, client, mapper, skip_exists=False):
        self.keycloak_admin.realm_name = realm  # work around because otherwise client was getting created in master
        client_id = self.keycloak_admin.get_client_id(client)
        mapper_url = 'admin/realms/'+realm+'/clients/'+client_id+'/protocol-mappers/models'
        payload = mapper_payload(mapper)
        try:
            print('\t\tCreating Mapper %s' % mapper['mapper_name'])
            data_raw = self.keycloak_admin.connection.raw_post(mapper_url, data=json.dumps(payload))
            return raise_error_from_response(data_raw, KeycloakGetError, skip_exists=skip_exists)

        except KeycloakError as e:
            if e.response_code == 409:
                print('\t\tMapper %s Exists already exists; SKIPPING;' % mapper['mapper_name'])

        except:
            self.keycloak_admin.realm_name = 'master' # restore
            raise

    def create_user(self, realm, uname, email, fname, lname, password, temp_flag, attributes={}):
        self.keycloak_admin.realm_name = realm
        payload = {
          "username" : uname,
          "email" : email,
          "firstName" : fname,
          "lastName" : lname,
          "enabled": True,
          "attributes": attributes
        }
        try:
            print('Creating user %s' % uname)
            self.keycloak_admin.create_user(payload, False) # If exists, update. So don't skip
            user_id = self.keycloak_admin.get_user_id(uname)
            self.keycloak_admin.set_user_password(user_id, password, temporary=temp_flag)
        except KeycloakError as e:
            if e.response_code == 409:
                print('Exists, updating %s' % uname)
                user_id = self.keycloak_admin.get_user_id(uname)
                self.keycloak_admin.update_user(user_id, payload)
        except:
            self.keycloak_admin.realm_name = 'master' # restore
            raise

        self.keycloak_admin.realm_name = 'master' # restore

    def assign_user_roles(self, realm, username, roles):
        self.keycloak_admin.realm_name = realm
        roles = [self.keycloak_admin.get_realm_role(role) for role in roles]
        try:
            print(f'''Get user id for {username}''')
            user_id = self.keycloak_admin.get_user_id(username)
            self.keycloak_admin.assign_realm_roles(user_id, roles)
        except:
            self.keycloak_admin.realm_name = 'master' # restore
            raise

        self.keycloak_admin.realm_name = 'master' # restore

    def assign_client_roles(self, realm, client, role):
        self.keycloak_admin.realm_name = realm
        try:
            client_id = self.keycloak_admin.get_client_id(client)
            role_representation = {
                "name": role["role_name"],
                "description": role.get("role_description", "")
            }
            
            # Prepare URL and payload for raw_post
            URL = 'admin/realms/{realm-name}/clients/{client-id}/roles'
            params_path = {"realm-name": self.keycloak_admin.realm_name, "client-id": client_id}
            payload = json.dumps(role_representation)
            
            # Perform the POST requests
            print(f'Creating role {role["role_name"]} for client {client}')
            data_raw = self.keycloak_admin.connection.raw_post(URL.format(**params_path), data=payload)
            return raise_error_from_response(data_raw, KeycloakGetError)
        except:
            self.keycloak_admin.realm_name = 'master' # restore
            raise

    def assign_client_roles_to_user(self, realm, username, client, client_roles=None):
        self.keycloak_admin.realm_name = realm
        try:
           # Get the user ID for the specified username
           users = self.keycloak_admin.get_users({"username": username})
           if not users:
               raise KeycloakGetError(f"User '{username}' not found.")
           user = users[0]  # Assuming usernames are unique and we're getting the first match
           # Get the client ID
           client_id = self.keycloak_admin.get_client_id(client)

           # Get full role representations for each client role
           client_role_list = []
           for client_role in client_roles:
               print('\t\t\tAdding Client Role :: "%s" to =====> user :: "%s"' %(client_role, user['username']))
               client_role_list.append(self.keycloak_admin.get_client_role(client_id, client_role))

           # Construct the URL for role-mappings endpoint
           URL = 'admin/realms/{realm-name}/users/{user-id}/role-mappings/clients/{client-id}'
           params_path = {"realm-name": self.keycloak_admin.realm_name, "user-id": user["id"], "client-id": client_id}
           # Make the POST request to assign roles
           data_raw = self.keycloak_admin.connection.raw_post(URL.format(**params_path), data=json.dumps(client_role_list))
           return raise_error_from_response(data_raw, KeycloakGetError)
        except Exception as e:
            self.keycloak_admin.realm_name = 'master'  # restore the realm
            raise e

    def assign_sa_client_roles(self, realm, client, sa_client, sa_client_roles=None):
        self.keycloak_admin.realm_name = realm
        try:
            client_id = self.keycloak_admin.get_client_id(client)
            user = self.keycloak_admin.get_client_service_account_user(client_id)
            sa_client_id = self.keycloak_admin.get_client_id(sa_client)
            sa_client_role_list = []  # Get full role representation of all roles

            for sa_client_role in sa_client_roles:
                print('\t\t\tAdding Client Role :: "%s" to =====> client user :: "%s"' %(sa_client_role, user['username']))
                sa_client_role_list.append(self.keycloak_admin.get_client_role(sa_client_id, sa_client_role))

            URL = 'admin/realms/{realm-name}/users/{user}/role-mappings/clients/{sa-client-id}'
            params_path = {"realm-name": self.keycloak_admin.realm_name, "user": user["id"], "sa-client-id": sa_client_id}
            data_raw = self.keycloak_admin.connection.raw_post(URL.format(**params_path), data=json.dumps(sa_client_role_list))
            return raise_error_from_response(data_raw, KeycloakGetError)
        except:
            self.keycloak_admin.realm_name = 'master' # restore
            raise

    def get_client_scope_id(self, realm, client_scope_name):
        self.keycloak_admin.realm_name = realm

        try:
            payload = {}
            URL = 'admin/realms/{realm-name}/client-scopes'
            params_path = {"realm-name": self.keycloak_admin.realm_name}
            data_raw = self.keycloak_admin.connection.raw_get(URL.format(**params_path), data=json.dumps(payload))
            for client_scope in data_raw.json():
                if client_scope['name'] == client_scope_name:
                    return client_scope['id']
            return raise_error_from_response(data_raw, KeycloakGetError)

        except:
            self.keycloak_admin.realm_name = 'master'  # restore
            raise

    def update_client_scope(self, realm, client_scope, client_scope_id):
        self.keycloak_admin.realm_name = realm

        try:
            payload = client_scope
            payload['id'] = client_scope_id
            URL = 'admin/realms/{realm-name}/client-scopes/{client-scope-id}'
            params_path = {"realm-name": self.keycloak_admin.realm_name, "client-scope-id": client_scope_id}
            data_raw = self.keycloak_admin.connection.raw_put(URL.format(**params_path), data=json.dumps(payload))
            return raise_error_from_response(data_raw, KeycloakGetError)
        except:
            self.keycloak_admin.realm_name = 'master'  # restore
            raise

    def create_client_scope(self, realm, client_scope):
        self.keycloak_admin.realm_name = realm

        try:
            payload = client_scope
            print("\tCreating client scope \"%s\" for realm %s " % (payload['name'], self.keycloak_admin.realm_name))
            URL = 'admin/realms/{realm-name}/client-scopes'
            params_path = {"realm-name": self.keycloak_admin.realm_name}
            data_raw = self.keycloak_admin.connection.raw_post(URL.format(**params_path), data=json.dumps(payload))
            return raise_error_from_response(data_raw, KeycloakGetError)
        except KeycloakError as e:
            if e.response_code == 409:
                print('\tExists, updating "%s"' % payload['name'])
                client_scope_id = self.get_client_scope_id(self.keycloak_admin.realm_name, payload['name'])
                self.update_client_scope(self.keycloak_admin.realm_name, payload, client_scope_id)
        except:
            self.keycloak_admin.realm_name = 'master'  # restore
            raise

    def assign_client_scope(self, realm, client_name, client_scope):
        self.keycloak_admin.realm_name = realm

        try:
            client_scope_id = self.get_client_scope_id(realm, client_scope)
            client_id = self.keycloak_admin.get_client_id(client_name)
            payload = {"client": client_id, "clientScopeId": client_scope_id, "realm": self.keycloak_admin.realm_name}
            print("\t\tAssigning client scope \"%s\" for %s client " % (client_scope, client_name))
            URL = 'admin/realms/{realm-name}/clients/{client-id}/default-client-scopes/{client-scope-id}'
            params_path = {"realm-name": self.keycloak_admin.realm_name, "client-id": client_id, "client-scope-id": client_scope_id}
            data_raw = self.keycloak_admin.connection.raw_put(URL.format(**params_path), data=json.dumps(payload))
            return raise_error_from_response(data_raw, KeycloakGetError)

        except:
            self.keycloak_admin.realm_name = 'master'  # restore
            raise


def args_parse():
    parser = argparse.ArgumentParser()
    parser.add_argument('server_url', type=str, help='Full url to point to the server for auth: Eg. https://iam.xyz.com/auth/.  Note: slash is important')
    parser.add_argument('user', type=str, help='Admin user')
    parser.add_argument('password', type=str, help='Admin password')
    parser.add_argument('input_yaml', type=str, help='File containing input for roles and clients in YAML format')
    parser.add_argument('--disable_ssl_verify', help='Disable ssl cert verification while connecting to server', action='store_true')

    args = parser.parse_args()
    return args

def main():

    args =  args_parse()
    server_url = args.server_url
    user = args.user
    password = args.password
    input_yaml = args.input_yaml

    ssl_verify = True
    if args.disable_ssl_verify:
        ssl_verify = False

    fp = open(input_yaml, 'rt')
    values = yaml.load(fp, Loader=yaml.FullLoader)

    server_url = server_url # Full url to access api

    try:
        print(server_url)
        ks = KeycloakSession('master', server_url, user, password, ssl_verify)
        for realm in values:
            if realm == "del_realms":
                print("Delete realms : ")
                for r in values[realm]:
                    ks.delete_realm(r)
                break

        print('Create realms :')
        for realm in values:
            if realm == "del_realms":
                continue
            print('\tCreate realms : %s' %realm)
            if 'realm_config' in values[realm]:
                ks.create_realm(realm, values[realm]['realm_config'])  # {realm : [role]}

        for realm in values:
            roles = []
            if 'roles' in values[realm]:
                print('Create roles for realm %s' % realm)
                roles = values[realm]['roles']
            for role in roles:
                ks.create_role(realm, role)

            del_roles = []
            if 'del_roles' in values[realm]:
                print('Delete roles for realm %s' % realm)
                del_roles = values[realm]['del_roles']
            for role in del_roles:
                ks.delete_realm_role(realm, role)
            del_clients = []
            if 'del_clients' in values[realm]:
                print("Delete clients for realm %s" % realm)
                del_clients = values[realm]['del_clients']
            for client in del_clients:
                ks.delete_client(realm, client)

            client_scopes = []
            if 'client_scopes' in values[realm]:
                print("Create client scopes for realm %s" % realm)
                client_scopes = values[realm]['client_scopes']
            for client_scope in client_scopes:
                ks.create_client_scope(realm, client_scope)

            authentication = []
            if 'authentication' in values[realm]:
                authentication = values[realm]['authentication']
                auth_flows = []
                if 'auth_flows' in authentication:
                    auth_flows = authentication['auth_flows']
                    print('\tCreating authentication flows')
                    for auth_flow in auth_flows:
                        print('\t\tCreating authentication flow for "%s" ' % auth_flow['alias'])
                        ks.create_authentication_flow(realm, auth_flow)

            # Expect secrets passed via env variables.
            clients = []
            if 'clients' in values[realm]:
                print('\nCreate clients for realm %s' % realm)
                clients = values[realm]['clients']
            for client in clients:
                secret_env_name = '%s_secret' % client['name']
                secret_env_name = secret_env_name.replace('-', '_') # Compatible with environment variables
                secret = os.environ.get(secret_env_name)
                if secret is None:  # Env variable not found
                    print('\n\tSecret environment variable %s not found, generating' % secret_env_name)
                    secret = secrets.token_urlsafe(16)

                if 'public_client' in client and client['public_client']:
                    direct_grant_flow_id = ks.get_auth_flow_id(realm, client['direct_grant_flow_alias'])
                    browser_flow_id = ks.get_auth_flow_id(realm, client['browser_flow_alias'])
                    ks.create_public_client(realm, client['name'], client['redirect_urls'], client['web_origins'], direct_grant_flow_id, browser_flow_id)
                elif 'saroles' in client:
                    ks.create_client(realm, client['name'], secret, client['saroles'])

                if 'del_saroles' in client:
                    print("\tRemoving roles from service account user for client %s" % client['name'])
                    ks.remove_sa_roles(realm, client['name'], client['del_saroles'])

                if 'mappers' in client:
                    mappers = client['mappers']
                    print("\tCreating mappers for %s client " % client['name'])
                    for mapper in mappers:
                        ks.create_mapper(realm, client['name'], mapper)

                if 'roles' in client:
                    roles = client['roles']
                    print("\tCreating new role for %s client " % client['name'])
                    for role in roles:
                        ks.assign_client_roles(realm, client['name'], role)

                if 'sa_client_roles' in client:
                    sa_client_roles = client['sa_client_roles']
                    print('\tAssigning service account client roles for %s client ' % client['name'])
                    for cid_roles in sa_client_roles:
                        sa_client = list(cid_roles)[0]
                        sa_client_role_list = cid_roles[sa_client]
                        print('\t\tService account client name :: "%s"' % list(cid_roles)[0])
                        ks.assign_sa_client_roles(realm, client['name'], sa_client, sa_client_role_list)

                if 'assign_client_scopes' in client:
                    assign_client_scopes = client['assign_client_scopes']
                    print('\tAssigning client scopes for %s client ' % client['name'])
                    for client_scope in assign_client_scopes:
                        ks.assign_client_scope(realm, client['name'], client_scope)

                if 'authentication_flow_overrides' in client:
                    cid=None
                    client_data=None
                    print('\tUpdating "authentication flow overrides" for client : "%s"' % client['name'])

                    for auth_flow_overrides in client['authentication_flow_overrides']:
                        print('\t\tAuth flow overrides "%s" : "%s"' %(auth_flow_overrides, client['authentication_flow_overrides'][auth_flow_overrides]))
                        authentication_flow_overrides_id = ks.get_auth_flow_id(realm, client['authentication_flow_overrides'][auth_flow_overrides])
                        cid=ks.keycloak_admin.get_client_id(client['name'])
                        client_data=ks.keycloak_admin.get_client(cid)
                        client_data['authenticationFlowBindingOverrides'][auth_flow_overrides]=authentication_flow_overrides_id
                        print('\t\t\tauthentication_flow_overrides_id : ', authentication_flow_overrides_id)
                        print("\t\t\tClient ID : ",cid)
                        print("\t\t\tClient Data authentication_flow_overrides_id : ",client_data['authenticationFlowBindingOverrides'])
                    if cid is not None and client_data is not None:
                        ks.keycloak_admin.update_client(cid,client_data)
                        print("\t\t\t'authenticationFlowBindingOverrides' successfully updated for client '%s' "% client['name'])


            users = []
            if 'users' in values[realm]:
                users = values[realm]['users']
            for user in users:
                print(f'''Creating user {user['username']}''')
                ks.create_user(realm, user['username'], user['email'], user['firstName'], user['lastName'], user['password'], user['temporary'], user['attributes'])
                ks.assign_user_roles(realm, user['username'], user['realmRoles'])
                ks.assign_client_roles_to_user(realm, user['username'], user['client'], user['clientRoles'])
    except:
        formatted_lines = traceback.format_exc()
        print(formatted_lines)
        sys.exit(1)

    sys.exit(0)

if __name__=="__main__":
    main()
