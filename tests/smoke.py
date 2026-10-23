# Copyright (c) 2026 Obeo.
# SPDX-License-Identifier: EPL-2.0

"""Exercise a running application through its public GraphQL API.
Usage: python3.13 tests/smoke.py http://localhost:8083
Requires requests and websocket-client. Creates a dedicated verification project.
"""
import json
import sys
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import quote
import requests
import websocket

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8083"


def graphql(query, **variables):
    response = requests.post(BASE + "/api/graphql", json={"query": query, "variables": variables}, timeout=20)
    response.raise_for_status()
    result = response.json()
    assert "errors" not in result, result
    return result["data"]


def uid():
    return str(uuid.uuid4())


project = graphql("""
mutation($input:CreateProjectInput!) {
 createProject(input:$input) { __typename
  ... on CreateProjectSuccessPayload { project { id currentEditingContext { id } } }
  ... on ErrorPayload { messages { body } }
 }
}""", input={"id": uid(), "name": "Ecore API verification", "templateId": "blank-project", "libraryIds": []})["createProject"]
assert project["__typename"] == "CreateProjectSuccessPayload", project
context = project["project"]["currentEditingContext"]["id"]

# Keep editing regressions independent of the contents of the user-facing Example template.
operations = {"query": """mutation($input:UploadDocumentInput!) {
 uploadDocument(input:$input) { __typename ... on ErrorPayload { messages { body } } }
}""", "variables": {"input": {"id": uid(), "editingContextId": context, "file": None, "readOnly": False}}}
with (Path(__file__).parent / "fixtures/library.ecore").open("rb") as fixture:
    response = requests.post(BASE + "/api/graphql/upload", data={"operations": json.dumps(operations),
                             "map": json.dumps({"0": "variables.file"})},
                             files={"0": ("library.ecore", fixture, "application/octet-stream")}, timeout=20)
response.raise_for_status()
assert response.json()["data"]["uploadDocument"]["__typename"] == "UploadDocumentSuccessPayload", response.text


def objects(expression):
    result = graphql("""
    mutation($input:EvaluateExpressionInput!) {
     evaluateExpression(input:$input) {
      ... on EvaluateExpressionSuccessPayload {
       result { ... on ObjectsExpressionResult { value { id label iconURLs } } }
      }
      ... on ErrorPayload { messages { body } }
     }
    }""", input={"id": uid(), "editingContextId": context, "expression": expression, "selectedObjectIds": []})
    return result["evaluateExpression"]["result"]["value"]


root = objects("aql:editingContext.allContents()->filter(ecore::EPackage)")[0]
types = {obj["label"]: obj for obj in objects("aql:editingContext.allContents()->filter(ecore::EClass)")}
for obj in [root, *types.values()]:
    for icon in obj["iconURLs"]:
        response = requests.get(BASE + icon, timeout=10)
        response.raise_for_status()
        assert ET.fromstring(response.content).tag == "{http://www.w3.org/2000/svg}svg"

descriptions = graphql("""
query($ctx:ID!, $obj:ID!) {
 viewer { editingContext(editingContextId:$ctx) {
  representationDescriptions(objectId:$obj) { edges { node { id label } } }
 }}
}""", ctx=context, obj=root["id"])["viewer"]["editingContext"]["representationDescriptions"]["edges"]
description = next(e["node"]["id"] for e in descriptions if e["node"]["label"] == "Ecore Class Diagram")
created = graphql("""
mutation($input:CreateRepresentationInput!) {
 createRepresentation(input:$input) { __typename
  ... on CreateRepresentationSuccessPayload { representation { id } }
  ... on ErrorPayload { messages { body } }
 }
}""", input={"id": uid(), "editingContextId": context, "objectId": root["id"],
             "representationDescriptionId": description, "representationName": "Library classes"})["createRepresentation"]
assert created["__typename"] == "CreateRepresentationSuccessPayload", created
diagram = created["representation"]["id"]
metadata = graphql("""query($ctx:ID!, $diagram:ID!) {
 viewer { editingContext(editingContextId:$ctx) {
  representations(representationIds:[$diagram]) { edges { node { iconURLs } } }
 } }
}""", ctx=context, diagram=diagram)["viewer"]["editingContext"]["representations"]["edges"][0]["node"]
assert len(metadata["iconURLs"]) == 1, metadata
assert metadata["iconURLs"][0].endswith("/icons/ecore/EcoreClassDiagram.svg"), metadata

ws = websocket.create_connection(BASE.replace("http", "ws", 1) + "/subscriptions",
                                 subprotocols=["graphql-ws"], timeout=20)
ws.send(json.dumps({"type": "connection_init", "payload": {}}))
assert json.loads(ws.recv())["type"] == "connection_ack"
node_fields = """id descriptionId targetObjectId defaultWidth defaultHeight insideLabel { id text insideLabelLocation style { color iconURL bold } } style { ... on RectangularNodeStyle { borderColor borderSize borderStyle background } }"""
subscription = """
subscription($input:DiagramEventInput!) {
 diagramEvent(input:$input) { __typename
  ... on DiagramRefreshedEventPayload { diagram {
   id nodes { NODE childNodes { NODE childNodes { NODE } } borderNodes { NODE } } edges {
    id descriptionId sourceId targetId targetObjectId centerLabel { id text style { bold color } }
    beginLabel { id text style { bold color } } endLabel { id text style { bold color } }
    style { sourceArrow targetArrow lineStyle }
   }
   layoutData { nodeLayoutData { id position { x y } size { width height } resizedByUser } }
  }}
  ... on ErrorPayload { messages { body } }
 }
}""".replace("NODE", node_fields)
ws.send(json.dumps({"id": "diagram", "type": "start", "payload": {
    "query": subscription, "variables": {"input": {"id": uid(), "editingContextId": context, "diagramId": diagram}}}}))


def refreshed(predicate=lambda _: True):
    for _ in range(30):
        message = json.loads(ws.recv())
        if message["type"] == "data":
            assert "errors" not in message["payload"], message
            payload = message["payload"]["data"]["diagramEvent"]
            assert payload["__typename"] == "DiagramRefreshedEventPayload", payload
            if predicate(payload["diagram"]):
                return payload["diagram"]
        elif message["type"] == "error":
            raise AssertionError(message)
    raise AssertionError("No matching diagram refresh")


def palette(element):
    result = graphql("""
    query($ctx:ID!, $diagram:ID!, $element:ID!) {
     viewer { editingContext(editingContextId:$ctx) {
      representation(representationId:$diagram) { description { ... on DiagramDescription {
       palette(diagramElementIds:[$element]) {
        quickAccessTools { id label ... on SingleClickOnDiagramElementTool { dialogDescriptionId } }
        paletteEntries {
        ... on SingleClickOnDiagramElementTool { id label dialogDescriptionId }
        ... on SingleClickOnTwoDiagramElementsTool { id label candidates { sources { id } targets { id } } }
        ... on ToolSection { tools { id label
         ... on SingleClickOnTwoDiagramElementsTool { candidates { sources { id } targets { id } } }
        } }
       }}
      }}}
     }}
    }""", ctx=context, diagram=diagram, element=element)
    return result["viewer"]["editingContext"]["representation"]["description"]["palette"]


