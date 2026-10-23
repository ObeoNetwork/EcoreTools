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
    private final EPackage pack = services.ecoreExample();
    private final EClass book = (EClass) pack.getEClassifier("Book");
    private final EClass library = (EClass) pack.getEClassifier("Library");
    private final EReference reference = library.getEReferences().getFirst();
    private final EClass other = (EClass) services.ecoreCreate(pack, "EClass");

    private void reconnect(int edgeIndex, boolean source, EObject semantic, EObject oldEnd, EObject otherEnd, EObject newEnd) {
        var diagram = (DiagramDescription) new EcoreViews().create().getDescriptions().getFirst();
        var palette = diagram.getEdgeDescriptions().get(edgeIndex).getPalette();
        var tool = palette.getEdgeReconnectionTools().stream()
                .filter(candidate -> source ? candidate instanceof SourceEdgeEndReconnectionTool : candidate instanceof TargetEdgeEndReconnectionTool)
                .findFirst().orElseThrow();
        var interpreter = new AQLInterpreter(List.of(EcoreServices.class), List.of(EcorePackage.eINSTANCE));
        var result = interpreter.evaluateExpression(Map.of("edgeSemanticElement", semantic, "semanticReconnectionSource", oldEnd,
                "semanticOtherEnd", otherEnd, "semanticReconnectionTarget", newEnd), ((ChangeContext) tool.getBody().getFirst()).getExpression());
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
        reconnect(bidirectional ? 1 : 0, true, reference, library, book, other);
        assertThat(reference.getEContainingClass()).isSameAs(other);
        assertThat(library.getEReferences()).isEmpty();
        assertThat(reference.isContainment()).isTrue();
        assertThat(reference.isDerived()).isTrue();
        assertThat(reference.getLowerBound()).isEqualTo(1);
        assertThat(reference.getUpperBound()).isEqualTo(-1);
        assertThat(reference.getName()).isEqualTo("books");
        assertThat(services.ecoreNote(reference, false)).isSameAs(note);
        if (opposite != null) assertThat(opposite.getEType()).isSameAs(other);
        reconnect(bidirectional ? 1 : 0, false, reference, book, other, library);
        assertThat(reference.getEType()).isSameAs(library);
        assertThat(reference.getEGenericType()).isSameAs(generic);
        if (opposite != null) {
            assertThat(opposite.getEContainingClass()).isSameAs(library);
            assertThat(reference.getEOpposite()).isSameAs(opposite);
            assertThat(opposite.getEOpposite()).isSameAs(reference);
            assertThat(opposite.getEType()).isSameAs(other);
        }
    }

    @Test
    void reconnectsReferencesBetweenClassAndGenericPortAndKeepsCompatibleArguments() {
        var parameter = (ETypeParameter) services.ecoreCreate(library, "ETypeParameter");
        reconnect(0, false, reference, book, library, parameter);
        assertThat(services.ecoreReferenceTarget(reference)).isSameAs(parameter);
        reconnect(0, false, reference, parameter, library, book);
        assertThat(reference.getEType()).isSameAs(book);
        assertThat(reference.getEGenericType().getETypeParameter()).isNull();
        services.ecoreCreate(book, "ETypeParameter");
        services.ecoreReconnectReference(reference, book, false);
        var argument = reference.getEGenericType().getETypeArguments().getFirst();
        argument.setEClassifier(EcorePackage.Literals.ESTRING);
        services.ecoreCreate(other, "ETypeParameter");
        services.ecoreReconnectReference(reference, other, false);
        assertThat(reference.getEGenericType().getETypeArguments()).containsExactly(argument);
        assertThat(argument.getEClassifier()).isSameAs(EcorePackage.Literals.ESTRING);
        services.ecoreReconnectReference(reference, library, false);
        assertThat(reference.getEGenericType().getETypeArguments()).hasSize(1);
        services.ecoreReconnectReference(reference, (EClass) services.ecoreCreate(pack, "EClass"), false);
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
        reconnect(2, true, library, library, book, other);
        assertThat(library.getEGenericSuperTypes()).isEmpty();
        assertThat(other.getEGenericSuperTypes()).containsExactly(binding);
        services.ecoreCreate(library, "ETypeParameter");
        reconnect(2, false, other, book, other, library);
        assertThat(binding.getEClassifier()).isSameAs(library);
        assertThat(binding.getETypeArguments()).containsExactly(argument);
        assertThat(argument.getEClassifier()).isSameAs(EcorePackage.Literals.ESTRING);
        assertThat(book.getESuperTypes()).isEmpty();
    }

    @Test
    void refusesCyclesDuplicatesAndIndirectInheritanceWithoutRemovingExistingBindings() {
        services.ecoreSuperType(library, book);
        services.ecoreSuperType(other, library);
        var binding = library.getEGenericSuperTypes().getFirst();
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(library, book, library, false)).hasMessageContaining("cycle");
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(library, book, other, false)).hasMessageContaining("cycle");
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(library, book, book, true)).hasMessageContaining("cycle");
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(other, book, book, false)).hasMessageContaining("indirect");
        services.ecoreSuperType(other, book);
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(library, book, other, true)).hasMessageContaining("already");
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(other, library, book, false)).hasMessageContaining("already");
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(library, book, services.ecoreCreate(book, "ETypeParameter"), false))
                .hasMessageContaining("EClasses");
        assertThat(library.getEGenericSuperTypes()).containsExactly(binding);
        assertThat(other.getESuperTypes()).containsExactly(library, book);
    }

    @Test
    void protectsReadOnlyResourcesAndAllowsUnidirectionalLinksToReadOnlyTypes() {
        var readOnlyResource = new JSONResourceFactory().createResourceFromPath("readonly");
        readOnlyResource.getContents().add(other);
        readOnlyResource.eAdapters().add(new ResourceMetadataAdapter("readonly.ecore", true));
        assertThatThrownBy(() -> services.ecoreReconnectReference(reference, other, true)).hasMessageContaining("editable");
        services.ecoreReconnectReference(reference, other, false);
        assertThat(reference.getEType()).isSameAs(other);
        services.ecoreReconnectReference(reference, book, false);
        services.ecoreCreateOpposite(reference);
        assertThatThrownBy(() -> services.ecoreReconnectReference(reference, other, false)).hasMessageContaining("editable");
        assertThat(reference.getEType()).isSameAs(book);
        readOnlyResource.getContents().add(book);
        assertThatThrownBy(() -> services.ecoreReconnectReference(reference, library, false)).hasMessageContaining("editable");
        services.ecoreSuperType(library, book);
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(library, book, other, true)).hasMessageContaining("editable");
        readOnlyResource.getContents().add(library);
        assertThatThrownBy(() -> services.ecoreReconnectSuperType(library, book, other, false)).hasMessageContaining("editable");
        assertThat(reference.getEContainingClass()).isSameAs(library);
        assertThat(library.getESuperTypes()).containsExactly(book);
    }
}
