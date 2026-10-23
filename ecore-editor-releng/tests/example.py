# Copyright (c) 2026 Obeo.
# SPDX-License-Identifier: EPL-2.0

"""Verify that the Example template includes its model and a saved, laid-out diagram.
Usage (from ecore-editor-releng): python3.13 tests/example.py http://localhost:8083
"""
import json
import sys
import uuid
import xml.etree.ElementTree as ET
import requests
import websocket

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8083"


def graphql(query, **variables):
    response = requests.post(BASE + "/api/graphql", json={"query": query, "variables": variables}, timeout=20)
    response.raise_for_status()
    result = response.json()
    assert "errors" not in result, result
    return result["data"]


created = graphql("""mutation($input:CreateProjectInput!) {
 createProject(input:$input) { __typename
  ... on CreateProjectSuccessPayload { project { id currentEditingContext { id } } }
  ... on ErrorPayload { messages { body } }
 }
}""", input={"id": str(uuid.uuid4()), "name": "EcoreTools example", "templateId": "Ecore example", "libraryIds": []})["createProject"]
assert created["__typename"] == "CreateProjectSuccessPayload", created
project = created["project"]
context = project["currentEditingContext"]["id"]
representations = graphql("""query($ctx:ID!) {
 viewer { editingContext(editingContextId:$ctx) { representations { edges { node { id label iconURLs } } } } }
}""", ctx=context)["viewer"]["editingContext"]["representations"]["edges"]
assert len(representations) == 1, representations
assert representations[0]["node"]["label"] == "Example", representations
assert len(representations[0]["node"]["iconURLs"]) == 1, representations
assert representations[0]["node"]["iconURLs"][0].endswith("/icons/ecore/EcoreClassDiagram.svg"), representations
diagram_id = representations[0]["node"]["id"]
document = graphql("""mutation($input:EvaluateExpressionInput!) {
 evaluateExpression(input:$input) { ... on EvaluateExpressionSuccessPayload {
  result { ... on ObjectExpressionResult { value { id label } } }
 } ... on ErrorPayload { messages { body } } }
}""", input={"id": str(uuid.uuid4()), "editingContextId": context, "selectedObjectIds": [],
             "expression": "aql:editingContext.allContents()->filter(ecore::EPackage)->first().eResource()"})["evaluateExpression"]["result"]["value"]
assert document["label"] == "Ecore example.ecore", document
download = requests.get(BASE + "/api/editingcontexts/" + context + "/documents/" + document["id"],
                        headers={"Accept": "application/xml"}, timeout=20)
download.raise_for_status()
assert 'filename="Ecore example.ecore"' in download.headers["Content-Disposition"], download.headers
model = ET.fromstring(download.content)
assert model.tag == "{http://www.eclipse.org/emf/2002/Ecore}EPackage", model.tag
assert model.attrib["name"] == "example", model.attrib
node = "id targetObjectId insideLabel { text style { bold color iconURL } }"
query = """subscription($input:DiagramEventInput!) {
 diagramEvent(input:$input) { __typename ... on DiagramRefreshedEventPayload { diagram {
  id nodes { NODE childNodes { NODE } borderNodes { NODE } }
  edges { id sourceId targetId centerLabel { text style { bold color italic } }
   beginLabel { text style { bold color } } endLabel { text style { bold color } }
   style { sourceArrow lineStyle } }
  layoutData { nodeLayoutData { id position { x y } size { width height } movedByUser resizedByUser }
   edgeLayoutData { id bendingPoints { x y } } }
 }} ... on ErrorPayload { messages { body } } }
}""".replace("NODE", node)
ws = websocket.create_connection(BASE.replace("http", "ws", 1) + "/subscriptions", subprotocols=["graphql-ws"], timeout=20)
try:
    ws.send(json.dumps({"type": "connection_init", "payload": {}}))
    assert json.loads(ws.recv())["type"] == "connection_ack"
    ws.send(json.dumps({"id": "example", "type": "start", "payload": {"query": query, "variables": {
        "input": {"id": str(uuid.uuid4()), "editingContextId": context, "diagramId": diagram_id}}}}))
    for _ in range(10):
        message = json.loads(ws.recv())
        if message["type"] == "data":
            assert "errors" not in message["payload"], message
            diagram = message["payload"]["data"]["diagramEvent"]["diagram"]
            break
        assert message["type"] != "error", message
    else:
        raise AssertionError("No saved example diagram")
    nodes = {n["insideLabel"]["text"]: n for n in diagram["nodes"]}
    assert set(nodes) == {"Set", "SetOfThing", "Source", "E", "Interface", "subPackage", "SomeDataType", "Enumeration", "hasNoSpecialChar"}, nodes.keys()
    assert len(diagram["edges"]) == 12, diagram["edges"]
    assert [n["insideLabel"]["text"] for n in nodes["Set"]["borderNodes"]] == ["T", "V"]
    assert [n["insideLabel"]["text"] for n in nodes["SetOfThing"]["borderNodes"]] == ["W"]
    assert not nodes["Set"]["childNodes"], nodes["Set"]
    assert [n["insideLabel"]["text"] for n in nodes["Source"]["childNodes"]] == ["required : EString", "optional : EString"]
    assert [n["insideLabel"]["style"]["bold"] for n in nodes["Source"]["childNodes"]] == [True, False]
    labels = {e["centerLabel"]["text"]: e for e in diagram["edges"] if e["centerLabel"]}
    assert {"<<bind>> C,E", "<<bind>> Thing,W", "[0..*] elements", "[0..1] optionalToE", "[1..1] requiredToE", "[0..1] /derivedToE", "[0..*] containedToE", "[0..*] /containedDerived"} <= labels.keys(), labels.keys()
    assert labels["[0..*] elements"]["targetId"] == nodes["Set"]["borderNodes"][0]["id"]
    assert labels["[1..1] requiredToE"]["centerLabel"]["style"]["bold"]
    assert labels["[0..1] /derivedToE"]["centerLabel"]["style"]["color"] == "#2d6f9f"
    opposite_labels = {(e["beginLabel"]["text"], e["endLabel"]["text"]) for e in diagram["edges"] if e["beginLabel"]}
    assert opposite_labels == {("[0..*] eMany", "[0..1] source"), ("[0..1] e", "[1..1] mandatoryE")}, opposite_labels
    for n in diagram["nodes"]:
        for item in [n, *n["childNodes"], *n["borderNodes"]]:
            assert item["insideLabel"]["style"]["color"] != "#c62828", item
    for edge in diagram["edges"]:
        for field in ("beginLabel", "centerLabel", "endLabel"):
            if edge[field]:
                assert edge[field]["style"]["color"] != "#c62828", edge
    bounds = next(n for n in diagram["layoutData"]["nodeLayoutData"] if n["id"] == nodes["Source"]["id"])
    assert bounds["position"] == {"x": 330, "y": 250}, bounds
    assert bounds["size"] == {"width": 240, "height": 280}, bounds
    assert bounds["movedByUser"] and bounds["resizedByUser"], bounds
    assert len(diagram["layoutData"]["edgeLayoutData"]) == len(diagram["edges"])
    print("PASS: Example template, .ecore document name and XML download, valid semantic styles, generic ports/bindings, references/opposites and saved diagram layout")
    print(BASE + "/projects/" + project["id"] + "/edit/" + diagram_id)
finally:
    ws.close()