def tools(element):
    available = palette(element)
    entries = available["paletteEntries"] + available["quickAccessTools"]
    return {tool["label"]: tool["id"] for entry in entries for tool in entry.get("tools", [entry]) if "label" in tool}


def invoke_edge(label, source, target, expect_error=False):
    tool = next(t for t in connector_tools(source, target) if t["label"] == label)
    assert any(source["descriptionId"] in {d["id"] for d in candidate["sources"]}
               and target["descriptionId"] in {d["id"] for d in candidate["targets"]}
               for candidate in tool["candidates"]), tool
    result = graphql("""mutation($input:InvokeSingleClickOnTwoDiagramElementsToolInput!) {
     invokeSingleClickOnTwoDiagramElementsTool(input:$input) { __typename
      ... on ErrorPayload { messages { body } }
      ... on InvokeSingleClickOnTwoDiagramElementsToolSuccessPayload { messages { body level } }
     }
    }""", input={"id": uid(), "editingContextId": context, "representationId": diagram,
                 "toolId": tool["id"], "diagramSourceElementId": source["id"], "diagramTargetElementId": target["id"],
                 "sourcePositionX": 0, "sourcePositionY": 0, "targetPositionX": 0, "targetPositionY": 0, "variables": []})
    result = result["invokeSingleClickOnTwoDiagramElementsTool"]
    if expect_error:
        assert result["__typename"] == "ErrorPayload", result
        return result
    assert result["__typename"] == "InvokeSingleClickOnTwoDiagramElementsToolSuccessPayload", result
    assert not any(m["level"] == "ERROR" for m in result["messages"]), result


def connector_tools(source, target):
    result = graphql("""query($ctx:ID!, $diagram:ID!, $source:ID!, $target:ID!) {
     viewer { editingContext(editingContextId:$ctx) {
      representation(representationId:$diagram) { description { ... on DiagramDescription {
       connectorPalette(sourceDiagramElementId:$source, targetDiagramElementId:$target) { paletteEntries {
        ... on SingleClickOnTwoDiagramElementsTool { id label candidates { sources { id } targets { id } } }
        ... on ToolSection { tools { id label
         ... on SingleClickOnTwoDiagramElementsTool { candidates { sources { id } targets { id } } }
        } }
       } }
      } } }
     } }
    }""", ctx=context, diagram=diagram, source=source["id"], target=target["id"])
    entries = result["viewer"]["editingContext"]["representation"]["description"]["connectorPalette"]["paletteEntries"]
    return [t for entry in entries for t in entry.get("tools", [entry]) if "label" in t]


def reconnect_edge(edge, target, kind="TARGET"):
    return graphql("""mutation($input:ReconnectEdgeInput!) {
     reconnectEdge(input:$input) { __typename ... on ErrorPayload { messages { body } } }
    }""", input={"id": uid(), "editingContextId": context, "representationId": diagram,
                 "edgeId": edge["id"], "newEdgeEndId": target["id"], "reconnectEdgeKind": kind,
                 "targetPositionX": 0, "targetPositionY": 0})["reconnectEdge"]


def note_text(note):
    result = graphql("""mutation($input:EvaluateExpressionInput!) {
     evaluateExpression(input:$input) { ... on EvaluateExpressionSuccessPayload {
      result { ... on StringExpressionResult { value } }
     } }
    }""", input={"id": uid(), "editingContextId": context, "expression": "aql:self.value",
                 "selectedObjectIds": [note["targetObjectId"]]})
    return result["evaluateExpression"]["result"]["value"]


def related_candidates(node):
    tool = next(t for t in palette(node["id"])["quickAccessTools"] if t["label"] == "Import related elements")
    selection = "selectionDialog://?representationDescription=" + quote(tool["dialogDescriptionId"], safe="")
    result = graphql("""
    query($ctx:ID!, $selection:ID!) {
     viewer { editingContext(editingContextId:$ctx) {
      representation(representationId:$selection) { description { ... on SelectionDescription {
       multiple treeDescription { id }
      }}}
     }}
    }""", ctx=context, selection=selection)
    description = result["viewer"]["editingContext"]["representation"]["description"]
    assert description["multiple"] is True, description
    tree_id = "selection://?treeDescriptionId=" + quote(description["treeDescription"]["id"], safe="")
    tree_id += "&targetObjectId=" + quote(node["targetObjectId"], safe="") + "&expandedIds=[]"
    tree_ws = websocket.create_connection(BASE.replace("http", "ws", 1) + "/subscriptions",
                                         subprotocols=["graphql-ws"], timeout=20)
    try:
        tree_ws.send(json.dumps({"type": "connection_init", "payload": {}}))
        assert json.loads(tree_ws.recv())["type"] == "connection_ack"
        query = """subscription($input:SelectionDialogTreeEventInput!) {
         selectionDialogTreeEvent(input:$input) { __typename
          ... on TreeRefreshedEventPayload { tree { children { id selectable } } }
          ... on ErrorPayload { messages { body } }
         }
        }"""
        tree_ws.send(json.dumps({"id": "related", "type": "start", "payload": {"query": query,
            "variables": {"input": {"id": uid(), "editingContextId": context, "representationId": tree_id}}}}))
        for _ in range(30):
            message = json.loads(tree_ws.recv())
            if message["type"] == "data":
                assert "errors" not in message["payload"], message
                payload = message["payload"]["data"]["selectionDialogTreeEvent"]
                assert payload["__typename"] == "TreeRefreshedEventPayload", payload
                return {item["id"] for item in payload["tree"]["children"] if item["selectable"]}
            assert message["type"] != "error", message
        raise AssertionError("No related-element dialog tree received")
    finally:
        tree_ws.close()


def invoke(label, element=None, selected=()):
    element = element or diagram
    result = graphql("""
    mutation($input:InvokeSingleClickOnDiagramElementToolInput!) {
     invokeSingleClickOnDiagramElementTool(input:$input) { __typename
      ... on ErrorPayload { messages { body } }
      ... on InvokeSingleClickOnDiagramElementToolSuccessPayload { messages { body level } }
     }
    }""", input={"id": uid(), "editingContextId": context, "representationId": diagram,
                 "toolId": tools(element)[label], "diagramElementIds": [element],
                 "startingPositionX": 100, "startingPositionY": 100,
                 "variables": [{"name": "selectedObjects", "type": "OBJECT_ID_ARRAY", "value": ",".join(selected)}] if selected else []})
    result = result["invokeSingleClickOnDiagramElementTool"]
    assert result["__typename"] == "InvokeSingleClickOnDiagramElementToolSuccessPayload", result
    assert not any(m["level"] == "ERROR" for m in result["messages"]), result


