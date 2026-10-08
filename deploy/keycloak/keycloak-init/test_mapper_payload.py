#!/usr/bin/env python3
"""Regression checks for realm YAML protocol mappers.

Existing entries have no protocol_mapper and must keep producing
oidc-usermodel-attribute-mapper. Audience entries are opt-in.
"""

import unittest
from pathlib import Path

import yaml

import keycloak_init


# Mapper entries shipped in helm/keycloak-init/values.yaml. They have no
# protocol_mapper and must keep the historical user-attribute payload.
EXISTING_HELM_MAPPERS = [
    {"mapper_name": "phoneNumber", "mapper_user_attribute": "phoneNumber", "token_claim_name": "phoneNumber"},
    {"mapper_name": "organizationName", "mapper_user_attribute": "organizationName", "token_claim_name": "organizationName"},
    {"mapper_name": "partnerType", "mapper_user_attribute": "partnerType", "token_claim_name": "partnerType"},
    {"mapper_name": "addressTest", "mapper_user_attribute": "address", "token_claim_name": "addressTest"},
    {"mapper_name": "individual_id", "mapper_user_attribute": "individual_id", "token_claim_name": "individual_id"},
    {"mapper_name": "ida_token", "mapper_user_attribute": "ida_token", "token_claim_name": "ida_token"},
    {"mapper_name": "langCode", "mapper_user_attribute": "langCode", "token_claim_name": "langCode"},
]

VALUES_CANDIDATES = [
    Path(__file__).resolve().parents[1] / "helm" / "keycloak-init" / "values.yaml",
]


def legacy_user_attribute_payload(mapper):
    return {
        "protocol": "openid-connect",
        "config": {
            "id.token.claim": "true",
            "access.token.claim": "true",
            "userinfo.token.claim": "true",
            "multivalued": "",
            "aggregate.attrs": "",
            "user.attribute": mapper["mapper_user_attribute"],
            "claim.name": mapper["token_claim_name"],
            "jsonType.label": "String",
        },
        "name": mapper["mapper_name"],
        "protocolMapper": "oidc-usermodel-attribute-mapper",
    }


def helm_values_mappers():
    values_path = next((path for path in VALUES_CANDIDATES if path.is_file()), None)
    if values_path is None:
        return []
    values = yaml.safe_load(values_path.read_text())
    found = []
    for realm in values["keycloak"]["realms"].values():
        for client in realm.get("clients") or []:
            for mapper in client.get("mappers") or []:
                if mapper:
                    found.append(mapper)
    return found


class MapperPayloadTest(unittest.TestCase):
    def test_existing_helm_mappers_keep_user_attribute_payload(self):
        found = list(EXISTING_HELM_MAPPERS)
        from_chart = helm_values_mappers()
        if from_chart:
            chart_names = {mapper["mapper_name"] for mapper in from_chart}
            fixture_names = {mapper["mapper_name"] for mapper in EXISTING_HELM_MAPPERS}
            self.assertTrue(fixture_names.issubset(chart_names))
            found.extend(from_chart)
        self.assertGreater(len(found), 0)
        for mapper in found:
            self.assertNotIn("protocol_mapper", mapper)
            self.assertEqual(keycloak_init.mapper_payload(mapper), legacy_user_attribute_payload(mapper))

    def test_explicit_user_attribute_type_matches_default(self):
        mapper = {
            "mapper_name": "partnerType",
            "mapper_user_attribute": "partnerType",
            "token_claim_name": "partnerType",
        }
        explicit = dict(mapper)
        explicit["protocol_mapper"] = "oidc-usermodel-attribute-mapper"
        self.assertEqual(keycloak_init.mapper_payload(mapper), keycloak_init.mapper_payload(explicit))

    def test_client_audience_mapper(self):
        payload = keycloak_init.mapper_payload({
            "mapper_name": "target-api-audience",
            "protocol_mapper": "oidc-audience-mapper",
            "included_client_audience": "target-api",
        })
        self.assertEqual(payload["protocolMapper"], "oidc-audience-mapper")
        self.assertEqual(payload["name"], "target-api-audience")
        self.assertEqual(payload["protocol"], "openid-connect")
        self.assertEqual(payload["config"], {
            "access.token.claim": "true",
            "included.client.audience": "target-api",
        })

    def test_custom_audience_mapper(self):
        payload = keycloak_init.mapper_payload({
            "mapper_name": "custom-audience",
            "protocol_mapper": "oidc-audience-mapper",
            "included_custom_audience": "https://api.example.org",
        })
        self.assertEqual(payload["config"], {
            "access.token.claim": "true",
            "included.custom.audience": "https://api.example.org",
        })

    def test_client_and_custom_audience_together(self):
        payload = keycloak_init.mapper_payload({
            "mapper_name": "both",
            "protocol_mapper": "oidc-audience-mapper",
            "included_client_audience": "target-api",
            "included_custom_audience": "https://api.example.org",
        })
        self.assertEqual(payload["config"]["included.client.audience"], "target-api")
        self.assertEqual(payload["config"]["included.custom.audience"], "https://api.example.org")
        self.assertNotIn("user.attribute", payload["config"])
        self.assertNotIn("claim.name", payload["config"])

    def test_audience_mapper_requires_an_audience(self):
        with self.assertRaises(ValueError):
            keycloak_init.mapper_payload({
                "mapper_name": "missing-audience",
                "protocol_mapper": "oidc-audience-mapper",
            })


if __name__ == "__main__":
    unittest.main()
