/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package fr.obeo.ecore.editor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.components.interpreter.AQLInterpreter;
import org.eclipse.sirius.components.interpreter.Status;
import org.eclipse.sirius.components.interpreter.Result;
import org.eclipse.sirius.components.view.*;
import org.eclipse.sirius.components.view.diagram.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class EcoreEditorTests {
    private final EcoreServices services = new EcoreServices();
    private final AQLInterpreter interpreter = new AQLInterpreter(List.of(EcoreServices.class), List.of(EcorePackage.eINSTANCE));
    private final EPackage examplePackage = services.ecoreExample();
    private final EClass book = (EClass) examplePackage.getEClassifier("Book");

    @Test
    void labelsPreserveDesktopSyntaxAndPartialEdits() {
        var attribute = book.getEAttributes().getFirst();
        services.ecoreEdit(attribute, "/caption : EString = hello");
        assertThat(attribute.getName()).isEqualTo("caption");
        assertThat(attribute.isDerived()).isTrue();
        assertThat(attribute.isTransient()).isTrue();
        assertThat(attribute.isVolatile()).isTrue();
        assertThat(attribute.isChangeable()).isFalse();
        assertThat(attribute.getDefaultValueLiteral()).isEqualTo("hello");
        assertThat(services.ecoreLabel(attribute)).isEqualTo("/caption : EString = hello");
        services.ecoreEdit(attribute, ": EInt");
        assertThat(attribute.getName()).isEqualTo("caption");
        assertThat(attribute.getEType()).isSameAs(EcorePackage.Literals.EINT);
        services.ecoreEdit(attribute, "= 42");
        assertThat(attribute.getDefaultValueLiteral()).isEqualTo("42");
        services.ecoreEdit(book, "a new book");
        assertThat(book.getName()).isEqualTo("ANewBook");
        services.ecoreEdit(examplePackage, "a package");
        assertThat(examplePackage.getName()).isEqualTo("a package");
        var enumeration = (EEnum) services.ecoreCreate(examplePackage, "EEnum");
        var literal = (EEnumLiteral) services.ecoreCreate(enumeration, "EEnumLiteral");
        services.ecoreEdit(literal, "a_literal");
        assertThat(literal.getName()).isEqualTo("a_literal");
    }

    @ParameterizedTest
    @CsvSource({"0,0,1", "1,1,1", "11,1,1", "*,0,-1", "-1,0,-1"})
    void cardinalityShortcutsWorkForAttributesAndReferences(String input, int lower, int upper) {
        for (EStructuralFeature feature : List.of(book.getEAttributes().getFirst(),
                ((EClass) examplePackage.getEClassifier("Library")).getEReferences().getFirst())) {
            feature.setLowerBound(0);
            feature.setUpperBound(1);
            services.ecoreEdit(feature, input);
            assertThat(feature.getLowerBound()).isEqualTo(lower);
            assertThat(feature.getUpperBound()).isEqualTo(upper);
        }
    }

    @Test
    void referencesSupportBoundsDerivedNamesAndPartialCardinalities() {
        var created = (EReference) services.ecoreCreate(book, "EReference");
        assertThat(created.getEType()).isSameAs(EcorePackage.Literals.EOBJECT);
        var reference = services.ecoreReference(book, book, false);
        services.ecoreEdit(reference, "[1..*] /related");
        assertThat(services.ecoreLabel(reference)).isEqualTo("[1..*] /related");
        services.ecoreEdit(reference, "[0..]");
        assertThat(reference.getLowerBound()).isZero();
        assertThat(reference.getUpperBound()).isEqualTo(-1);
        assertThat(reference.getName()).isEqualTo("related");
        services.ecoreEdit(reference, "[..1]");
        assertThat(reference.getUpperBound()).isEqualTo(1);
        services.ecoreEdit(reference, "next");
        assertThat(reference.isDerived()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void referenceToolsAcceptTypeParameterPortsAndKeepDesktopCreationSemantics(boolean containment) {
        var parameter = (ETypeParameter) services.ecoreCreate(book, "ETypeParameter");
        var diagram = (DiagramDescription) new EcoreViews().create().getDescriptions().getFirst();
        var classNodeDescription = diagram.getNodeDescriptions().getFirst();
        var port = classNodeDescription.getBorderNodesDescriptions().getFirst();
        var connector = classNodeDescription.getEdgeTools().getFirst();
        var tool = connector.getPalette().getNodeTools().stream()
                .filter(candidate -> candidate.getName().equals(containment ? "Composition" : "Reference"))
                .findFirst().orElseThrow();
        assertThat(connector.getTargetElementDescriptions()).containsExactly(classNodeDescription, port);
        var body = (ChangeContext) tool.getBody().getFirst();
        var result = interpreter.evaluateExpression(Map.of("semanticEdgeSource", book, "semanticEdgeTarget", parameter), body.getExpression());
        assertThat(result.getStatus()).isNotEqualTo(Status.ERROR);
        var reference = book.getEReferences().getLast();
        assertThat(reference.getName()).isEqualTo("t");
        assertThat(reference.getEGenericType().getETypeParameter()).isSameAs(parameter);
        assertThat(reference.getEGenericType().getEClassifier()).isNull();
        assertThat(services.ecoreReferenceTarget(reference)).isSameAs(parameter);
        assertThat(reference.isContainment()).isEqualTo(containment);
        assertThat(reference.getUpperBound()).isEqualTo(containment ? -1 : 1);
        assertThat(services.ecoreHasError(reference)).isFalse();
        services.ecoreCreateOpposite(reference);
        assertThat(reference.getEOpposite()).isNull();
        var oppositeTool = diagram.getEdgeDescriptions().getFirst().getPalette().getNodeTools().getFirst();
        assertThat(interpreter.evaluateExpression(Map.of("self", reference), oppositeTool.getPreconditionExpression()).asBoolean()).contains(false);

        var target = (EClass) services.ecoreCreate(examplePackage, "EClass");
        services.ecoreCreate(target, "ETypeParameter");
        var classReference = services.ecoreReference(book, target, containment);
        assertThat(classReference.getName()).isEqualTo("newclass");
        assertThat(classReference.getEType()).isSameAs(target);
        assertThat(classReference.getEGenericType().getETypeArguments()).hasSize(1);
        assertThat(services.ecoreHasError(classReference))
                .as(() -> "Created class reference validation: " + Diagnostician.INSTANCE.validate(classReference))
                .isFalse();
        assertThatThrownBy(() -> services.ecoreReference(book, EcorePackage.Literals.ESTRING, containment))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(book.getEReferences()).containsExactly(reference, classReference);
    }

    @ParameterizedTest
    @CsvSource({"0,1,false", "1,1,true", "0,-1,false", "1,-1,true", "2,-1,true"})
    void featureLabelsCombineMandatoryDerivedAndValidationStyles(int lower, int upper, boolean bold) {
        var diagram = (DiagramDescription) new EcoreViews().create().getDescriptions().getFirst();
        var rows = diagram.getNodeDescriptions().getFirst().getChildrenDescriptions();
        var reference = ((EClass) examplePackage.getEClassifier("Library")).getEReferences().getFirst();
        var edge = diagram.getEdgeDescriptions().getFirst();
        for (boolean derived : List.of(false, true)) {
            for (boolean invalid : List.of(false, true)) {
                for (var feature : List.of(book.getEAttributes().getFirst(), reference)) {
                    feature.setDerived(derived);
                    feature.setLowerBound(lower);
                    feature.setUpperBound(upper);
                    feature.setName(invalid ? "invalid name" : "feature");
                    var label = rows.stream().filter(n -> n.getDomainType().equals("ecore::" + feature.eClass().getName()))
                            .findFirst().orElseThrow().getInsideLabel();
                    var style = label.getConditionalStyles().stream()
                            .filter(s -> interpreter.evaluateExpression(Map.of("self", feature), s.getCondition()).asBoolean().orElseThrow())
                            .map(ConditionalInsideLabelStyle::getStyle).findFirst().orElse(label.getStyle());
                    assertThat(style.isBold()).isEqualTo(bold);
                    assertThat(style.getLabelColor().getName()).isEqualTo(invalid ? "Error" : derived ? "Derived" : "Ink");
                }
                for (boolean containment : List.of(false, true)) {
                    reference.setContainment(containment);
                    var style = edge.getConditionalStyles().stream()
                            .filter(s -> interpreter.evaluateExpression(Map.of("self", reference), s.getCondition()).asBoolean().orElseThrow())
                            .map(EdgeStyle.class::cast).findFirst().orElse(edge.getStyle());
                    assertThat(style.isBold()).isEqualTo(bold);
                    assertThat(style.getColor().getName()).isEqualTo(invalid ? "Error" : derived ? "Derived" : "Ink");
                    assertThat(style.getSourceArrowStyle()).isEqualTo(containment ? ArrowStyle.FILL_DIAMOND : ArrowStyle.NONE);
                }
            }
        }
    }

    @Test
    void oppositeLinkStyleIncludesBothReferences() {
        var diagram = (DiagramDescription) new EcoreViews().create().getDescriptions().getFirst();
        var edge = diagram.getEdgeDescriptions().get(1);
        var reference = ((EClass) examplePackage.getEClassifier("Library")).getEReferences().getFirst();
        reference.setContainment(false);
        services.ecoreCreateOpposite(reference);
        for (int lower : List.of(0, 1)) {
            reference.setLowerBound(lower);
            for (int oppositeLower : List.of(0, 1)) {
                reference.getEOpposite().setLowerBound(oppositeLower);
                var style = edge.getConditionalStyles().stream()
                        .filter(s -> interpreter.evaluateExpression(Map.of("self", reference), s.getCondition()).asBoolean().orElseThrow())
                        .map(EdgeStyle.class::cast).findFirst().orElse(edge.getStyle());
                assertThat(style.isBold()).isEqualTo(lower > 0 || oppositeLower > 0);
                assertThat(style.getSourceArrowStyle()).isEqualTo(ArrowStyle.INPUT_ARROW);
            }
        }
    }

    @Test
    void operationsSupportParametersReturnTypeAndPartialEdits() {
        var operation = (EOperation) services.ecoreCreate(book, "EOperation");
        services.ecoreEdit(operation, "find(name : EString, count EInt) : Book");
        assertThat(operation.getName()).isEqualTo("find");
        assertThat(operation.getEParameters()).extracting(EParameter::getName).containsExactly("name", "count");
        assertThat(operation.getEParameters()).extracting(EParameter::getEType).containsExactly(EcorePackage.Literals.ESTRING, EcorePackage.Literals.EINT);
        assertThat(operation.getEType()).isSameAs(book);
        var first = operation.getEParameters().getFirst();
        services.ecoreEdit(operation, "(query EString)");
        assertThat(operation.getEParameters()).containsExactly(first);
        assertThat(first.getName()).isEqualTo("query");
        assertThat(operation.getName()).isEqualTo("find");
        assertThat(operation.getEType()).isSameAs(book);
        services.ecoreEdit(operation, "():");
        assertThat(operation.getEParameters()).isEmpty();
        assertThat(operation.getEType()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"false,false,false,Ink", "true,false,false,Derived", "false,true,false,Derived", "true,true,false,Derived",
            "false,true,true,Error"})
    void oppositeLinkPreservesDerivedAndErrorColors(boolean derived, boolean oppositeDerived, boolean invalid, String color) {
        var diagram = (DiagramDescription) new EcoreViews().create().getDescriptions().getFirst();
        var edge = diagram.getEdgeDescriptions().get(1);
        var reference = ((EClass) examplePackage.getEClassifier("Library")).getEReferences().getFirst();
        reference.setContainment(false);
        reference.setLowerBound(1);
        services.ecoreCreateOpposite(reference);
        reference.setDerived(derived);
        reference.getEOpposite().setDerived(oppositeDerived);
        if (invalid) reference.getEOpposite().setName("invalid name");
        var style = edge.getConditionalStyles().stream()
                .filter(s -> interpreter.evaluateExpression(Map.of("self", reference), s.getCondition()).asBoolean().orElseThrow())
                .map(EdgeStyle.class::cast).findFirst().orElse(edge.getStyle());
        assertThat(style.getColor().getName()).isEqualTo(color);
        assertThat(style.isBold()).isTrue();
        assertThat(style.getSourceArrowStyle()).isEqualTo(ArrowStyle.INPUT_ARROW);
    }

    @Test
    void genericTypesAndCaseInsensitiveLookupMatchDesktop() {
        var typeParameter = (ETypeParameter) services.ecoreCreate(book, "ETypeParameter");
        assertThat(typeParameter.getName()).isEqualTo("T");
        var attribute = book.getEAttributes().getFirst();
        services.ecoreEdit(attribute, ": T");
        assertThat(attribute.getEGenericType().getETypeParameter()).isSameAs(typeParameter);
        var operation = (EOperation) services.ecoreCreate(book, "EOperation");
        services.ecoreEdit(operation, ": T");
        assertThat(operation.getEGenericType().getETypeParameter()).isSameAs(typeParameter);
        services.ecoreEdit(operation, ": book");
        assertThat(operation.getEType()).isSameAs(book);
        assertThat(operation.getEGenericType().getETypeArguments()).hasSize(1);
    }

    @Test
    void genericBindingsAndReferencesToVisibleTypeParametersAreDisplayed() {
        var parent = (EClass) examplePackage.getEClassifier("Library");
        var parameter = (ETypeParameter) services.ecoreCreate(parent, "ETypeParameter");
        var reference = parent.getEReferences().getFirst();
        services.ecoreEditReferenceRow(reference, ": T");
        assertThat(services.ecoreReferenceTarget(reference)).isSameAs(parameter);
        String parameterId = java.util.UUID.randomUUID().toString();
        new org.eclipse.sirius.components.emf.services.EObjectIDManager().setId(parameter, parameterId);
        var port = mock(org.eclipse.sirius.components.diagrams.Node.class);
        when(port.getId()).thenReturn("port"); when(port.getTargetObjectId()).thenReturn(parameterId);
        var diagram = mock(org.eclipse.sirius.components.diagrams.Diagram.class);
        when(diagram.getNodes()).thenReturn(List.of(port));
        var context = new org.eclipse.sirius.components.collaborative.diagrams.DiagramContext(diagram);
        assertThat(services.ecoreReferenceRows(parent, context)).isEmpty();
        when(diagram.getNodes()).thenReturn(List.of());
        assertThat(services.ecoreReferenceRows(parent, context)).containsExactly(reference);
        var superType = EcoreFactory.eINSTANCE.createEGenericType();
        superType.setEClassifier(parent);
        var argument = EcoreFactory.eINSTANCE.createEGenericType();
        argument.setEClassifier(EcorePackage.Literals.ESTRING);
        superType.getETypeArguments().add(argument);
        book.getEGenericSuperTypes().add(superType);
        assertThat(services.ecoreSuperTypeLabel(book, parent)).isEqualTo("<<bind>> EString");
        var localParameter = (ETypeParameter) services.ecoreCreate(book, "ETypeParameter");
        argument.setEClassifier(null);
        argument.setETypeParameter(localParameter);
        assertThat(services.ecoreSuperTypeLabel(book, parent)).isEqualTo("<<bind>> T");
        assertThat(services.ecoreSuperTypeLabel(parent, book)).isEmpty();
        var inheritance = ((DiagramDescription) new EcoreViews().create().getDescriptions().getFirst()).getEdgeDescriptions().get(2);
        assertThat(interpreter.evaluateExpression(Map.of("self", book, "semanticEdgeTarget", parent),
                inheritance.getCenterLabelExpression()).asString()).contains("<<bind>> T");
    }

    @Test
    void errorsDisappearAfterRepairAndInheritanceCannotCycle() {
        assertThat(services.ecoreHasError(examplePackage)).isFalse();
        book.getEAttributes().getFirst().setName("not a valid name");
        assertThat(services.ecoreHasError(book)).isTrue();
        book.getEAttributes().getFirst().setName("title");
        assertThat(services.ecoreHasError(book)).isFalse();
        var other = (EClass) examplePackage.getEClassifier("Library");
        var parameter = (ETypeParameter) services.ecoreCreate(book, "ETypeParameter");
        var result = interpreter.evaluateExpression(Map.of("semanticEdgeSource", book, "semanticEdgeTarget", parameter),
                "aql:semanticEdgeSource.ecoreSuperType(semanticEdgeTarget)");
        assertThat(result.getStatus()).isNotEqualTo(Status.ERROR);
        assertThat(book.getESuperTypes()).isEmpty();
        services.ecoreSuperType(book, other);
        services.ecoreSuperType(other, book);
        assertThat(other.getESuperTypes()).isEmpty();
        services.ecoreRemoveSuperType(book, other);
        assertThat(book.getESuperTypes()).isEmpty();
    }

    @Test
    void untypedReferencesRemainVisibleAsRowsAndRetypingRestoresEdges() {
        var owner = (EClass) examplePackage.getEClassifier("Library");
        var reference = owner.getEReferences().getFirst();
        var objectIdManager = new org.eclipse.sirius.components.emf.services.EObjectIDManager();
        String targetId = java.util.UUID.randomUUID().toString();
        objectIdManager.setId(book, targetId);
        var targetNode = mock(org.eclipse.sirius.components.diagrams.Node.class);
        when(targetNode.getId()).thenReturn("book-view"); when(targetNode.getTargetObjectId()).thenReturn(targetId);
        var diagram = mock(org.eclipse.sirius.components.diagrams.Diagram.class);
        when(diagram.getNodes()).thenReturn(List.of(targetNode));
        var context = new org.eclipse.sirius.components.collaborative.diagrams.DiagramContext(diagram);
        assertThat(services.ecoreReferenceRows(owner, context)).isEmpty();
        reference.setEType(null);
        assertThat(services.ecoreReferenceRows(owner, context)).containsExactly(reference);
        assertThat(services.ecoreReferenceRowLabel(reference).trim()).isEqualTo("books");
        assertThat(services.ecoreHasError(reference)).isTrue();
        services.ecoreEditReferenceRow(reference, ": Book");
        assertThat(reference.getEType()).isSameAs(book);
        assertThat(services.ecoreReferenceRows(owner, context)).isEmpty();
        when(diagram.getNodes()).thenReturn(List.of());
        assertThat(services.ecoreReferenceRows(owner, context)).containsExactly(reference);
    }

    @Test
    void inheritedUntypedReferencesAppearOnlyWhenTheirOwnerIsAbsent() {
        var owner = (EClass) examplePackage.getEClassifier("Library");
        var reference = owner.getEReferences().getFirst();
        reference.setEType(null);
        var child = (EClass) services.ecoreCreate(examplePackage, "EClass");
        services.ecoreSuperType(child, owner);
        var diagram = mock(org.eclipse.sirius.components.diagrams.Diagram.class);
        when(diagram.getNodes()).thenReturn(List.of());
        var context = new org.eclipse.sirius.components.collaborative.diagrams.DiagramContext(diagram);
        assertThat(services.ecoreReferenceRows(child, context)).containsExactly(reference);
        String ownerId = java.util.UUID.randomUUID().toString();
        new org.eclipse.sirius.components.emf.services.EObjectIDManager().setId(owner, ownerId);
        var ownerNode = mock(org.eclipse.sirius.components.diagrams.Node.class);
        when(ownerNode.getId()).thenReturn("owner-view"); when(ownerNode.getTargetObjectId()).thenReturn(ownerId);
        when(diagram.getNodes()).thenReturn(List.of(ownerNode));
        assertThat(services.ecoreReferenceRows(child, context)).isEmpty();
        assertThat(services.ecoreReferenceRows(owner, context)).containsExactly(reference);
    }

    @Test
    void visibleAncestorsAndPendingImportsControlReferenceRows() {
        var parent = (EClass) examplePackage.getEClassifier("Library");
        var ancestor = (EClass) services.ecoreCreate(examplePackage, "EClass");
        services.ecoreSuperType(book, parent);
        services.ecoreSuperType(parent, ancestor);
        var objectIdManager = new org.eclipse.sirius.components.emf.services.EObjectIDManager();
        String ancestorId = java.util.UUID.randomUUID().toString();
        objectIdManager.setId(ancestor, ancestorId);
        var visible = mock(org.eclipse.sirius.components.diagrams.Node.class);
        when(visible.getId()).thenReturn("ancestor-view");
        when(visible.getTargetObjectId()).thenReturn(ancestorId);
        var diagram = mock(org.eclipse.sirius.components.diagrams.Diagram.class);
        when(diagram.getNodes()).thenReturn(List.of(visible));
        String parentId = java.util.UUID.randomUUID().toString();
        objectIdManager.setId(parent, parentId);
        var oldInheritance = mock(org.eclipse.sirius.components.diagrams.Edge.class);
        when(oldInheritance.getId()).thenReturn("parent-inheritance");
        when(oldInheritance.getTargetObjectId()).thenReturn(parentId);
        when(diagram.getEdges()).thenReturn(List.of(oldInheritance));
        var context = new org.eclipse.sirius.components.collaborative.diagrams.DiagramContext(diagram);
        assertThat(services.ecoreVisibleSuperTypes(book, context)).containsExactly(ancestor);
        services.ecoreNote(parent, false);
        assertThat(services.ecoreNotes(examplePackage, context)).isEmpty();
        assertThat(services.ecoreReferenceRows(parent, context)).hasSize(1);
        String bookId = java.util.UUID.randomUUID().toString();
        objectIdManager.setId(book, bookId);
        context.viewCreationRequests().add(org.eclipse.sirius.components.diagrams.ViewCreationRequest.newViewCreationRequest()
                .targetObjectId(bookId).parentElementId("diagram").descriptionId("class")
                .containmentKind(org.eclipse.sirius.components.diagrams.components.NodeContainmentKind.CHILD_NODE).build());
        assertThat(services.ecoreReferenceRows(parent, context)).isEmpty();
        context.viewDeletionRequests().add(org.eclipse.sirius.components.diagrams.ViewDeletionRequest.newViewDeletionRequest().elementId("ancestor-view").build());
        assertThat(services.ecoreVisibleSuperTypes(book, context)).isEmpty();
    }

    @Test
    void oppositeReferencesUseOneEdgeAndKeepContainmentDirection() {
        var reference = ((EClass) examplePackage.getEClassifier("Library")).getEReferences().getFirst();
        services.ecoreCreateOpposite(reference);
        assertThat(reference.getEOpposite().getEOpposite()).isSameAs(reference);
        assertThat(services.ecoreReferences(examplePackage)).containsExactly(reference);
        var diagram = (DiagramDescription) new EcoreViews().create().getDescriptions().getFirst();
        var edge = diagram.getEdgeDescriptions().get(1);
        var variables = Map.<String, Object>of("self", reference);
        assertThat(edge.getCenterLabelExpression()).isEmpty();
        assertThat(interpreter.evaluateExpression(variables, edge.getBeginLabelExpression()).asString()).contains("[0..1] opposite");
        assertThat(interpreter.evaluateExpression(variables, edge.getEndLabelExpression()).asString()).contains("[0..*] books");
        assertThat(edge.getPalette().getCenterLabelEditTool()).isNotNull();
        var beginEdit = (ChangeContext) edge.getPalette().getBeginLabelEditTool().getBody().getFirst();
        interpreter.evaluateExpression(Map.of("self", reference, "newLabel", "[0..*] /owner"), beginEdit.getExpression());
        assertThat(reference.getEOpposite().getName()).isEqualTo("owner");
        assertThat(reference.getEOpposite().isDerived()).isTrue();
        assertThat(reference.getName()).isEqualTo("books");
        var endEdit = (ChangeContext) edge.getPalette().getEndLabelEditTool().getBody().getFirst();
        interpreter.evaluateExpression(Map.of("self", reference, "newLabel", "[1..*] volumes"), endEdit.getExpression());
        assertThat(reference.getName()).isEqualTo("volumes");
        assertThat(reference.getLowerBound()).isEqualTo(1);
        assertThat(reference.getEOpposite().getName()).isEqualTo("owner");
        services.ecoreCreateOpposite(reference);
        assertThat(book.getEReferences()).hasSize(1);
        services.ecoreEditReferenceRow(reference, "/items : Book");
        assertThat(reference.getEType()).isSameAs(book);
        assertThat(reference.isDerived()).isTrue();
        assertThat(services.ecoreReferenceRowLabel(reference)).contains("/items : Book");
    }

    @Test
    void relatedImportsCoverReferencesInheritanceAndTypedMembersInBothDirections() {
        var library = (EClass) examplePackage.getEClassifier("Library");
        var parent = (EClass) services.ecoreCreate(examplePackage, "EClass");
        parent.setName("Entity");
        var child = (EClass) services.ecoreCreate(examplePackage, "EClass");
        child.setName("SpecialBook");
        var unrelated = (EClass) services.ecoreCreate(examplePackage, "EClass");
        unrelated.setName("Unrelated");
        services.ecoreSuperType(book, parent);
        services.ecoreSuperType(child, book);
        var money = (EDataType) services.ecoreCreate(examplePackage, "EDataType");
        money.setName("Money");
        var status = (EEnum) services.ecoreCreate(examplePackage, "EEnum");
        status.setName("Status");
        var price = (EAttribute) services.ecoreCreate(book, "EAttribute");
        price.setEType(money);
        var operation = (EOperation) services.ecoreCreate(book, "EOperation");
        operation.setEType(status);
        var argument = EcoreFactory.eINSTANCE.createEParameter();
        argument.setEType(money);
        operation.getEParameters().add(argument);
        assertThat(services.ecoreRelatedElements(book, null)).containsExactlyInAnyOrder(parent, child, library, money, status);
        assertThat(services.ecoreRelatedElements(money, null)).containsExactly(book);
        assertThat(services.ecoreRelatedElements(status, null)).containsExactly(book);
        assertThat(services.ecoreRelatedElements(operation, null)).containsExactlyInAnyOrder(money, status);
        assertThat(services.ecoreRelatedElements(price, null)).containsExactly(money);
        assertThat(services.ecoreRelatedElements(examplePackage, null)).containsExactlyElementsOf(examplePackage.getEClassifiers());
    }

    @Test
    void relatedImportsCrossDocumentsAndExcludeExistingAndRequestedViews() {
        var resources = new org.eclipse.emf.ecore.resource.impl.ResourceSetImpl();
        var first = new org.eclipse.emf.ecore.resource.impl.ResourceImpl(org.eclipse.emf.common.util.URI.createURI("first.ecore"));
        var second = new org.eclipse.emf.ecore.resource.impl.ResourceImpl(org.eclipse.emf.common.util.URI.createURI("second.ecore"));
        resources.getResources().addAll(List.of(first, second));
        first.getContents().add(examplePackage);
        var other = services.ecoreExample(); second.getContents().add(other);
        var consumer = (EClass) other.getEClassifier("Book");
        services.ecoreReference(consumer, book, false);
        var diagram = mock(org.eclipse.sirius.components.diagrams.Diagram.class);
        when(diagram.getNodes()).thenReturn(List.of());
        var context = new org.eclipse.sirius.components.collaborative.diagrams.DiagramContext(diagram);
        assertThat(services.ecoreRelatedElements(book, context)).contains(consumer);
        String id = java.util.UUID.randomUUID().toString();
        new org.eclipse.sirius.components.emf.services.EObjectIDManager().setId(consumer, id);
        var node = mock(org.eclipse.sirius.components.diagrams.Node.class);
        when(node.getId()).thenReturn("consumer-view"); when(node.getTargetObjectId()).thenReturn(id);
        when(diagram.getNodes()).thenReturn(List.of(node));
        assertThat(services.ecoreRelatedElements(book, context)).doesNotContain(consumer);
        when(diagram.getNodes()).thenReturn(List.of());
        context.viewCreationRequests().add(org.eclipse.sirius.components.diagrams.ViewCreationRequest.newViewCreationRequest()
                .targetObjectId(id).parentElementId("diagram").descriptionId("class")
                .containmentKind(org.eclipse.sirius.components.diagrams.components.NodeContainmentKind.CHILD_NODE).build());
        assertThat(services.ecoreRelatedElements(book, context)).doesNotContain(consumer);
    }

    @Test
    void viewHasStableIdentityAndExplicitImportWithoutSemanticDeletion() {
        var view = new EcoreViews().create();
        var diagram = (DiagramDescription) view.getDescriptions().getFirst();
        assertThat(diagram.getStyle()).isNotNull();
        assertThat(diagram.getToolbar()).isNotNull();
        assertThat(diagram.getPalette().getDropTool()).isNotNull();
        assertThat(EcoreUtil.getURI(diagram)).isEqualTo(EcoreUtil.getURI(new EcoreViews().create().getDescriptions().getFirst()));
        assertThat(diagram.getNodeDescriptions().subList(0, 4)).allMatch(n -> n.getSynchronizationPolicy() == SynchronizationPolicy.UNSYNCHRONIZED);
        var classNodeDescription = diagram.getNodeDescriptions().getFirst();
        assertThat(classNodeDescription.getChildrenDescriptions()).allMatch(n -> n.getSynchronizationPolicy() == SynchronizationPolicy.SYNCHRONIZED);
        var remove = classNodeDescription.getPalette().getNodeTools().stream().filter(t -> t.getName().equals("Remove from diagram")).findFirst().orElseThrow();
        assertThat(remove.getBody().getFirst()).isInstanceOf(DeleteView.class);
        assertThat(classNodeDescription.getPalette().getDeleteTool().getBody().getFirst()).isInstanceOf(DeleteElement.class);
        var importer = diagram.getPalette().getNodeTools().stream()
                .filter(tool -> "Import existing elements".equals(tool.getName())).findFirst().orElseThrow();
        assertThat(((SelectionDialogDescription) importer.getDialogDescription()).isMultiple()).isTrue();
        var loop = (For) importer.getBody().getFirst();
        assertThat(loop.getChildren()).hasSize(4);
        for (var node : diagram.getNodeDescriptions().subList(0, 4)) {
            var related = node.getPalette().getNodeTools().stream().filter(t -> t.getName().equals("Import related elements")).findFirst().orElseThrow();
            assertThat(node.getPalette().getQuickAccessTools()).anyMatch(t -> t.getName().equals(related.getName()));
            assertThat(((SelectionDialogDescription) related.getDialogDescription()).isMultiple()).isTrue();
            var importLoop = (For) related.getBody().getFirst();
            assertThat(importLoop.getChildren()).allSatisfy(operation ->
                    assertThat(((org.eclipse.sirius.components.view.diagram.CreateView) ((If) operation).getChildren().getFirst()).getParentViewExpression())
                            .isEqualTo("aql:null"));
        }
    }

    @Test
    void packagesKeepFreeFormContentsAndCreationToolsInEveryValidationState() {
        var diagram = (DiagramDescription) new EcoreViews().create().getDescriptions().getFirst();
        var packageNodeDescription = diagram.getNodeDescriptions().stream().filter(n -> "EPackage".equals(n.getName())).findFirst().orElseThrow();
        assertThat(packageNodeDescription.getStyle().getChildrenLayoutStrategy()).isInstanceOf(FreeFormLayoutStrategyDescription.class);
        assertThat(packageNodeDescription.getConditionalStyles()).allSatisfy(style ->
                assertThat(style.getStyle().getChildrenLayoutStrategy()).isInstanceOf(FreeFormLayoutStrategyDescription.class));
        assertThat(packageNodeDescription.getReusedChildNodeDescriptions()).containsExactlyElementsOf(diagram.getNodeDescriptions().subList(0, 4));
        for (var type : List.of("EClass", "EEnum", "EDataType", "EPackage")) {
            var tool = packageNodeDescription.getPalette().getNodeTools().stream().filter(t -> t.getName().equals("New " + type)).findFirst().orElseThrow();
            var change = (ChangeContext) tool.getBody().getFirst();
            var created = evaluateAql(this.examplePackage, change.getExpression()).asObject().orElseThrow();
            assertThat(((org.eclipse.emf.ecore.EObject) created).eContainer()).isSameAs(this.examplePackage);
            var createView = (CreateView) change.getChildren().getFirst();
            assertThat(createView.getElementDescription().getName()).isEqualTo(type);
            assertThat(createView.getParentViewExpression()).isEqualTo("aql:selectedNode");
        }
    }

    @Test
    void semanticAqlAndCreationToolsResolveAndIconsArePackaged() {
        var diagram = (DiagramDescription) new EcoreViews().create().getDescriptions().getFirst();
        evaluateAql(examplePackage, diagram.getTitleExpression());
        for (var node : diagram.getNodeDescriptions()) {
            evaluateAql(examplePackage, node.getSemanticCandidatesExpression());
            if (node.getDomainType().equals("ecore::EStringToStringMapEntry")) {
                evaluateAql(services.ecoreNote(book, false), node.getInsideLabel().getLabelExpression());
                continue;
            }
            var context = node.getName().equals("EPackage") ? examplePackage : services.ecoreCreate(examplePackage, node.getName());
            evaluateAql(context, node.getInsideLabel().getLabelExpression());
            node.getConditionalStyles().forEach(style -> evaluateAql(context, style.getCondition()));
            for (var child : node.getChildrenDescriptions()) {
                evaluateAql(context, child.getSemanticCandidatesExpression());
            }
        }
        for (var tool : diagram.getPalette().getNodeTools()) {
            if (tool.getBody().getFirst() instanceof ChangeContext change) evaluateAql(examplePackage, change.getExpression());
        }
        var reference = services.ecoreReference(book, book, true);
        var edge = diagram.getEdgeDescriptions().getFirst();
        evaluateAql(examplePackage, edge.getSemanticCandidatesExpression());
        evaluateAql(reference, edge.getSourceExpression());
        evaluateAql(reference, edge.getTargetExpression());
        evaluateAql(reference, edge.getCenterLabelExpression());
        edge.getConditionalStyles().forEach(style -> evaluateAql(reference, style.getCondition()));
        for (var classifier : EcorePackage.eINSTANCE.getEClassifiers()) {
            if (classifier instanceof EClass type && !type.isAbstract()) {
                var object = EcoreFactory.eINSTANCE.create(type);
                assertThat(getClass().getResource(services.ecoreIcon(object))).as(type.getName()).isNotNull();
            }
        }
        for (var tool : diagram.getPalette().getNodeTools()) {
            assertThat(getClass().getResource(tool.getIconURLsExpression())).isNotNull();
        }
    }

    @Test
    void backgroundDocumentationIsVisibleWithoutImportingItsPackage() {
        var diagram = (DiagramDescription) new EcoreViews().create().getDescriptions().getFirst();
        var tool = diagram.getPalette().getNodeTools().stream().filter(t -> "Documentation".equals(t.getName())).findFirst().orElseThrow();
        evaluateAql(examplePackage, ((ChangeContext) tool.getBody().getFirst()).getExpression());
        var note = services.ecoreNote(examplePackage, false);
        assertThat(services.ecoreNotes(examplePackage, null)).containsExactly(note);
        services.ecoreNote(book, false);
        assertThat(services.ecoreNotes(examplePackage, null)).containsExactly(note);
        var empty = mock(org.eclipse.sirius.components.diagrams.Diagram.class);
        when(empty.getNodes()).thenReturn(List.of());
        when(empty.getEdges()).thenReturn(List.of());
        var context = new org.eclipse.sirius.components.collaborative.diagrams.DiagramContext(empty);
        assertThat(services.ecoreNotes(examplePackage, context)).containsExactly(note);
    }

    @Test
    void documentationCanBeAssignedAndReconnectedWithoutChangingItsIdentityOrOtherMetadata() {
        var resource = new EcoreJsonResource(new org.eclipse.sirius.components.emf.services.JSONResourceFactory().createResourceURI("documentation-test"));
        resource.getContents().add(examplePackage);
        var diagram = (DiagramDescription) new EcoreViews().create().getDescriptions().getFirst();
        var noteDescription = diagram.getNodeDescriptions().getLast();
        var assignment = noteDescription.getEdgeTools().getFirst();
        var noteLink = diagram.getEdgeDescriptions().getLast();
        assertThat(assignment.getTargetElementDescriptions()).contains(diagram.getEdgeDescriptions().getFirst(), diagram.getEdgeDescriptions().get(1));
        assertThat(noteLink.getTargetDescriptions()).contains(diagram.getEdgeDescriptions().getFirst(), diagram.getEdgeDescriptions().get(1));
        var note = services.ecoreNote(examplePackage, false);
        var source = (EAnnotation) note.eContainer();
        source.getDetails().put("other", "Keep this package setting");
        note.eSet(note.eClass().getEStructuralFeature("value"), "Line one\nLine two");
        var objectIdManager = new org.eclipse.sirius.components.emf.services.EObjectIDManager();
        String noteId = objectIdManager.getOrCreateId(note);
        var existing = EcoreFactory.eINSTANCE.createEAnnotation();
        existing.setSource(source.getSource()); existing.getDetails().put("setting", "Keep this class setting");
        book.getEAnnotations().add(existing);
        var result = interpreter.evaluateExpression(Map.of("semanticEdgeSource", note, "semanticEdgeTarget", book),
                ((ChangeContext) assignment.getBody().getFirst()).getExpression());
        assertThat(result.getStatus()).isNotEqualTo(Status.ERROR);
        assertThat(note.eContainer()).isSameAs(existing);
        assertThat(source.getDetails().get("other")).isEqualTo("Keep this package setting");
        assertThat(source.getDetails().map()).doesNotContainKey("documentation");
        assertThat(existing.getDetails().get("setting")).isEqualTo("Keep this class setting");
        assertThat(existing.getDetails().get("documentation")).isEqualTo("Line one\nLine two");
        var reference = ((EClass) examplePackage.getEClassifier("Library")).getEReferences().getFirst();
        var reconnect = noteLink.getPalette().getEdgeReconnectionTools().getFirst();
        assertThat(reconnect).isInstanceOf(TargetEdgeEndReconnectionTool.class);
        result = interpreter.evaluateExpression(Map.of("edgeSemanticElement", note, "semanticReconnectionTarget", reference),
                ((ChangeContext) reconnect.getBody().getFirst()).getExpression());
        assertThat(result.getStatus()).isNotEqualTo(Status.ERROR);
        assertThat(((EAnnotation) note.eContainer()).getEModelElement()).isSameAs(reference);
        assertThat(objectIdManager.getOrCreateId(note)).isEqualTo(noteId);
        assertThat(existing.getDetails().map()).doesNotContainKey("documentation");
        assertThat(existing.getDetails().get("setting")).isEqualTo("Keep this class setting");
        var edge = mock(org.eclipse.sirius.components.diagrams.Edge.class);
        when(edge.getId()).thenReturn("reference-edge"); when(edge.getTargetObjectId()).thenReturn(objectIdManager.getOrCreateId(reference));
        var rendered = mock(org.eclipse.sirius.components.diagrams.Diagram.class);
        when(rendered.getNodes()).thenReturn(List.of()); when(rendered.getEdges()).thenReturn(List.of(edge));
        assertThat(services.ecoreNotes(examplePackage, new org.eclipse.sirius.components.collaborative.diagrams.DiagramContext(rendered))).contains(note);
    }

    @Test
    void reassigningNotesPreservesExistingDocumentationAndReadOnlyModels() {
        var feedback = mock(org.eclipse.sirius.components.core.api.IFeedbackMessageService.class);
        var services = new EcoreServices(feedback);
        var note = services.ecoreNote(examplePackage, false);
        var existing = services.ecoreNote(book, false);
        var value = note.eClass().getEStructuralFeature("value");
        note.eSet(value, "New documentation\nKeep both texts");
        existing.eSet(value, "Existing documentation");
        assertThatThrownBy(() -> services.ecoreAssignNote(note, book)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already has documentation");
        org.mockito.Mockito.verify(feedback).addFeedbackMessage(new org.eclipse.sirius.components.representations.Message(
                "The target already has documentation; both notes have been preserved", org.eclipse.sirius.components.representations.MessageLevel.ERROR));
        assertThat(((EAnnotation) note.eContainer()).getEModelElement()).isSameAs(examplePackage);
        assertThat(services.ecoreNote(book, false)).isSameAs(existing);
        assertThat(note.eGet(value)).isEqualTo("New documentation\nKeep both texts");
        assertThat(existing.eGet(value)).isEqualTo("Existing documentation");
        assertThat(services.ecoreAssignNote(existing, book)).isSameAs(existing);
        var resource = new org.eclipse.sirius.components.emf.services.JSONResourceFactory().createResourceFromPath("readonly");
        var readOnly = EcoreFactory.eINSTANCE.createEClass(); readOnly.setName("ReadOnly");
        resource.getContents().add(readOnly);
        resource.eAdapters().add(new org.eclipse.sirius.components.emf.ResourceMetadataAdapter("readonly.ecore", true));
        assertThatThrownBy(() -> services.ecoreAssignNote(note, readOnly)).isInstanceOf(IllegalArgumentException.class);
        assertThat(readOnly.getEAnnotations()).isEmpty();
        var readOnlyNote = services.ecoreNote(readOnly, false);
        assertThatThrownBy(() -> services.ecoreAssignNote(readOnlyNote, book)).isInstanceOf(IllegalArgumentException.class);
        assertThat(((EAnnotation) readOnlyNote.eContainer()).getEModelElement()).isSameAs(readOnly);
    }

    @Test
    void annotationToolsPreserveExistingTextAndOfferDirectValueEditing() {
        assertThat(services.ecoreNotes(examplePackage, null)).isEmpty();
        assertThat(services.ecoreExistingNote(book, false)).isNull();
        assertThat(book.getEAnnotations()).isEmpty();
        var documentation = services.ecoreNote(book, false);
        assertThat(services.ecoreExistingNote(book, false)).isSameAs(documentation);
        assertThat(services.ecoreExistingNote(book, true)).isNull();
        var value = documentation.eClass().getEStructuralFeature("value");
        documentation.eSet(value, "First line\nSecond line");
        assertThat(services.ecoreNote(book, false)).isSameAs(documentation);
        assertThat(documentation.eGet(value)).isEqualTo("First line\nSecond line");
        var constraints = services.ecoreNote(book, true);
        assertThat(services.ecoreExistingNote(book, true)).isSameAs(constraints);
        assertThat(constraints).isNotSameAs(documentation);
        assertThat(((EAnnotation) constraints.eContainer()).getSource()).isEqualTo(EcorePackage.eNS_URI);
    }

    @Test
    void everyPackagedImageIsRealSvg() throws Exception {
        try (var files = java.nio.file.Files.list(java.nio.file.Path.of("src/main/resources/icons/ecore"))) {
            for (var file : files.toList()) {
                var document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile());
                assertThat(document.getDocumentElement().getTagName()).isEqualTo("svg");
            }
        }
    }

    @Test
    void operationParameterParsingKeepsSeparatorPrecedenceAndAbsentParts() {
        var labels = new fr.obeo.ecore.editor.labels.EOperationServices();
        assertThat(labels.parseParameter("name : Type")).containsExactly("name", "Type");
        assertThat(labels.parseParameter("name Type")).containsExactly("name", "Type");
        assertThat(labels.parseParameter("name\tType")).containsExactly("name", "Type");
        assertThat(labels.parseParameter("name\tignored Type")).containsExactly("name\tignored", "Type");
        assertThat(labels.parseParameter(": Type")).containsExactly(null, "Type");
        assertThat(labels.parseParameter("name:")).containsExactly("name", null);
        assertThat(labels.parseParameter("name")).containsExactly("name", null);
        assertThat(labels.parseParameter(" ")).containsExactly(null, null);
    }

    @Test
    void referenceRangeParsingKeepsUnspecifiedAndInvalidBoundsDistinctFromUnbounded() {
        var labels = new fr.obeo.ecore.editor.labels.EReferenceServices();
        assertThat(labels.parseCardinality("[..2]")).containsExactly(null, 2);
        assertThat(labels.parseCardinality("[2..]")).containsExactly(2, null);
        assertThat(labels.parseCardinality("[bad..*]")).containsExactly(null, -1);
        assertThat(labels.parseCardinality("[1..2..3]")).containsExactly(1, 2);
        assertThat(labels.parseCardinality("[4]")).containsExactly(null, null);
        assertThat(labels.parseCardinality("name")).containsExactly(null, null);
    }

    private Result evaluateAql(Object self, String expression) {
        var diagram = mock(org.eclipse.sirius.components.diagrams.Diagram.class);
        when(diagram.getNodes()).thenReturn(List.of());
        var context = new org.eclipse.sirius.components.collaborative.diagrams.DiagramContext(diagram);
        var result = interpreter.evaluateExpression(Map.of("self", self, "diagramContext", context), expression);
        assertThat(result.getStatus()).as("%s: %s", expression, result).isEqualTo(Status.OK);
        return result;
    }
}
