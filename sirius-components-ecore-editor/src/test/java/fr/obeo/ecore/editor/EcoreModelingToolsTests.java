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
import java.util.List;
import java.util.Map;
import org.eclipse.emf.ecore.*;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.interpreter.AQLInterpreter;
import org.eclipse.sirius.components.interpreter.Status;
import org.eclipse.sirius.components.view.ChangeContext;
import org.eclipse.sirius.components.view.DeleteElement;
import org.eclipse.sirius.components.view.For;
import org.eclipse.sirius.components.view.diagram.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class EcoreModelingToolsTests {
    private final EcoreServices services = new EcoreServices();
    private final EPackage pack = services.ecoreExample();
    private final EClass book = (EClass) pack.getEClassifier("Book");
    private final EClass library = (EClass) pack.getEClassifier("Library");
    private final DiagramDescription diagram = (DiagramDescription) new EcoreViews().create().getDescriptions().getFirst();
    private final AQLInterpreter interpreter = new AQLInterpreter(List.of(EcoreServices.class), List.of(EcorePackage.eINSTANCE));

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bidirectionalToolCreatesTwoNonContainmentReferencesIncludingSelfLinks(boolean selfLink) {
        var target = selfLink ? library : book;
        services.ecoreCreate(target, "ETypeParameter");
        var clazz = diagram.getNodeDescriptions().getFirst();
        var tool = clazz.getPalette().getEdgeTools().stream().filter(t -> t.getName().equals("Bidirectional reference")).findFirst().orElseThrow();
        assertThat(tool.getTargetElementDescriptions()).containsExactly(clazz);
        int count = library.getEReferences().size();
        var result = interpreter.evaluateExpression(Map.of("semanticEdgeSource", library, "semanticEdgeTarget", target),
                ((ChangeContext) tool.getBody().getFirst()).getExpression());
        assertThat(result.getStatus()).isNotEqualTo(Status.ERROR);
        assertThat(library.getEReferences()).hasSize(count + (selfLink ? 2 : 1));
        var reference = library.getEReferences().get(count);
        var opposite = reference.getEOpposite();
        assertThat(opposite).isNotNull().isNotSameAs(reference);
        assertThat(opposite.getEOpposite()).isSameAs(reference);
        assertThat(reference.getEType()).isSameAs(target);
        assertThat(opposite.getEType()).isSameAs(library);
        assertThat(opposite.getEContainingClass()).isSameAs(target);
        for (var end : List.of(reference, opposite)) {
            assertThat(end.isContainment()).isFalse();
            assertThat(end.getLowerBound()).isZero();
            assertThat(end.getUpperBound()).isEqualTo(1);
            assertThat(end.getEGenericType().getETypeArguments()).hasSize(((EClass) end.getEType()).getETypeParameters().size());
        }
    }

    @Test
    void bidirectionalCreationRejectsGenericPortsAndReadOnlyOwnersWithoutCreatingHalfAPair() {
        var parameter = services.ecoreCreate(book, "ETypeParameter");
        var before = List.copyOf(library.getEReferences());
        assertThatThrownBy(() -> services.ecoreBidirectionalReference(library, parameter)).hasMessageContaining("EClasses");
        var resource = new JSONResourceFactory().createResourceFromPath("readonly");
        resource.getContents().add(book);
        resource.eAdapters().add(new ResourceMetadataAdapter("readonly.ecore", true));
        assertThatThrownBy(() -> services.ecoreBidirectionalReference(library, book)).hasMessageContaining("editable");
        assertThatThrownBy(() -> services.ecoreBidirectionalReference(book, library)).hasMessageContaining("editable");
        assertThat(library.getEReferences()).containsExactlyElementsOf(before);
        assertThat(book.getEReferences()).isEmpty();
    }

    @Test
    void deletionChecksTheEntirePairBeforeRemovingEitherReference() {
        var reference = library.getEReferences().getFirst();
        assertThat(services.ecoreReferencesToDelete(reference)).containsExactly(reference);
        services.ecoreCreateOpposite(reference);
        var opposite = reference.getEOpposite();
        assertThat(services.ecoreReferencesToDelete(reference)).containsExactly(opposite, reference);
        assertThat(services.ecoreReferencesToDelete(opposite)).containsExactly(reference, opposite);
        var resource = new JSONResourceFactory().createResourceFromPath("readonly");
        resource.getContents().add(book);
        resource.eAdapters().add(new ResourceMetadataAdapter("readonly.ecore", true));
        assertThatThrownBy(() -> services.ecoreReferencesToDelete(reference)).hasMessageContaining("editable");
        assertThatThrownBy(() -> services.ecoreReferencesToDelete(opposite)).hasMessageContaining("editable");
        assertThat(reference.getEContainingClass()).isSameAs(library);
        assertThat(opposite.getEContainingClass()).isSameAs(book);
        assertThat(reference.getEOpposite()).isSameAs(opposite);
        assertThat(opposite.getEOpposite()).isSameAs(reference);
        var row = diagram.getNodeDescriptions().getFirst().getChildrenDescriptions().stream()
                .filter(n -> n.getName().equals("EReference")).findFirst().orElseThrow();
        for (var delete : List.of(row.getPalette().getDeleteTool(), diagram.getEdgeDescriptions().getFirst().getPalette().getDeleteTool(),
                diagram.getEdgeDescriptions().get(1).getPalette().getDeleteTool())) {
            var loop = (For) delete.getBody().getFirst();
            assertThat(loop.getExpression()).isEqualTo("aql:self.ecoreReferencesToDelete()");
            assertThat(((ChangeContext) loop.getChildren().getFirst()).getChildren().getFirst()).isInstanceOf(DeleteElement.class);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void newSupertypeArgumentsMatchTargetParametersAndExistingBindingsArePreserved(int parameterCount) {
        for (int i = 0; i < parameterCount; i++) ((ETypeParameter) services.ecoreCreate(book, "ETypeParameter")).setName("T" + i);
        var tool = diagram.getNodeDescriptions().getFirst().getPalette().getEdgeTools().stream()
                .filter(t -> t.getName().equals("Supertype")).findFirst().orElseThrow();
        var result = interpreter.evaluateExpression(Map.of("semanticEdgeSource", library, "semanticEdgeTarget", book),
                ((ChangeContext) tool.getBody().getFirst()).getExpression());
        assertThat(result.getStatus()).isNotEqualTo(Status.ERROR);
        var binding = library.getEGenericSuperTypes().getFirst();
        assertThat(binding.getEClassifier()).isSameAs(book);
        assertThat(binding.getETypeArguments()).hasSize(parameterCount).allSatisfy(argument -> {
            assertThat(argument.getEClassifier()).isNull();
            assertThat(argument.getETypeParameter()).isNull();
        });
        if (parameterCount > 0) binding.getETypeArguments().getFirst().setEClassifier(EcorePackage.Literals.ESTRING);
        services.ecoreSuperType(library, book);
        assertThat(library.getEGenericSuperTypes()).containsExactly(binding);
        assertThat(binding.getETypeArguments()).hasSize(parameterCount);
        if (parameterCount > 0) assertThat(binding.getETypeArguments().getFirst().getEClassifier()).isSameAs(EcorePackage.Literals.ESTRING);
        services.ecoreSuperType(book, library);
        assertThat(book.getESuperTypes()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"false,false,SOLID", "false,true,DASH", "true,false,DOT", "true,true,DOT"})
    void indirectInheritanceIsDottedEvenWhenItsVisibleAncestorIsAnInterface(boolean indirect, boolean isInterface, LineStyle expected) {
        var child = (EClass) services.ecoreCreate(pack, "EClass");
        book.setInterface(isInterface);
        book.setAbstract(isInterface);
        services.ecoreSuperType(child, indirect ? library : book);
        if (indirect) services.ecoreSuperType(library, book);
        var edge = diagram.getEdgeDescriptions().get(2);
        var variables = Map.<String, Object>of("self", child, "semanticEdgeTarget", book);
        var lineStyle = edge.getConditionalStyles().stream()
                .filter(style -> interpreter.evaluateExpression(variables, style.getCondition()).asBoolean().orElseThrow())
                .map(ConditionalEdgeStyle::getLineStyle).findFirst().orElse(edge.getStyle().getLineStyle());
        assertThat(lineStyle).isEqualTo(expected);
    }
}