def edit(label_id, text):
    result = graphql("""
    mutation($input:EditLabelInput!) { editLabel(input:$input) { __typename
     ... on ErrorPayload { messages { body } }
     ... on EditLabelSuccessPayload { messages { body level } }
    }}
    """, input={"id": uid(), "editingContextId": context, "representationId": diagram,
                 "labelId": label_id, "newText": text})["editLabel"]
    assert result["__typename"] == "EditLabelSuccessPayload", result


def clear_reference_type(reference_id):
    form_id = "details://?objectIds=[" + reference_id + "]"
    form_ws = websocket.create_connection(BASE.replace("http", "ws", 1) + "/subscriptions",
                                         subprotocols=["graphql-ws"], timeout=20)
    try:
        form_ws.send(json.dumps({"type": "connection_init", "payload": {}}))
        assert json.loads(form_ws.recv())["type"] == "connection_ack"
        query = """subscription($input:FormEventInput!) {
         formEvent(input:$input) { __typename
          ... on FormRefreshedEventPayload { form { pages { groups { widgets {
           id label ... on ReferenceWidget { reference { referenceKind } }
          }}}}}
          ... on ErrorPayload { messages { body } }
         }
        }"""
        form_ws.send(json.dumps({"id": "properties", "type": "start", "payload": {"query": query,
            "variables": {"input": {"id": uid(), "editingContextId": context, "formId": form_id}}}}))
        for _ in range(30):
            message = json.loads(form_ws.recv())
            if message["type"] == "data":
                assert "errors" not in message["payload"], message
                payload = message["payload"]["data"]["formEvent"]
                assert payload["__typename"] != "ErrorPayload", payload
                if payload["__typename"] != "FormRefreshedEventPayload":
                    continue
                widgets = [w for p in payload["form"]["pages"] for g in p["groups"] for w in g["widgets"]]
                widget = next(w for w in widgets if "reference" in w and w["label"].replace(" ", "").lower() in ("etype", "type"))
                result = graphql("""
                mutation($input:ClearReferenceInput!) { clearReference(input:$input) { __typename
                 ... on ErrorPayload { messages { body } }
                }}""", input={"id": uid(), "editingContextId": context, "representationId": form_id,
                              "referenceWidgetId": widget["id"]})["clearReference"]
                assert result["__typename"] == "SuccessPayload", result
                return
            assert message["type"] != "error", message
        raise AssertionError("No EType properties widget received")
    finally:
        form_ws.close()


def check_icon(node, name):
    icons = node["insideLabel"]["style"]["iconURL"]
    assert icons == ["/api/images/icons/ecore/" + name + ".svg"], icons
    response = requests.get(BASE + icons[0], timeout=10)
    response.raise_for_status()
    assert ET.fromstring(response.content).tag == "{http://www.w3.org/2000/svg}svg"


