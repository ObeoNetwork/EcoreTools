# Copyright (c) 2026 Obeo.
# SPDX-License-Identifier: EPL-2.0

"""Check saved Ecore instantiation and Studio authoring; remove only test projects.
Usage: python3.13 tests/instances.py http://localhost:8083
"""
import sys
import uuid
import xml.etree.ElementTree as ET
import requests

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8083"
projects = []


def graphql(query, **variables):
    response = requests.post(BASE + "/api/graphql", json={"query": query, "variables": variables}, timeout=30)
    response.raise_for_status()
    result = response.json()
    assert "errors" not in result, result
    return result["data"]


def project(template):
    result = graphql("""mutation($input:CreateProjectInput!) {
      createProject(input:$input) { __typename
        ... on CreateProjectSuccessPayload { project { id currentEditingContext { id } } }
        ... on ErrorPayload { messages { body } }
      }
    }""", input={"id": str(uuid.uuid4()), "name": "Instance regression test", "templateId": template,
                "libraryIds": []})["createProject"]
    assert result["__typename"] == "CreateProjectSuccessPayload", result
    projects.append(result["project"]["id"])
    return result["project"]["currentEditingContext"]["id"]


def create(context, stereotype, name):
    result = graphql("""mutation($input:CreateDocumentInput!) {
      createDocument(input:$input) { __typename
        ... on CreateDocumentSuccessPayload { document { id name } }
        ... on ErrorPayload { messages { body } }
      }
    }""", input={"id": str(uuid.uuid4()), "editingContextId": context, "stereotypeId": stereotype,
                "name": name})["createDocument"]
    assert result["__typename"] == "CreateDocumentSuccessPayload", result
    return result["document"]


try:
    definition = project("Ecore example")
    namespace = "urn:ecore-editor:test:" + str(uuid.uuid4())
    renamed = graphql("""mutation($input:EvaluateExpressionInput!) {
      evaluateExpression(input:$input) { __typename
        ... on ErrorPayload { messages { body } }
      }
    }""", input={"id": str(uuid.uuid4()), "editingContextId": definition, "selectedObjectIds": [],
                "expression": "aql:editingContext.allContents()->filter(ecore::EPackage)->select(p | p.name = 'example')->first().eSet('nsURI', '" + namespace + "')"})["evaluateExpression"]
    assert renamed["__typename"] == "EvaluateExpressionSuccessPayload", renamed
    context = project("blank-project")
    # The expression API represents an empty collection as StringsExpressionResult.
    blank_resources = graphql("""mutation($input:EvaluateExpressionInput!) {
      evaluateExpression(input:$input) { ... on EvaluateExpressionSuccessPayload {
        result { ... on StringsExpressionResult { value } }
      } }
    }""", input={"id": str(uuid.uuid4()), "editingContextId": context, "selectedObjectIds": [],
                "expression": "aql:editingContext.allContents().eResource()->asSet()"})["evaluateExpression"]["result"]
    assert blank_resources == {"value": []}, blank_resources
    model_types = graphql("""query($ctx:ID!) { viewer { editingContext(editingContextId:$ctx) {
      stereotypes { edges { node { id label } } }
      actions { edges { node { id } } }
    } } }""", ctx=context)["viewer"]["editingContext"]
    types = {e["node"]["id"]: e["node"]["label"] for e in model_types["stereotypes"]["edges"]}
    instance_id = "ecore-editor:instance:" + namespace
    assert types[instance_id] in {"example", "example (" + namespace + ")"}, types
    assert "ecore-editor:ecore" in types, types
    assert not {"domain", "view"} & types.keys(), types
    assert not any(type_id.startswith(instance_id + "#//") for type_id in types), types
    assert not {"empty_domain", "empty_view"} & {e["node"]["id"] for e in model_types["actions"]["edges"]}
    instance = create(context, instance_id, "Instances")
    roots = graphql("""query($ctx:ID!, $domain:ID!) { viewer { editingContext(editingContextId:$ctx) {
      rootObjectCreationDescriptions(domainId:$domain, suggested:false) { id label }
    } } }""", ctx=context, domain=namespace)["viewer"]["editingContext"]["rootObjectCreationDescriptions"]
    root_types = {root["id"] for root in roots}
    assert {"Source", "E"} <= root_types, roots
    assert not {"Interface", "Enumeration", "SomeDataType"} & root_types, roots
    root = graphql("""mutation($input:CreateRootObjectInput!) {
      createRootObject(input:$input) { __typename
        ... on CreateRootObjectSuccessPayload { object { id } }
        ... on ErrorPayload { messages { body } }
      }
    }""", input={"id": str(uuid.uuid4()), "editingContextId": context, "documentId": instance["id"],
                "domainId": namespace, "rootObjectCreationDescriptionId": "E"})["createRootObject"]
    assert root["__typename"] == "CreateRootObjectSuccessPayload", root
    document = requests.get(BASE + "/api/editingcontexts/" + context + "/documents/" + instance["id"],
                            headers={"Accept": "application/xml"}, timeout=30)
    document.raise_for_status()
    assert ET.fromstring(document.content).tag == "{" + namespace + "}E", document.text
    assert create(context, "ecore-editor:ecore", "New metamodel")["name"] == "New metamodel.ecore"
    studio = project("blank-studio-template")
    studio_types = graphql("""query($ctx:ID!) { viewer { editingContext(editingContextId:$ctx) {
      stereotypes { edges { node { id } } }
      actions { edges { node { id } } }
    } } }""", ctx=studio)["viewer"]["editingContext"]
    assert {"domain", "view"} <= {e["node"]["id"] for e in studio_types["stereotypes"]["edges"]}, studio_types
    assert {"empty_domain", "empty_view"} <= {e["node"]["id"] for e in studio_types["actions"]["edges"]}, studio_types
    assert create(studio, "view", "Views")["name"] == "Views"
    assert create(studio, "domain", "Domain")["name"] == "Domain"
    # Opening a fresh project reloads saved metamodels and Studio View documents.
    fresh = project("blank-project")
    available = graphql("""query($ctx:ID!) { viewer { editingContext(editingContextId:$ctx) {
      stereotypes { edges { node { id } } }
    } } }""", ctx=fresh)["viewer"]["editingContext"]["stereotypes"]["edges"]
    assert instance_id in {e["node"]["id"] for e in available}, available
    print("PASS: Blank projects exclude Studio palettes/actions, package-level model types and instance XML export work, and Studio projects retain Domain/View creation")
finally:
    for project_id in reversed(projects):
        result = graphql("""mutation($input:DeleteProjectInput!) {
          deleteProject(input:$input) { __typename ... on ErrorPayload { messages { body } } }
        }""", input={"id": str(uuid.uuid4()), "projectId": project_id})["deleteProject"]
        assert result["__typename"] == "SuccessPayload", result
