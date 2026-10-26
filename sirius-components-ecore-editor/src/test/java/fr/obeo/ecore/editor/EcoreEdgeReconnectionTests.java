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
import org.eclipse.sirius.components.view.diagram.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EcoreEdgeReconnectionTests {
    private final EcoreServices services = new EcoreServices();
    private final EPackage examplePackage = services.ecoreExample();
    private final EClass book = (EClass) examplePackage.getEClassifier("Book");
    private final EClass library = (EClass) examplePackage.getEClassifier("Library");
    private final EReference reference = library.getEReferences().getFirst();
    private final EClass otherClass = (EClass) services.ecoreCreate(examplePackage, "EClass");

    private void executeReconnectionTool(int edgeDescriptionIndex, boolean reconnectSource, EObject edgeSemanticElement, EObject semanticReconnectionSource, EObject semanticOtherEnd, EObject semanticReconnectionTarget) {
        var diagram = (DiagramDescription) new EcoreViews().create().getDescriptions().getFirst();
        var palette = diagram.getEdgeDescriptions().get(edgeDescriptionIndex).getPalette();
        var tool = palette.getEdgeReconnectionTools().stream()
                .filter(candidate -> reconnectSource ? candidate instanceof SourceEdgeEndReconnectionTool : candidate instanceof TargetEdgeEndReconnectionTool)
                .findFirst().orElseThrow();
        var interpreter = new AQLInterpreter(List.of(EcoreServices.class), List.of(EcorePackage.eINSTANCE));
        var result = interpreter.evaluateExpression(Map.of("edgeSemanticElement", edgeSemanticElement, "semanticReconnectionSource", semanticReconnectionSource,
                "semanticOtherEnd", semanticOtherEnd, "semanticReconnectionTarget", semanticReconnectionTarget), ((ChangeContext) tool.getBody().getFirst()).getExpression());
        assertThat(result.getStatus()).isNotEqualTo(Status.ERROR);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reconnectsBothReferenceEndsAndPreservesMetadata(boolean bidirectional) {
        var note = services.ecoreNote(reference, false);
        reference.setLowerBound(1);
        reference.setDerived(true);
        if (bidirectional) services.ecoreCreateOpposite(reference);
        var opposite = reference.getEOpposite();
        var generic = reference.getEGenericType();
        executeReconnectionTool(bidirectional ? 1 : 0, true, reference, library, book, otherClass);
        assertThat(reference.getEContainingClass()).isSameAs(otherClass);
        assertThat(library.getEReferences()).isEmpty();
        assertThat(reference.isContainment()).isTrue();
        assertThat(reference.isDerived()).isTrue();
        assertThat(reference.getLowerBound()).isEqualTo(1);
        assertThat(reference.getUpperBound()).isEqualTo(-1);
        assertThat(reference.getName()).isEqualTo("books");
        assertThat(services.ecoreNote(reference, false)).isSameAs(note);
        if (opposite != null) assertThat(opposite.getEType()).isSameAs(otherClass);
        executeReconnectionTool(bidirectional ? 1 : 0, false, reference, book, otherClass, library);
        assertThat(reference.getEType()).isSameAs(library);
        assertThat(reference.getEGenericType()).isSameAs(generic);
        if (opposite != null) {
            assertThat(opposite.getEContainingClass()).isSameAs(library);
            assertThat(reference.getEOpposite()).isSameAs(opposite);
            assertThat(opposite.getEOpposite()).isSameAs(reference);
            assertThat(opposite.getEType()).isSameAs(otherClass);
        }
    }

    @Test
    void reconnectsReferencesBetweenClassAndGenericPortAndKeepsCompatibleArguments() {
        var parameter = (ETypeParameter) services.ecoreCreate(library, "ETypeParameter");
        executeReconnectionTool(0, false, reference, book, library, parameter);
        assertThat(services.ecoreReferenceTarget(reference)).isSameAs(parameter);
        executeReconnectionTool(0, false, reference, parameter, library, book);
        assertThat(reference.getEType()).isSameAs(book);
        assertThat(reference.getEGenericType().getETypeParameter()).isNull();
        services.ecoreCreate(book, "ETypeParameter");
        services.ecoreReconnectReference(reference, book, false);
        var argument = reference.getEGenericType().getETypeArguments().getFirst();
        argument.setEClassifier(EcorePackage.Literals.ESTRING);
        services.ecoreCreate(otherClass, "ETypeParameter");
        services.ecoreReconnectReference(reference, otherClass, false);
        assertThat(reference.getEGenericType().getETypeArguments()).containsExactly(argument);
        assertThat(argument.getEClassifier()).isSameAs(EcorePackage.Literals.ESTRING);
        services.ecoreReconnectReference(reference, library, false);
        assertThat(reference.getEGenericType().getETypeArguments()).hasSize(1);
        services.ecoreReconnectReference(reference, (EClass) services.ecoreCreate(examplePackage, "EClass"), false);
        assertThat(reference.getEGenericType().getETypeArguments()).isEmpty();
    }

    @Test
    void refusesInvalidReferenceEndsBeforeChangingEitherOpposite() {
        services.ecoreCreateOpposite(reference);
        var opposite = reference.getEOpposite();
        var parameter = services.ecoreCreate(book, "ETypeParameter");
        assertThatThrownBy(() -> services.ecoreReconnectReference(reference, parameter, false)).hasMessageContaining("opposite");
        assertThatThrownBy(() -> services.ecoreReconnectReference(reference, parameter, true)).hasMessageContaining("source");
        assertThatThrownBy(() -> services.ecoreReconnectReference(reference, EcorePackage.Literals.ESTRING, false)).hasMessageContaining("EClass");
        assertThat(reference.getEContainingClass()).isSameAs(library);
        assertThat(reference.getEType()).isSameAs(book);
        assertThat(opposite.getEContainingClass()).isSameAs(book);
        assertThat(opposite.getEType()).isSameAs(library);
    }

    @Test
    void reconnectsSupertypeEndsWithoutReplacingTheGenericBinding() {
        services.ecoreCreate(book, "ETypeParameter");
        services.ecoreSuperType(library, book);
        var binding = library.getEGenericSuperTypes().getFirst();
        var argument = binding.getETypeArguments().getFirst();
        argument.setEClassifier(EcorePackage.Literals.ESTRING);
        executeReconnectionTool(2, true, library, library, book, otherClass);
        assertThat(library.getEGenericSuperTypes()).isEmpty();
        assertThat(otherClass.getEGenericSuperTypes()).containsExactly(binding);
        services.ecoreCreate(library, "ETypeParameter");
        executeReconnectionTool(2, false, otherClass, book, otherClass, library);
        assertThat(binding.getEClassifier()).isSameAs(library);
        assertThat(binding.getETypeArguments()).containsExactly(argument);
        assertThat(argument.getEClassifier()).isSameAs(EcorePackage.Literals.ESTRING);
        assertThat(book.getESuperTypes()).isEmpty();
    }

    @Test
    void refusesCyclesDuplicatesAndIndirectInheritanceWithoutRemovingExistingBindings() {
        services.ecoreSuperType(library, book);
        services.ecoreSuperType(otherClass, library);
        var binding = library.getEGenericSuperTypes().getFirst();
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(library, book, library, false)).hasMessageContaining("cycle");
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(library, book, otherClass, false)).hasMessageContaining("cycle");
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(library, book, book, true)).hasMessageContaining("cycle");
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(otherClass, book, book, false)).hasMessageContaining("indirect");
        services.ecoreSuperType(otherClass, book);
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(library, book, otherClass, true)).hasMessageContaining("already");
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(otherClass, library, book, false)).hasMessageContaining("already");
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(library, book, services.ecoreCreate(book, "ETypeParameter"), false))
                .hasMessageContaining("EClasses");
        assertThat(library.getEGenericSuperTypes()).containsExactly(binding);
        assertThat(otherClass.getESuperTypes()).containsExactly(library, book);
    }

    @Test
    void protectsReadOnlyResourcesAndAllowsUnidirectionalLinksToReadOnlyTypes() {
        var readOnlyResource = new JSONResourceFactory().createResourceFromPath("readonly");
        readOnlyResource.getContents().add(otherClass);
        readOnlyResource.eAdapters().add(new ResourceMetadataAdapter("readonly.ecore", true));
        assertThatThrownBy(() -> services.ecoreReconnectReference(reference, otherClass, true)).hasMessageContaining("editable");
        services.ecoreReconnectReference(reference, otherClass, false);
        assertThat(reference.getEType()).isSameAs(otherClass);
        services.ecoreReconnectReference(reference, book, false);
        services.ecoreCreateOpposite(reference);
        assertThatThrownBy(() -> services.ecoreReconnectReference(reference, otherClass, false)).hasMessageContaining("editable");
        assertThat(reference.getEType()).isSameAs(book);
        readOnlyResource.getContents().add(book);
        assertThatThrownBy(() -> services.ecoreReconnectReference(reference, library, false)).hasMessageContaining("editable");
        services.ecoreSuperType(library, book);
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(library, book, otherClass, true)).hasMessageContaining("editable");
        readOnlyResource.getContents().add(library);
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(library, book, otherClass, false)).hasMessageContaining("editable");
        assertThat(reference.getEContainingClass()).isSameAs(library);
        assertThat(library.getESuperTypes()).containsExactly(book);
    }
}
