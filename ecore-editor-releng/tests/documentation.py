# Copyright (c) 2026 Obeo.
# SPDX-License-Identifier: EPL-2.0

"""Check Ecore documentation properties through GraphQL, using a disposable project.

Usage: python3.13 tests/documentation.py http://localhost:8083
"""
import json
import sys
import uuid
from pathlib import Path

import requests
import websocket

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8083"


def uid():
    return str(uuid.uuid4())


def graphql(query, **variables):
    response = requests.post(BASE + "/api/graphql", json={"query": query, "variables": variables}, timeout=30)
    response.raise_for_status()
    result = response.json()
    assert "errors" not in result, result
    return result["data"]


project = graphql("""mutation($input:CreateProjectInput!) {
  createProject(input:$input) { __typename
    ... on CreateProjectSuccessPayload { project { id currentEditingContext { id } } }
    ... on ErrorPayload { messages { body } }
  }
}""", input={"id": uid(), "name": "Documentation regression test", "templateId": "blank-project",
             "libraryIds": []})["createProject"]
assert project["__typename"] == "CreateProjectSuccessPayload", project
project_id = project["project"]["id"]
context = project["project"]["currentEditingContext"]["id"]


def objects(expression):
    result = graphql("""mutation($input:EvaluateExpressionInput!) {
      evaluateExpression(input:$input) { __typename
        ... on EvaluateExpressionSuccessPayload { result { ... on ObjectsExpressionResult { value { id label } } } }
        ... on ErrorPayload { messages { body } }
      }
    }""", input={"id": uid(), "editingContextId": context, "selectedObjectIds": [], "expression": expression})["evaluateExpression"]
    assert result["__typename"] == "EvaluateExpressionSuccessPayload", result
    return result["result"]["value"]


def scalar(expression):
    result = graphql("""mutation($input:EvaluateExpressionInput!) {
      evaluateExpression(input:$input) { __typename
        ... on EvaluateExpressionSuccessPayload { result { ... on StringExpressionResult { stringValue: value } ... on IntExpressionResult { intValue: value } } }
        ... on ErrorPayload { messages { body } }
      }
    }""", input={"id": uid(), "editingContextId": context, "selectedObjectIds": [], "expression": expression})["evaluateExpression"]
    assert result["__typename"] == "EvaluateExpressionSuccessPayload", result
    values = result["result"]
    return values.get("stringValue", values.get("intValue"))


def properties(object_id, kind="documentation"):
    form_id = kind + "://?objectIds=[" + object_id + "]"
    ws = websocket.create_connection(BASE.replace("http", "ws", 1) + "/subscriptions",
                                     subprotocols=["graphql-ws"], timeout=30)
    ws.expected_form_id = form_id
    ws.send(json.dumps({"type": "connection_init", "payload": {}}))
    assert json.loads(ws.recv())["type"] == "connection_ack"
    ws.send(json.dumps({"id": "properties", "type": "start", "payload": {
        "query": """subscription($input:FormEventInput!) {
          formEvent(input:$input) { __typename
            ... on FormRefreshedEventPayload { form { id pages { label groups { widgets {
              __typename id label readOnly ... on RichText { value } ... on Textarea { value }
            } } } } }
            ... on ErrorPayload { messages { body } }
          }
        }""", "variables": {"input": {"id": uid(), "editingContextId": context, "formId": form_id}}}}))
    return form_id, ws


def refreshed(ws, predicate=lambda pages: True):
    for _ in range(30):
        message = json.loads(ws.recv())
        assert message["type"] != "error", message
        if message["type"] == "data":
            assert "errors" not in message["payload"], message
            payload = message["payload"]["data"]["formEvent"]
            assert payload["__typename"] != "ErrorPayload", payload
            if payload["__typename"] == "FormRefreshedEventPayload":
                ws.form_id = payload["form"]["id"]
                if ws.expected_form_id.startswith("documentation://"):
                    assert ws.form_id == ws.expected_form_id, payload["form"]
                pages = payload["form"]["pages"]
                if predicate(pages):
                    return pages
    raise AssertionError("No properties form received")


def documentation_widget(pages):
    assert len(pages) == 1 and pages[0]["label"] == "Documentation", pages
    containing = [p for p in pages if any(w["label"] in ("Documentation", "Documentation (HTML)")
                  for g in p["groups"] for w in g["widgets"])]
    assert len(containing) == 1, pages
    widgets = [w for g in containing[0]["groups"] for w in g["widgets"]]
    assert len(widgets) == 1 and widgets[0]["label"] in ("Documentation", "Documentation (HTML)"), widgets
    assert sum(w["label"] in ("Documentation", "Documentation (HTML)") for w in widgets) == 1, widgets
    return widgets[0]


