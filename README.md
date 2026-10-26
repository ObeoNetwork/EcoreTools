# Ecore Editor for Sirius Web

A browser-based editor for EMF Ecore metamodels, inspired by the **Design**
class diagram in [Eclipse EcoreTools](https://eclipse.dev/ecoretools/).
Built with Sirius Web, it edits standard `.ecore` models and provides a
reusable editor component plus a standalone application to try it out.

![Example class diagram](sirius-components-ecore-editor-application/src/main/resources/project-templates/ecore-example.svg)

## Try it

You need JDK 21+, Maven, and Docker with Compose. The project currently uses
Sirius Web **2026.7.3** and Spring Boot **4.1.0**.
Sirius dependencies come from GitHub Packages; configure Maven credentials
for the repository IDs declared in [the releng POM](ecore-editor-releng/pom.xml).

From the repository root:

```sh
mvn -f ecore-editor-releng/pom.xml clean verify
docker compose -f ecore-editor-releng/compose.yaml up -d --wait
java -jar sirius-components-ecore-editor-application/target/ecore-editor.jar \
  --spring.profiles.active=dev --server.port=8083
```

Open [localhost:8083](http://localhost:8083) and create a project using the
**Example** template. It includes a model and a ready-to-edit diagram with
classes, generics, references, opposites, an enum, and a constraint.
Studio is enabled for all projects, including existing ones. The standard
**Studio** and **Blank Studio** templates are also available.

To create instances, save an Ecore model with a unique package namespace URI,
then open an instance project. Use **+ → New model** and select
the **package name** in **Model type**. If several packages share a name, their
labels also include their namespace URI. This creates an empty document. Use its
**New root object** action to select the domain (package namespace URI) and a
concrete class, following Sirius Web's standard root creation flow.
**Ecore model**, **Domain**, and **View** create their corresponding
documents; use View documents to describe representations for your instances.
Metamodels are loaded from saved Ecore documents when a project opens. Reopen
the instance project after editing a metamodel; existing instances are not
automatically migrated after structural changes.

PostgreSQL runs on local port **5435** with its own persistent volume.
`docker compose -f ecore-editor-releng/compose.yaml stop` keeps your data.
Outside the `dev` profile, set
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and
`SPRING_DATASOURCE_PASSWORD` for your database.

## Import into Eclipse

Use **File → Import → Maven → Existing Maven Projects**, select the repository
root, and import all three projects. The releng project owns the parent POM;
m2e configures the Java projects from their POMs. Java 21 and m2e are required.
For an existing workspace, remove the old `ecore-editor-parent` entry without
deleting its files, import `ecore-editor-releng`, then run **Maven → Update
Project** on all three projects.

## Work with a diagram

- Create classifiers and packages with the diagram palette. In a new empty
  diagram, use **Import existing elements** or drag elements from the explorer.
  **Import related elements** adds types connected to a selected element.
- Classifiers and packages are added explicitly. Their attributes, operations,
  literals, type parameters, and relationships update automatically.
  A reference appears as an edge when its target is visible, or as a row in
  its owning class when the target is hidden or missing.
- Edit labels directly using EcoreTools syntax: `name : EString = initial`,
  `[0..*] children`, or `find(query : EString) : Book`. Both ends of an opposite
  reference have independently editable labels. References can target generic
  type parameter ports such as `T`.
- **Bidirectional reference** creates both opposite references in one action.
  Deleting a reference, whether shown as an edge or a class member, also deletes
  its opposite. Both owners must be editable.
- Reconnect either end of a reference or inheritance edge by dragging its
  endpoint. Opposite references move together. Inheritance cycles, duplicate
  supertypes, and edits to read-only elements are rejected. For an inheritance
  edge projected through a hidden parent, import the direct parent first.
  These indirect inheritance links are dotted. New supertype links initialize
  one generic argument per target type parameter, displayed as `?` until set.
- Mandatory attributes and references use bold labels; derived features use
  a `/` prefix and blue labels. EMF validation errors turn borders and labels red.
  Neutral cards use colored header accents with the original Ecore SVG icons.
  Relationship labels have transparent backgrounds; selection uses Sirius Web's
  native magenta feedback.
- Packages contain freely positioned classes, data types, enumerations and
  nested packages. Use their palette to create or import elements.
- Add **Documentation** from the background or an element's palette.
  **Assign annotation** attaches a note to an element or reference; reconnect
  its target end to move it. A target that already has documentation is rejected
  with a message, preserving both notes and their text. Constraints have a
  separate style and editable text.
- **Remove from diagram** keeps the model element. **Delete from model** deletes
  it. Downloaded Ecore documents retain the `.ecore` extension.

## Reuse the editor

| Module | Purpose |
| --- | --- |
| `ecore-editor-releng` | Parent POM, complete Maven reactor, Docker Compose, and API checks |
| `sirius-components-ecore-editor` | View descriptions, AQL services, label editing, validation styles, and SVG icons |
| `sirius-components-ecore-editor-application` | Spring Boot application with the standard Sirius Web frontend and Example template |

Run `mvn -f ecore-editor-releng/pom.xml install` from the root, then add the
component to a compatible Sirius Web application:

```xml
<dependency>
  <groupId>fr.obeo.ecore</groupId>
  <artifactId>sirius-components-ecore-editor</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Import its Spring configuration:

```java
@Import(fr.obeo.ecore.editor.EcoreEditorConfiguration.class)
```

The host application supplies Sirius Web and persistence. The component
registers the Ecore metamodel, EMF Edit support, views, services, and icons.

## Tests

`mvn -f ecore-editor-releng/pom.xml verify` runs the Java tests across the whole
reactor. With the application running, exercise the public GraphQL API and Example
template from the releng project:

```sh
cd ecore-editor-releng
python3.13 -m venv .venv
.venv/bin/pip install -r tests/requirements.txt
.venv/bin/python tests/smoke.py http://localhost:8083
.venv/bin/python tests/example.py http://localhost:8083
.venv/bin/python tests/instances.py http://localhost:8083
```

The smoke and example checks leave their projects available for inspection.
The instance check removes its own temporary projects.

## Scope and license

This project focuses on the EcoreTools Design class diagram. The standalone
application includes native Studio Domain/View authoring and instantiation of
saved Ecore models. Code generation, automatic instance migration, and the other
Desktop viewpoints remain outside the current scope.

Project sources and assets are distributed under **EPL-2.0**. See [LICENSE](LICENSE)
for the terms and [NOTICE](NOTICE) for EcoreTools, EMF, and Sirius Web attributions.