try:
    assert refreshed()["nodes"] == [], "Classifiers must start unsynchronized"
    invoke("Import existing elements", selected=[types["Library"]["id"]])
    first = refreshed(lambda d: len(d["nodes"]) == 1)
    assert len(first["nodes"][0]["childNodes"]) == 1, first
    assert first["edges"] == []
    check_icon(first["nodes"][0], "EClass")
    check_icon(first["nodes"][0]["childNodes"][0], "EReference")
    assert first["nodes"][0]["childNodes"][0]["style"]["background"] == "transparent"
    reference_row = first["nodes"][0]["childNodes"][0]
    assert reference_row["insideLabel"]["style"]["bold"] is False
    for cardinality in ("1", "11"):
        edit(reference_row["insideLabel"]["id"], cardinality)
        refreshed(lambda d: d["nodes"][0]["childNodes"][0]["insideLabel"]["style"]["bold"])
    for text, mandatory, color in [("/books : Book", True, "#2d6f9f"), ("0", False, "#2d6f9f"),
                                   ("1", True, "#2d6f9f"), ("books : Book", True, "#566179")]:
        edit(reference_row["insideLabel"]["id"], text)
        changed = refreshed(lambda d: d["nodes"][0]["childNodes"][0]["insideLabel"]["style"]["color"] == color
                            and d["nodes"][0]["childNodes"][0]["insideLabel"]["style"]["bold"] == mandatory)
        assert changed["nodes"][0]["childNodes"][0]["insideLabel"]["text"].lstrip().startswith("/") == (color == "#2d6f9f"), changed
    assert related_candidates(first["nodes"][0]) == {types["Book"]["id"]}
    invoke("Import related elements", first["nodes"][0]["id"], selected=[types["Book"]["id"]])
    both = refreshed(lambda d: len(d["nodes"]) == 2)
    library = next(n for n in both["nodes"] if n["targetObjectId"] == types["Library"]["id"])
    book = next(n for n in both["nodes"] if n["targetObjectId"] == types["Book"]["id"])
    assert library["childNodes"] == [], both
    assert len(both["edges"]) == 1, both
    reference_label = both["edges"][0]["centerLabel"]["id"]
    assert both["edges"][0]["centerLabel"]["style"]["bold"] is True
    for text, mandatory, color in [("0", False, "#63708a"), ("11", True, "#63708a"),
                                   ("[1..*] invalid name", True, "#c62828"), ("[1..*] books", True, "#63708a"),
                                   ("[0..*] /books", False, "#2d6f9f"), ("[1..*] /books", True, "#2d6f9f"),
                                   ("[1..*] /invalid name", True, "#c62828"), ("[1..*] /books", True, "#2d6f9f"),
                                   ("[1..*] books", True, "#63708a")]:
        edit(reference_label, text)
        changed = refreshed(lambda d: d["edges"][0]["centerLabel"]["style"] == {"bold": mandatory, "color": color})
        assert changed["edges"][0]["style"]["sourceArrow"] == "FillDiamond", changed
    reference_id = both["edges"][0]["targetObjectId"]
    clear_reference_type(reference_id)
    untyped = refreshed(lambda d: not d["edges"] and len(next(n for n in d["nodes"] if n["id"] == library["id"])["childNodes"]) == 1)
    untyped_row = next(n for n in untyped["nodes"] if n["id"] == library["id"])["childNodes"][0]
    assert untyped_row["targetObjectId"] == reference_id, untyped
    assert untyped_row["insideLabel"]["text"].strip() == "books", untyped_row
    assert untyped_row["insideLabel"]["style"]["color"] == "#c62828", untyped_row
    edit(untyped_row["insideLabel"]["id"], "renamedBooks")
    renamed = refreshed(lambda d: next(n for n in d["nodes"] if n["id"] == library["id"])["childNodes"][0]["insideLabel"]["text"].strip() == "renamedBooks")
    assert not renamed["edges"], renamed
    edit(untyped_row["insideLabel"]["id"], "books : Book")
    retyped = refreshed(lambda d: len(d["edges"]) == 1 and not next(n for n in d["nodes"] if n["id"] == library["id"])["childNodes"])
    assert retyped["edges"][0]["targetObjectId"] == reference_id, retyped
    assert len(book["childNodes"]) == 1, both
    check_icon(book, "EClass")
    check_icon(book["childNodes"][0], "EAttribute")
    invoke("Remove from diagram", book["id"])
    remaining = refreshed(lambda d: len(d["nodes"]) == 1)
    assert len(remaining["nodes"][0]["childNodes"]) == 1, remaining
    assert len(objects("aql:editingContext.allContents()->filter(ecore::EClass)")) == 2
    invoke("Import existing elements", selected=[types["Book"]["id"]])
    restored = refreshed(lambda d: len(d["nodes"]) == 2)
    book = next(n for n in restored["nodes"] if n["targetObjectId"] == types["Book"]["id"])
    attribute = book["childNodes"][0]
    assert attribute["insideLabel"]["style"]["bold"] is False
    edit(attribute["insideLabel"]["id"], "1")
    refreshed(lambda d: next(n for n in d["nodes"] if n["id"] == book["id"])["childNodes"][0]["insideLabel"]["style"]["bold"])
    for text, expected in [("invalid name", "#c62828"), ("/title", "#2d6f9f"),
                           ("/invalid name", "#c62828"), ("title", "#566179")]:
        edit(attribute["insideLabel"]["id"], text)
        parent_color = "#c62828" if expected == "#c62828" else "#788398"
        updated = refreshed(lambda d: next(n for n in d["nodes"] if n["id"] == book["id"])["style"]["borderColor"] == parent_color)
        current_book = next(n for n in updated["nodes"] if n["id"] == book["id"])
        assert current_book["insideLabel"]["style"]["color"] == ("#c62828" if expected == "#c62828" else "#1e2644")
        assert current_book["childNodes"][0]["insideLabel"]["style"]["color"] == expected
        assert current_book["childNodes"][0]["insideLabel"]["text"].startswith("/") == text.startswith("/"), current_book
        assert current_book["childNodes"][0]["insideLabel"]["style"]["bold"] is True
        check_icon(current_book, "EClass")
        check_icon(current_book["childNodes"][0], "EAttribute")
        assert current_book["childNodes"][0]["style"]["background"] == "transparent"
    edit(attribute["insideLabel"]["id"], "0")
    refreshed(lambda d: not next(n for n in d["nodes"] if n["id"] == book["id"])["childNodes"][0]["insideLabel"]["style"]["bold"])
    invoke("New EClass")
    assert len(refreshed(lambda d: len(d["nodes"]) == 3)["nodes"]) == 3
    invoke("Documentation", book["id"])
    annotated = refreshed(lambda d: len(d["nodes"]) == 4)
    note = next(n for n in annotated["nodes"] if n["insideLabel"]["text"] == "New documentation note")
    assert note["style"]["borderSize"] == 1, note
    assert note["style"]["borderColor"] == "#788398", note
    assert note["insideLabel"]["style"]["color"] == "#566179", note
    edit(note["insideLabel"]["id"], "A library book\nWith its title")
    refreshed(lambda d: any(n["insideLabel"]["text"] == "A library book\nWith its title" for n in d["nodes"]))
    invoke("New EOperation", book["id"])
    with_operation = refreshed(lambda d: len(next(n for n in d["nodes"] if n["id"] == book["id"])["childNodes"]) == 2)
    operation = next(n for n in next(n for n in with_operation["nodes"] if n["id"] == book["id"])["childNodes"] if n["id"] != attribute["id"])
    check_icon(operation, "EOperation")
    assert operation["style"]["background"] == "transparent"
    edit(operation["insideLabel"]["id"], "find(query : EString) : Book")
    refreshed(lambda d: any(c["insideLabel"]["text"] == "find(query EString) : Book" for n in d["nodes"] for c in n["childNodes"]))
    invoke("New EDataType")
    with_datatype = refreshed(lambda d: len(d["nodes"]) == 5)
    datatype = next(n for n in with_datatype["nodes"] if n["insideLabel"]["text"] == "NewDataType")
    check_icon(datatype, "EDataType")
    assert datatype["childNodes"][0]["style"]["background"] == "transparent"
    edit(datatype["childNodes"][0]["insideLabel"]["id"], "java.lang.String")
    refreshed(lambda d: any(c["insideLabel"]["text"] == "java.lang.String" for n in d["nodes"] for c in n["childNodes"]))
    invoke("New EEnum")
    with_enum = refreshed(lambda d: len(d["nodes"]) == 6)
    enumeration = next(n for n in with_enum["nodes"] if n["insideLabel"]["text"] == "NewEnum")
    for count in (1, 2):
        invoke("New EEnumLiteral", enumeration["id"])
        updated = refreshed(lambda d: len(next(n for n in d["nodes"] if n["id"] == enumeration["id"])["childNodes"]) == count)
    enumeration = next(n for n in updated["nodes"] if n["id"] == enumeration["id"])
    assert enumeration["style"]["background"] == "var(--ecore-content-background, #f9f9fc)", enumeration
    assert all(n["style"]["background"] == "transparent" for n in enumeration["childNodes"]), enumeration
    result = graphql("""
    mutation($input:LayoutDiagramInput!) { layoutDiagram(input:$input) { __typename
     ... on ErrorPayload { messages { body } }
    }}
    """, input={"id": uid(), "editingContextId": context, "representationId": diagram, "cause": "layout",
                 "diagramLayoutData": {"nodeLayoutData": [{"id": enumeration["id"],
                     "position": {"x": 100, "y": 100}, "size": {"width": 400, "height": 280},
                     "resizedByUser": True, "movedByUser": False, "handleLayoutData": [],
                     "minComputedSize": {"width": 220, "height": 70}}],
                     "edgeLayoutData": [], "labelLayoutData": [], "autoLayoutState": "UNCHANGED"}})["layoutDiagram"]
    assert result["__typename"] == "SuccessPayload", result
    resized = refreshed(lambda d: any(n["id"] == enumeration["id"] and n["resizedByUser"]
                                    for n in d["layoutData"]["nodeLayoutData"]))
    bounds = next(n for n in resized["layoutData"]["nodeLayoutData"] if n["id"] == enumeration["id"])
    assert bounds["size"] == {"width": 400, "height": 280}, bounds
    enumeration = next(n for n in resized["nodes"] if n["id"] == enumeration["id"])
    assert enumeration["style"]["background"] == "var(--ecore-content-background, #f9f9fc)", enumeration
    assert all(n["style"]["background"] == "transparent" for n in enumeration["childNodes"]), enumeration
    invoke("Constraints", book["id"])
    with_constraints = refreshed(lambda d: len(d["nodes"]) == 7)
    constraint = next(n for n in with_constraints["nodes"] if n["insideLabel"]["text"] == "Constraint1 Constraint2")
    assert constraint["style"]["background"] == "#eef4f9", constraint
    assert constraint["style"]["borderSize"] == 1, constraint
    assert constraint["style"]["borderColor"] == "#2d6f9f", constraint
    assert constraint["insideLabel"]["style"]["color"] == "#2d6f9f", constraint
    comment = next(n for n in with_constraints["nodes"] if n["id"] == note["id"])
    assert comment["style"]["background"] != constraint["style"]["background"], with_constraints
    edit(constraint["insideLabel"]["id"], "MustHaveTitle")
    updated = refreshed(lambda d: any(n["insideLabel"]["text"] == "MustHaveTitle" for n in d["nodes"]))
    assert next(n for n in updated["nodes"] if n["id"] == constraint["id"])["style"] == constraint["style"], updated
    invoke("New ETypeParameter", book["id"])
    with_parameter = refreshed(lambda d: len(next(n for n in d["nodes"] if n["id"] == book["id"])["borderNodes"]) == 1)
    parameter = next(n for n in with_parameter["nodes"] if n["id"] == book["id"])["borderNodes"][0]
    assert parameter["insideLabel"]["text"] == "T", parameter
    assert parameter["insideLabel"]["style"]["iconURL"] == [], parameter
    assert parameter["insideLabel"]["insideLabelLocation"] == "MIDDLE_CENTER", parameter
    assert (parameter["defaultWidth"], parameter["defaultHeight"]) == (32, 32), parameter
    assert parameter["style"] == {"borderColor": "#788398", "borderSize": 1, "borderStyle": "Dash",
                                  "background": "#ffffff"}, parameter
    for text, color in [("1", "#c62828"), ("T", "#566179")]:
        edit(parameter["insideLabel"]["id"], text)
        changed = refreshed(lambda d: next(n for n in d["nodes"] if n["id"] == book["id"])["borderNodes"][0]["insideLabel"]["style"]["color"] == color)
        port = next(n for n in changed["nodes"] if n["id"] == book["id"])["borderNodes"][0]
        assert port["insideLabel"]["style"]["iconURL"] == [], port
        assert port["style"]["background"] == parameter["style"]["background"], port
        assert port["style"]["borderStyle"] == "Dash", port
    edit(attribute["insideLabel"]["id"], "title : NewDataType")
    refreshed(lambda d: any(c["insideLabel"]["text"] == "title : NewDataType" for n in d["nodes"] for c in n["childNodes"]))
    invoke("Remove from diagram", book["id"])
    without_book = refreshed(lambda d: not any(n["targetObjectId"] == types["Book"]["id"] for n in d["nodes"]))
    assert related_candidates(datatype) == {types["Book"]["id"]}
    invoke("Import related elements", datatype["id"], selected=[types["Book"]["id"]])
    imported = refreshed(lambda d: any(n["targetObjectId"] == types["Book"]["id"] for n in d["nodes"]))
    restored_book = next(n for n in imported["nodes"] if n["targetObjectId"] == types["Book"]["id"])
    assert len(imported["nodes"]) == len(without_book["nodes"]) + 3, imported
    assert all(c["targetObjectId"] != types["Book"]["id"] for n in imported["nodes"] for c in n["childNodes"]), imported
    assert objects("aql:editingContext.allContents()->filter(ecore::EClass)->select(c | c.name = 'Book')")[0]["id"] == types["Book"]["id"]
    invoke("New EAttribute", restored_book["id"])
    with_status = refreshed(lambda d: len(next(n for n in d["nodes"] if n["id"] == restored_book["id"])["childNodes"]) == 3)
    status = next(c for n in with_status["nodes"] if n["id"] == restored_book["id"]
                  for c in n["childNodes"] if c["insideLabel"]["text"].startswith("NewAttribute"))
    edit(status["insideLabel"]["id"], "status : NewEnum")
    refreshed(lambda d: any(c["insideLabel"]["text"].startswith("status : NewEnum") for n in d["nodes"] for c in n["childNodes"]))
    for node in (datatype, enumeration):
        invoke("Remove from diagram", node["id"])
        refreshed(lambda d: not any(n["id"] == node["id"] for n in d["nodes"]))
    selected = {datatype["targetObjectId"], enumeration["targetObjectId"]}
    assert selected <= related_candidates(restored_book)
    invoke("Import related elements", restored_book["id"], selected=sorted(selected))
    multiple = refreshed(lambda d: selected <= {n["targetObjectId"] for n in d["nodes"]})
    assert all(sum(n["targetObjectId"] == target for n in multiple["nodes"]) == 1 for target in selected), multiple
    assert all(c["targetObjectId"] not in selected for n in multiple["nodes"] if n["id"] == restored_book["id"]
               for c in n["childNodes"]), multiple
    reference = next(e for e in multiple["edges"] if e["targetObjectId"] == reference_id)
    invoke("Create opposite", reference["id"])
    with_opposite = refreshed(lambda d: any(e["targetObjectId"] == reference_id and e["beginLabel"] for e in d["edges"]))
    opposite = next(e for e in with_opposite["edges"] if e["targetObjectId"] == reference_id)
    assert sum(e["targetObjectId"] == reference_id for e in with_opposite["edges"]) == 1, with_opposite
    assert opposite["centerLabel"] is None, opposite
    assert opposite["beginLabel"]["text"] == "[0..1] opposite", opposite
    assert opposite["endLabel"]["text"] == "[1..*] books", opposite
    assert opposite["beginLabel"]["style"] == {"bold": False, "color": "#63708a"}, opposite
    # Earlier row edits left books transient, so EMF flags its non-transient opposite.
    assert opposite["endLabel"]["style"] == {"bold": True, "color": "#c62828"}, opposite
    for text, bold, color in [("[0..1] /owner", False, "#2d6f9f"),
                              ("[1..1] /invalid name", True, "#c62828"),
                              ("[0..1] owner", False, "#63708a")]:
        edit(opposite["beginLabel"]["id"], text)
        changed = refreshed()
        edge = next(e for e in changed["edges"] if e["targetObjectId"] == reference_id)
        assert edge["beginLabel"]["text"] == text, edge
        assert edge["beginLabel"]["style"] == {"bold": bold, "color": color}, edge
        assert edge["endLabel"]["text"] == "[1..*] books", edge
        assert edge["endLabel"]["style"] == {"bold": True, "color": "#c62828"}, edge
    edit(opposite["endLabel"]["id"], "[1..*] /volumes")
    changed = refreshed(lambda d: any(e["targetObjectId"] == reference_id and e["endLabel"]["text"] == "[1..*] /volumes" for e in d["edges"]))
    edge = next(e for e in changed["edges"] if e["targetObjectId"] == reference_id)
    assert edge["endLabel"]["style"] == {"bold": True, "color": "#c62828"}, edge
    assert edge["beginLabel"]["text"] == "[0..1] owner", edge
    assert edge["beginLabel"]["style"] == {"bold": False, "color": "#63708a"}, edge
    source = next(n for n in changed["nodes"] if n["targetObjectId"] == types["Book"]["id"])
    target = source["borderNodes"][0]
    assert {t["label"] for t in connector_tools(source, target)} == {"Reference", "Composition"}
    assert "Supertype" in {t["label"] for t in connector_tools(source, library)}
    for label, cardinality, arrow, name in [("Reference", "[0..1]", "None", "optionalT"),
                                           ("Composition", "[0..*]", "FillDiamond", "containedT")]:
        invoke_edge(label, source, target)
        linked = refreshed(lambda d: any(e["targetId"] == target["id"] and e["centerLabel"]["text"] == cardinality + " t" for e in d["edges"]))
        edge = next(e for e in linked["edges"] if e["targetId"] == target["id"] and e["centerLabel"]["text"] == cardinality + " t")
        assert edge["sourceId"] == source["id"], edge
        assert edge["style"]["sourceArrow"] == arrow, edge
        assert edge["centerLabel"]["style"]["color"] == "#63708a", edge
        assert "Create opposite" not in tools(edge["id"]), edge
        book_node = next(n for n in linked["nodes"] if n["id"] == source["id"])
        assert all(n["targetObjectId"] != edge["targetObjectId"] for n in book_node["childNodes"]), book_node
        semantic = objects("aql:editingContext.allContents()->filter(ecore::EReference)->select(r | r.name = 't' and r.eGenericType.eTypeParameter.name = 'T')")
        assert [r["id"] for r in semantic] == [edge["targetObjectId"]], semantic
        edit(edge["centerLabel"]["id"], cardinality + " " + name)
        changed = refreshed(lambda d: any(e["targetId"] == target["id"] and e["centerLabel"]["text"] == cardinality + " " + name for e in d["edges"]))
    invoke("Documentation")
    documented = refreshed(lambda d: any(n["insideLabel"]["text"] == "New documentation note" for n in d["nodes"]))
    background_note = next(n for n in documented["nodes"] if n["insideLabel"]["text"] == "New documentation note")
    assert not any(e["sourceId"] == background_note["id"] for e in documented["edges"]), documented
    text = "Diagram documentation\nKeep this text when reconnecting"
    edit(background_note["insideLabel"]["id"], text)
    refreshed(lambda d: any(n["id"] == background_note["id"] and n["insideLabel"]["text"] == text for n in d["nodes"]))
    refused = invoke_edge("Assign annotation", background_note, source, expect_error=True)
    assert any("already has documentation" in m["body"] for m in refused["messages"]), refused
    assert note_text(background_note) == text
    assert note_text(note) == "A library book\nWith its title"
    semantic = objects("aql:editingContext.allContents()->filter(ecore::EPackage).eAnnotations.details")
    assert background_note["targetObjectId"] in {n["id"] for n in semantic}, semantic
    existing_documentation = objects("aql:editingContext.allContents()->filter(ecore::EClass)->select(c | c.name = 'Book').eAnnotations.details")
    assert note["targetObjectId"] in {n["id"] for n in existing_documentation}, existing_documentation
    invoke_edge("Assign annotation", background_note, library)
    assigned = refreshed(lambda d: any(e["sourceId"] == background_note["id"] and e["targetId"] == library["id"] for e in d["edges"]))
    assignment = next(e for e in assigned["edges"] if e["sourceId"] == background_note["id"])
    semantic = objects("aql:editingContext.allContents()->filter(ecore::EClass)->select(c | c.name = 'Library').eAnnotations.details")
    assert background_note["targetObjectId"] in {n["id"] for n in semantic}, semantic
    assert reconnect_edge(assignment, datatype)["__typename"] == "SuccessPayload"
    reassigned = refreshed(lambda d: any(e["sourceId"] == background_note["id"] and e["targetId"] == datatype["id"] for e in d["edges"]))
    assignment = next(e for e in reassigned["edges"] if e["sourceId"] == background_note["id"])
    reference_edge = next(e for e in reassigned["edges"] if e["targetObjectId"] == reference_id)
    assert reconnect_edge(assignment, reference_edge)["__typename"] == "SuccessPayload"
    reference_documented = refreshed(lambda d: any(e["sourceId"] == background_note["id"] and e["targetId"] == reference_edge["id"] for e in d["edges"]))
    preserved = next(n for n in reference_documented["nodes"] if n["id"] == background_note["id"])
    assert preserved["targetObjectId"] == background_note["targetObjectId"], preserved
    assert preserved["insideLabel"]["text"] == text, preserved
    assignment = next(e for e in reference_documented["edges"] if e["sourceId"] == background_note["id"])
    refused = reconnect_edge(assignment, source)
    assert refused["__typename"] == "ErrorPayload", refused
    assert any("already has documentation" in m["body"] for m in refused["messages"]), refused
    assert note_text(background_note) == text
    assert note_text(note) == "A library book\nWith its title"
    assert objects("aql:editingContext.allContents()->filter(ecore::EClass)->select(c | c.name = 'Book').eAnnotations.details") == existing_documentation
    semantic = objects("aql:editingContext.allContents()->filter(ecore::EReference)->select(r | r.name = 'volumes').eAnnotations.details")
    assert background_note["targetObjectId"] in {n["id"] for n in semantic}, semantic
    invoke("Documentation")
    restored_background = refreshed(lambda d: any(n["insideLabel"]["text"] == "New documentation note" for n in d["nodes"]))
    new_note = next(n for n in restored_background["nodes"] if n["insideLabel"]["text"] == "New documentation note")
    assert new_note["targetObjectId"] != background_note["targetObjectId"], new_note
    current = restored_background
    new_classes = []
    for name in ("ReconnectA", "ReconnectB"):
        previous_ids = {n["id"] for n in current["nodes"]}
        invoke("New EClass")
        current = refreshed(lambda d: any(n["id"] not in previous_ids for n in d["nodes"]))
        node = next(n for n in current["nodes"] if n["id"] not in previous_ids)
        edit(node["insideLabel"]["id"], name)
        current = refreshed(lambda d: any(n["insideLabel"]["text"] == name for n in d["nodes"]))
        new_classes.append(next(n for n in current["nodes"] if n["id"] == node["id"]))
    reconnect_a, reconnect_b = new_classes
    for name in ("optionalT", "containedT"):
        edge = next(e for e in current["edges"] if e["centerLabel"] and e["centerLabel"]["text"].endswith(" " + name))
        semantic_id = edge["targetObjectId"]
        expected_source, expected_target = source["id"], target["id"]
        for kind, destination in [("TARGET", library), ("SOURCE", reconnect_a), ("SOURCE", source), ("TARGET", target)]:
            result = reconnect_edge(edge, destination, kind)
            assert result["__typename"] == "SuccessPayload", result
            if kind == "SOURCE":
                expected_source = destination["id"]
            else:
                expected_target = destination["id"]
            current = refreshed(lambda d: any(e["targetObjectId"] == semantic_id and e["sourceId"] == expected_source
                                             and e["targetId"] == expected_target for e in d["edges"]))
            edge = next(e for e in current["edges"] if e["targetObjectId"] == semantic_id)
            assert edge["centerLabel"]["text"].endswith(" " + name), edge
            assert edge["style"]["sourceArrow"] == ("FillDiamond" if name == "containedT" else "None"), edge
        semantic = objects("aql:editingContext.allContents()->filter(ecore::EReference)->select(r | r.name = '" + name
                           + "' and r.eContainingClass.name = 'Book' and r.eGenericType.eTypeParameter.name = 'T')")
        assert [r["id"] for r in semantic] == [semantic_id], semantic
    edge = next(e for e in current["edges"] if e["targetObjectId"] == reference_id)
    opposite_id = objects("aql:editingContext.allContents()->filter(ecore::EReference)->select(r | r.name = 'volumes').eOpposite")[0]["id"]
    expected_source, expected_target = library["id"], source["id"]
    for kind, destination in [("TARGET", reconnect_a), ("SOURCE", reconnect_b), ("TARGET", source), ("SOURCE", library)]:
        result = reconnect_edge(edge, destination, kind)
        assert result["__typename"] == "SuccessPayload", result
        if kind == "SOURCE":
            expected_source = destination["id"]
        else:
            expected_target = destination["id"]
        current = refreshed(lambda d: any(e["targetObjectId"] == reference_id and e["sourceId"] == expected_source
                                         and e["targetId"] == expected_target for e in d["edges"]))
        edge = next(e for e in current["edges"] if e["targetObjectId"] == reference_id)
        assert edge["beginLabel"] and edge["endLabel"], edge
        assert objects("aql:editingContext.allContents()->filter(ecore::EReference)->select(r | r.name = 'volumes').eOpposite")[0]["id"] == opposite_id
        assert objects("aql:editingContext.allContents()->filter(ecore::EReference)->select(r | r.name = 'volumes').eOpposite.eContainingClass")[0]["id"] == next(
            n["targetObjectId"] for n in current["nodes"] if n["id"] == expected_target)
        assert objects("aql:editingContext.allContents()->filter(ecore::EReference)->select(r | r.name = 'volumes').eOpposite.eType")[0]["id"] == next(
            n["targetObjectId"] for n in current["nodes"] if n["id"] == expected_source)
    refused = reconnect_edge(edge, target)
    assert refused["__typename"] == "ErrorPayload" and any("opposite" in m["body"] for m in refused["messages"]), refused
    assert note_text(background_note) == text
    invoke_edge("Supertype", reconnect_a, reconnect_b)
    current = refreshed(lambda d: any(e["sourceId"] == reconnect_a["id"] and e["targetId"] == reconnect_b["id"] for e in d["edges"]))
    inheritance = next(e for e in current["edges"] if e["sourceId"] == reconnect_a["id"] and e["targetId"] == reconnect_b["id"])
    assert reconnect_edge(inheritance, source, "SOURCE")["__typename"] == "SuccessPayload"
    current = refreshed(lambda d: any(e["sourceId"] == source["id"] and e["targetId"] == reconnect_b["id"] for e in d["edges"]))
    inheritance = next(e for e in current["edges"] if e["sourceId"] == source["id"] and e["targetId"] == reconnect_b["id"])
    assert reconnect_edge(inheritance, library)["__typename"] == "SuccessPayload"
    current = refreshed(lambda d: any(e["sourceId"] == source["id"] and e["targetId"] == library["id"] for e in d["edges"]))
    inheritance = next(e for e in current["edges"] if e["sourceId"] == source["id"] and e["targetId"] == library["id"])
    for destination, kind, message in [(source, "TARGET", "cycle"), (library, "SOURCE", "cycle"), (target, "TARGET", "EClasses")]:
        refused = reconnect_edge(inheritance, destination, kind)
        assert refused["__typename"] == "ErrorPayload" and any(message in m["body"] for m in refused["messages"]), refused
    assert objects("aql:editingContext.allContents()->filter(ecore::EClass)->select(c | c.name = 'Book').eSuperTypes")[0]["id"] == library["targetObjectId"]
    assert [c["id"] for c in objects("aql:editingContext.allContents()->filter(ecore::EClass)->select(c | c.name = 'ReconnectA' and c.eSuperTypes->isEmpty())")] == [reconnect_a["targetObjectId"]]
    for name in ("T", "U"):
        previous_ports = {p["id"] for n in current["nodes"] for p in n["borderNodes"]}
        invoke("New ETypeParameter", reconnect_b["id"])
        current = refreshed(lambda d: any(p["id"] not in previous_ports for n in d["nodes"] for p in n["borderNodes"]))
        port = next(p for n in current["nodes"] for p in n["borderNodes"] if p["id"] not in previous_ports)
        if name != "T":
            edit(port["insideLabel"]["id"], name)
            current = refreshed(lambda d: any(p["insideLabel"]["text"] == name for n in d["nodes"] if n["id"] == reconnect_b["id"] for p in n["borderNodes"]))
    invoke_edge("Supertype", reconnect_a, reconnect_b)
    current = refreshed(lambda d: any(e["sourceId"] == reconnect_a["id"] and e["targetId"] == reconnect_b["id"] for e in d["edges"]))
    generic_inheritance = next(e for e in current["edges"] if e["sourceId"] == reconnect_a["id"] and e["targetId"] == reconnect_b["id"])
    assert generic_inheritance["centerLabel"]["text"] == "<<bind>> ?,?", generic_inheritance
    assert generic_inheritance["style"]["lineStyle"] == "Solid", generic_inheritance
    arguments = objects("aql:editingContext.allContents()->filter(ecore::EClass)->select(c | c.name = 'ReconnectA').eGenericSuperTypes.eTypeArguments")
    assert len(arguments) == 2, arguments
    invoke_edge("Supertype", reconnect_b, source)
    current = refreshed(lambda d: any(e["sourceId"] == reconnect_b["id"] and e["targetId"] == source["id"] for e in d["edges"]))
    invoke("Remove from diagram", reconnect_b["id"])
    current = refreshed(lambda d: all(n["id"] != reconnect_b["id"] for n in d["nodes"]))
    projected = next((e for e in current["edges"] if e["sourceId"] == reconnect_a["id"] and e["targetId"] == source["id"]), None)
    assert projected is not None, {"nodes": [(n["id"], n["insideLabel"]["text"]) for n in current["nodes"]],
                                   "edges": [(e["sourceId"], e["targetId"], e["descriptionId"]) for e in current["edges"]]}
    assert projected["style"]["lineStyle"] == "Dot", projected
    invoke("Import existing elements", selected=[reconnect_b["targetObjectId"]])
    current = refreshed(lambda d: any(e["sourceId"] == reconnect_a["id"] and e["targetId"] == reconnect_b["id"] for e in d["edges"]))
    assert next(e for e in current["edges"] if e["sourceId"] == reconnect_a["id"] and e["targetId"] == reconnect_b["id"])["style"]["lineStyle"] == "Solid"
    assert objects("aql:editingContext.allContents()->filter(ecore::EClass)->select(c | c.name = 'ReconnectA').eGenericSuperTypes.eTypeArguments") == arguments
    reference_ids_before = {r["id"] for r in objects("aql:editingContext.allContents()->filter(ecore::EReference)")}
    for as_row in (False, True):
        invoke_edge("Bidirectional reference", reconnect_a, reconnect_b)
        pair_ids = {r["id"] for r in objects("aql:editingContext.allContents()->filter(ecore::EReference)")} - reference_ids_before
        assert len(pair_ids) == 2, pair_ids
        current = refreshed(lambda d: any(e["targetObjectId"] in pair_ids for e in d["edges"]))
        rendered = [e for e in current["edges"] if e["targetObjectId"] in pair_ids]
        assert len(rendered) == 1, rendered
        paired_edge = rendered[0]
        assert paired_edge["beginLabel"] and paired_edge["endLabel"], paired_edge
        assert paired_edge["style"]["sourceArrow"] == paired_edge["style"]["targetArrow"] == "InputArrow", paired_edge
        element_id = paired_edge["id"]
        if as_row:
            invoke("Remove from diagram", reconnect_b["id"])
            current = refreshed(lambda d: all(n["id"] != reconnect_b["id"] for n in d["nodes"]))
            row = next(c for n in current["nodes"] if n["id"] == reconnect_a["id"] for c in n["childNodes"] if c["targetObjectId"] in pair_ids)
            element_id = row["id"]
        invoke("Delete from model", element_id)
        current = refreshed(lambda d: not any(e["targetObjectId"] in pair_ids for e in d["edges"])
                            and not any(c["targetObjectId"] in pair_ids for n in d["nodes"] for c in n["childNodes"]))
        assert {r["id"] for r in objects("aql:editingContext.allContents()->filter(ecore::EReference)")} == reference_ids_before
    invoke("New EPackage")
    current = refreshed(lambda d: any(n["insideLabel"]["text"] == "NewPackage" for n in d["nodes"]))
    package = next(n for n in current["nodes"] if n["insideLabel"]["text"] == "NewPackage")
    assert package["defaultWidth"] == 360 and package["defaultHeight"] == 240, package
    edit(package["insideLabel"]["id"], "nested")
    current = refreshed(lambda d: any(n["id"] == package["id"] and n["insideLabel"]["text"] == "nested" for n in d["nodes"]))
    children = {}
    for kind in ("EClass", "EDataType", "EEnum", "EPackage"):
        invoke("New " + kind, package["id"])
        current = refreshed(lambda d: len(next(n for n in d["nodes"] if n["id"] == package["id"])["childNodes"]) == len(children) + 1)
        parent = next(n for n in current["nodes"] if n["id"] == package["id"])
        child = next(n for n in parent["childNodes"] if n["insideLabel"]["text"] == "New" + kind[1:])
        children[kind] = child
        assert child["targetObjectId"] in {o["id"] for o in objects(
            "aql:editingContext.allContents()->filter(ecore::EPackage)->select(p | p.name = 'nested').eContents()")}, child
        assert parent["style"]["background"] == "var(--ecore-content-background, #f9f9fc)", parent
    invoke("New EAttribute", children["EClass"]["id"])
    current = refreshed(lambda d: any(c["childNodes"] for n in d["nodes"] if n["id"] == package["id"]
                                     for c in n["childNodes"] if c["id"] == children["EClass"]["id"]))
    invoke("New EClass", children["EPackage"]["id"])
    current = refreshed(lambda d: any(c["childNodes"] for n in d["nodes"] if n["id"] == package["id"]
                                     for c in n["childNodes"] if c["id"] == children["EPackage"]["id"]))
    # Reimporting keeps the semantic owner and restores the child inside its package.
    invoke("Remove from diagram", children["EDataType"]["id"])
    current = refreshed(lambda d: len(next(n for n in d["nodes"] if n["id"] == package["id"])["childNodes"]) == 3)
    invoke("Import existing elements", package["id"], selected=[children["EDataType"]["targetObjectId"]])
    current = refreshed(lambda d: len(next(n for n in d["nodes"] if n["id"] == package["id"])["childNodes"]) == 4)
    parent = next(n for n in current["nodes"] if n["id"] == package["id"])
    children["EDataType"] = next(n for n in parent["childNodes"] if n["targetObjectId"] == children["EDataType"]["targetObjectId"])
    positions = {"EClass": (30, 70), "EDataType": (260, 70), "EEnum": (30, 200), "EPackage": (260, 200)}
    result = graphql("""mutation($input:LayoutDiagramInput!) { layoutDiagram(input:$input) { __typename
     ... on ErrorPayload { messages { body } }
    }}""", input={"id": uid(), "editingContextId": context, "representationId": diagram, "cause": "layout",
                  "diagramLayoutData": {"nodeLayoutData": [{"id": child["id"],
                      "position": {"x": positions[kind][0], "y": positions[kind][1]},
                      "size": {"width": 200, "height": 110}, "resizedByUser": True, "movedByUser": True,
                      "handleLayoutData": [], "minComputedSize": {"width": 0, "height": 0}}
                      for kind, child in children.items()],
                      "edgeLayoutData": [], "labelLayoutData": [], "autoLayoutState": "UNCHANGED"}})["layoutDiagram"]
    assert result["__typename"] == "SuccessPayload", result
    current = refreshed(lambda d: all(any(n["id"] == child["id"] and n["position"] == {"x": positions[kind][0], "y": positions[kind][1]}
                                         for n in d["layoutData"]["nodeLayoutData"]) for kind, child in children.items()))
    print("PASS: SVG, project, diagram, contextual related-element dialogs/imports, untyped reference rows and retyping, reference rows/edges, editable opposite endpoint labels with independent styles, remove/reimport, red borders/labels, mandatory bold and derived blue labels, operations, distinct comment/constraint styles, datatype editing, enum resize backgrounds, type parameter ports, reference/composition creation targeting T, background documentation, annotation assignment/reconnection, source/target reconnection for references, generic ports, compositions, opposites and supertypes, rejected cycles and invalid endpoints, initialized supertype arguments, dotted indirect inheritance, bidirectional reference creation and paired deletion from edges and rows, package creation/import with free-form child positions and nested packages")
    print(BASE + "/projects/" + project["project"]["id"] + "/edit/" + diagram)
finally:
    ws.close()