def edit(ws, widget, value):
    if widget["__typename"] == "RichText":
        field, input_type, widget_field = "editRichText", "EditRichTextInput", "richTextId"
    else:
        assert widget["__typename"] == "Textarea", widget
        field, input_type, widget_field = "editTextfield", "EditTextfieldInput", "textfieldId"
    result = graphql("mutation($input:" + input_type + "!) { " + field + "(input:$input) { __typename ... on ErrorPayload { messages { body } } } }",
                     input={"id": uid(), "editingContextId": context, "representationId": ws.form_id,
                            widget_field: widget["id"], "newValue": value})[field]
    assert result["__typename"] == "SuccessPayload", result


try:
    configuration = graphql("""query($id:ID!) {
      viewer { editingContext(editingContextId:$id) {
        workbenchConfiguration { workbenchPanels { id views { ... on DefaultViewConfiguration { id } } } }
      } }
    }""", id=context)["viewer"]["editingContext"]["workbenchConfiguration"]
    right = next(panel for panel in configuration["workbenchPanels"] if panel["id"] == "right")
    assert any(view.get("id") == "ecore-documentation" for view in right["views"]), right
    content = (Path(__file__).parent / "fixtures/library.ecore").read_text()
    content = content.replace('<eClassifiers xsi:type="ecore:EClass" name="Book">',
        '<eClassifiers xsi:type="ecore:EClass" name="Book">\n'
        '    <eAnnotations source="http://www.eclipse.org/emf/2002/GenModel">\n'
        '      <details key="documentation" value="&lt;p&gt;Existing&lt;/p&gt;"/>\n'
        '      <details key="other" value="keep me"/>\n'
        '    </eAnnotations>\n'
        '    <eAnnotations source="http://www.eclipse.org/emf/2002/Ecore">\n'
        '      <details key="constraints" value="ValidBook"/>\n'
        '      <details key="documentation" value="not a GenModel note"/>\n'
        '    </eAnnotations>')
    operations = {"query": """mutation($input:UploadDocumentInput!) {
      uploadDocument(input:$input) { __typename ... on ErrorPayload { messages { body } } }
    }""", "variables": {"input": {"id": uid(), "editingContextId": context, "file": None, "readOnly": False}}}
    response = requests.post(BASE + "/api/graphql/upload", data={"operations": json.dumps(operations),
                             "map": json.dumps({"0": "variables.file"})},
                             files={"0": ("library.ecore", content, "application/octet-stream")}, timeout=30)
    response.raise_for_status()
    assert response.json()["data"]["uploadDocument"]["__typename"] == "UploadDocumentSuccessPayload", response.text

    package = objects("aql:editingContext.allContents()->filter(ecore::EPackage)")[0]
    book = next(o for o in objects("aql:editingContext.allContents()->filter(ecore::EClass)") if o["label"] == "Book")
    attribute = objects("aql:editingContext.allContents()->filter(ecore::EAttribute)")[0]
    for element in (package, book, attribute):
        _, ws = properties(element["id"], "details")
        try:
            pages = refreshed(ws)
            assert all(w["label"] not in ("Documentation", "Documentation (HTML)")
                       for page in pages for group in page["groups"] for w in group["widgets"]), pages
        finally:
            ws.close()
    for element in (package, attribute):
        form_id, ws = properties(element["id"])
        try:
            widget = documentation_widget(refreshed(ws))
            assert widget["__typename"] == "RichText" and widget["value"] == "" and not widget["readOnly"], widget
            if element == attribute:
                assert scalar("aql:editingContext.allContents()->filter(ecore::EAttribute)->first().eAnnotations->size()") == 0
            text = "# Documentation\n\nA **bold** description"
            edit(ws, widget, text)
            updated = documentation_widget(refreshed(ws, lambda pages: documentation_widget(pages)["value"] == text))
            assert updated["value"] == text, updated
        finally:
            ws.close()
        form_id, ws = properties(element["id"])
        try:
            assert documentation_widget(refreshed(ws))["value"] == text
        finally:
            ws.close()

    descriptions = graphql("""query($ctx:ID!, $obj:ID!) {
      viewer { editingContext(editingContextId:$ctx) {
        representationDescriptions(objectId:$obj) { edges { node { id label } } }
      } }
    }""", ctx=context, obj=package["id"])["viewer"]["editingContext"]["representationDescriptions"]["edges"]
    diagram_description = next(edge["node"]["id"] for edge in descriptions if edge["node"]["label"] == "Ecore Class Diagram")
    created = graphql("""mutation($input:CreateRepresentationInput!) {
      createRepresentation(input:$input) { __typename
        ... on CreateRepresentationSuccessPayload { representation { id } }
        ... on ErrorPayload { messages { body } }
      }
    }""", input={"id": uid(), "editingContextId": context, "objectId": package["id"],
                 "representationDescriptionId": diagram_description, "representationName": "Documentation test"})["createRepresentation"]
    assert created["__typename"] == "CreateRepresentationSuccessPayload", created
    diagram_id = created["representation"]["id"]
    _, diagram_ws = properties(diagram_id)
    _, package_ws = properties(package["id"])
    try:
        diagram_widget = documentation_widget(refreshed(diagram_ws))
        package_widget = documentation_widget(refreshed(package_ws))
        assert diagram_widget["value"] == package_widget["value"] == text
        diagram_text = "Documentation edited from the **diagram background**"
        edit(diagram_ws, diagram_widget, diagram_text)
        assert documentation_widget(refreshed(diagram_ws, lambda pages: documentation_widget(pages)["value"] == diagram_text))["value"] == diagram_text
        assert documentation_widget(refreshed(package_ws, lambda pages: documentation_widget(pages)["value"] == diagram_text))["value"] == diagram_text
    finally:
        diagram_ws.close()
        package_ws.close()
    _, package_ws = properties(package["id"])
    try:
        assert documentation_widget(refreshed(package_ws))["value"] == diagram_text
    finally:
        package_ws.close()

    entry = objects("aql:editingContext.allContents()->filter(ecore::EAttribute)->first().eAnnotations.details->select(d | d.key = 'documentation')")
    assert len(entry) == 1, entry
    form_id, ws = properties(entry[0]["id"])
    owner_id, owner_ws = properties(attribute["id"])
    try:
        widget = documentation_widget(refreshed(ws))
        assert widget["__typename"] == "RichText" and widget["value"] == text, widget
        owner_widget = documentation_widget(refreshed(owner_ws))
        assert owner_widget["value"] == text, owner_widget
        from_note = "Edited from **note**"
        edit(ws, widget, from_note)
        assert documentation_widget(refreshed(ws, lambda pages: documentation_widget(pages)["value"] == from_note))["value"] == from_note
        assert documentation_widget(refreshed(owner_ws, lambda pages: documentation_widget(pages)["value"] == from_note))["value"] == from_note
        from_owner = "Edited from **attribute**"
        edit(owner_ws, owner_widget, from_owner)
        assert documentation_widget(refreshed(owner_ws, lambda pages: documentation_widget(pages)["value"] == from_owner))["value"] == from_owner
        assert documentation_widget(refreshed(ws, lambda pages: documentation_widget(pages)["value"] == from_owner))["value"] == from_owner
    finally:
        ws.close()
        owner_ws.close()
    form_id, ws = properties(entry[0]["id"])
    try:
        assert documentation_widget(refreshed(ws))["value"] == from_owner
    finally:
        ws.close()

    form_id, ws = properties(book["id"])
    try:
        widget = documentation_widget(refreshed(ws))
        assert widget["__typename"] == "Textarea" and widget["value"] == "<p>Existing</p>", widget
        html = "<p>Updated <strong>HTML</strong></p>"
        edit(ws, widget, html)
        assert documentation_widget(refreshed(ws, lambda pages: documentation_widget(pages)["value"] == html))["value"] == html
    finally:
        ws.close()
    other = objects("aql:editingContext.allContents()->filter(ecore::EAnnotation).details->select(d | d.key = 'other' or d.key = 'constraints' or (d.key = 'documentation' and d.value = 'not a GenModel note'))")
    assert len(other) == 3, other
    assert scalar("aql:editingContext.allContents()->filter(ecore::EAnnotation).details->select(d | d.key = 'other')->first().value") == "keep me"
    for detail in other:
        form_id, ws = properties(detail["id"], "details")
        try:
            pages = refreshed(ws)
            assert all(w["label"] not in ("Documentation", "Documentation (HTML)")
                       for page in pages for group in page["groups"] for w in group["widgets"]), pages
        finally:
            ws.close()
    print("PASS: Documentation on diagram roots, owners and notes, Markdown and HTML editing, generic pages, and unrelated annotations")
finally:
    result = graphql("""mutation($input:DeleteProjectInput!) { deleteProject(input:$input) { __typename } }""",
                     input={"id": uid(), "projectId": project_id})["deleteProject"]
    assert result["__typename"] == "SuccessPayload", result
