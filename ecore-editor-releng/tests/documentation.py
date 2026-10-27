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


def properties(object_id):
    form_id = "details://?objectIds=[" + object_id + "]"
    ws = websocket.create_connection(BASE.replace("http", "ws", 1) + "/subscriptions",
                                     subprotocols=["graphql-ws"], timeout=30)
    ws.send(json.dumps({"type": "connection_init", "payload": {}}))
    assert json.loads(ws.recv())["type"] == "connection_ack"
    ws.send(json.dumps({"id": "properties", "type": "start", "payload": {
        "query": """subscription($input:FormEventInput!) {
          formEvent(input:$input) { __typename
            ... on FormRefreshedEventPayload { form { pages { label groups { widgets {
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
                pages = payload["form"]["pages"]
                if predicate(pages):
                    return pages
    raise AssertionError("No properties form received")


def documentation_widget(pages):
    assert all(p["label"] != "Documentation" for p in pages), pages
    containing = [p for p in pages if any(w["label"] in ("Documentation", "Documentation (HTML)")
                  for g in p["groups"] for w in g["widgets"])]
    assert len(containing) == 1, pages
    widgets = [w for g in containing[0]["groups"] for w in g["widgets"]]
    assert len(widgets) > 1 and widgets[0]["label"] in ("Documentation", "Documentation (HTML)"), widgets
    assert sum(w["label"] in ("Documentation", "Documentation (HTML)") for w in widgets) == 1, widgets
    return widgets[0]


def edit(form_id, widget, value):
    if widget["__typename"] == "RichText":
        field, input_type, widget_field = "editRichText", "EditRichTextInput", "richTextId"
    else:
        assert widget["__typename"] == "Textarea", widget
        field, input_type, widget_field = "editTextfield", "EditTextfieldInput", "textfieldId"
    result = graphql("mutation($input:" + input_type + "!) { " + field + "(input:$input) { __typename ... on ErrorPayload { messages { body } } } }",
                     input={"id": uid(), "editingContextId": context, "representationId": form_id,
                            widget_field: widget["id"], "newValue": value})[field]
    assert result["__typename"] == "SuccessPayload", result


try:
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
    for element in (package, attribute):
        form_id, ws = properties(element["id"])
        try:
            widget = documentation_widget(refreshed(ws))
            assert widget["__typename"] == "RichText" and widget["value"] == "" and not widget["readOnly"], widget
            if element == attribute:
                assert scalar("aql:editingContext.allContents()->filter(ecore::EAttribute)->first().eAnnotations->size()") == 0
            text = "# Documentation\n\nA **bold** description"
            edit(form_id, widget, text)
            updated = documentation_widget(refreshed(ws, lambda pages: documentation_widget(pages)["value"] == text))
            assert updated["value"] == text, updated
        finally:
            ws.close()
        form_id, ws = properties(element["id"])
        try:
            assert documentation_widget(refreshed(ws))["value"] == text
        finally:
            ws.close()

    entry = objects("aql:editingContext.allContents()->filter(ecore::EAttribute)->first().eAnnotations.details->select(d | d.key = 'documentation')")
    assert len(entry) == 1, entry
    form_id, ws = properties(entry[0]["id"])
    try:
        widget = documentation_widget(refreshed(ws))
        assert widget["__typename"] == "RichText" and widget["value"] == text, widget
        from_note = "Edited from **note**"
        edit(form_id, widget, from_note)
        assert documentation_widget(refreshed(ws, lambda pages: documentation_widget(pages)["value"] == from_note))["value"] == from_note
    finally:
        ws.close()
    form_id, ws = properties(attribute["id"])
    try:
        widget = documentation_widget(refreshed(ws))
        assert widget["value"] == from_note, widget
        from_owner = "Edited from **attribute**"
        edit(form_id, widget, from_owner)
        assert documentation_widget(refreshed(ws, lambda pages: documentation_widget(pages)["value"] == from_owner))["value"] == from_owner
    finally:
        ws.close()
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
        edit(form_id, widget, html)
        assert documentation_widget(refreshed(ws, lambda pages: documentation_widget(pages)["value"] == html))["value"] == html
    finally:
        ws.close()
    other = objects("aql:editingContext.allContents()->filter(ecore::EAnnotation).details->select(d | d.key = 'other' or d.key = 'constraints' or (d.key = 'documentation' and d.value = 'not a GenModel note'))")
    assert len(other) == 3, other
    assert scalar("aql:editingContext.allContents()->filter(ecore::EAnnotation).details->select(d | d.key = 'other')->first().value") == "keep me"
    for detail in other:
        form_id, ws = properties(detail["id"])
        try:
            pages = refreshed(ws)
            assert all(w["label"] not in ("Documentation", "Documentation (HTML)")
                       for page in pages for group in page["groups"] for w in group["widgets"]), pages
        finally:
            ws.close()
    print("PASS: Documentation properties on owners and notes, Markdown and HTML editing, generic pages, and unrelated annotations")
finally:
    result = graphql("""mutation($input:DeleteProjectInput!) { deleteProject(input:$input) { __typename } }""",
                     input={"id": uid(), "projectId": project_id})["deleteProject"]
    assert result["__typename"] == "SuccessPayload", result
