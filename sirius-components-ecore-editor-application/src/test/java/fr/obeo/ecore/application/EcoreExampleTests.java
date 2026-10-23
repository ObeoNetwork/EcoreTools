/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package fr.obeo.ecore.application;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.Map;
import fr.obeo.ecore.editor.EcoreJsonResource;
import fr.obeo.ecore.editor.EcoreResourceLoader;
import java.util.List;
import org.eclipse.sirius.components.emf.services.EObjectIDManager;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.web.application.editingcontext.services.ResourceLoader;
import org.eclipse.sirius.web.application.document.services.XMLDocumentExporter;
import org.junit.jupiter.api.Test;

class EcoreExampleTests {
    @Test
    void xmlDownloadCanBeOpenedAsAnEcoreModel() throws Exception {
        var resource = new EcoreJsonResource(new JSONResourceFactory().createResourceURI("download-test"));
        resource.getContents().add(EcoreExample.load());
        var bytes = new XMLDocumentExporter().getBytes(resource, "application/xml").orElseThrow();
        var downloaded = new EcoreResourceFactoryImpl().createResource(URI.createURI("download.ecore"));
        new ResourceSetImpl().getResources().add(downloaded);
        downloaded.load(new ByteArrayInputStream(bytes), Map.of());
        assertThat(downloaded.getErrors()).isEmpty();
        assertThat(Diagnostician.INSTANCE.validate(downloaded.getContents().getFirst()).getSeverity()).isZero();
        assertThat(((EPackage) downloaded.getContents().getFirst()).getEClassifiers()).hasSize(9);
    }

    @Test
    void examplePreservesTheImageSemanticsAndSurvivesPersistence() throws Exception {
        var model = EcoreExample.load();
        assertThat(Diagnostician.INSTANCE.validate(model).getSeverity()).isZero();
        var set = (EClass) model.getEClassifier("Set");
        var source = (EClass) model.getEClassifier("Source");
        var target = (EClass) model.getEClassifier("E");
        var setOfThing = (EClass) model.getEClassifier("SetOfThing");
        assertThat(set.getETypeParameters()).extracting(type -> type.getName()).containsExactly("T", "V");
        assertThat(set.getEReferences().getFirst().getEGenericType().getETypeParameter()).isSameAs(set.getETypeParameters().getFirst());
        assertThat(source.getEGenericSuperTypes().getFirst().getETypeArguments())
                .extracting(type -> type.getEClassifier().getName()).containsExactly("C", "E");
        assertThat(setOfThing.getEGenericSuperTypes().getFirst().getETypeArguments().get(1).getETypeParameter())
                .isSameAs(setOfThing.getETypeParameters().getFirst());
        assertThat(source.getEAttributes()).extracting(feature -> feature.getLowerBound()).containsExactly(1, 0);
        assertThat(source.getEReferences()).extracting(reference -> reference.getName()).containsExactly(
                "optionalToE", "requiredToE", "derivedToE", "containedToE", "containedDerived", "source", "mandatoryE");
        assertThat(((EReference) target.getEStructuralFeature("eMany")).getEOpposite()).isSameAs(source.getEStructuralFeature("source"));
        assertThat(target.getESuperTypes().getFirst().isInterface()).isTrue();
        assertThat(((EEnum) model.getEClassifier("Enumeration")).getELiterals()).extracting(literal -> literal.getName()).containsExactly("ok", "nok");
        assertThat(model.getEClassifier("SomeDataType").getEAnnotations().getFirst().getDetails().get("constraints")).isEqualTo("hasNoSpecialChar");
        assertThat(model.getESubpackages()).extracting(pack -> pack.getName()).containsExactly("subPackage");
        var resource = new EcoreJsonResource(new JSONResourceFactory().createResourceURI("example-test"));
        resource.getContents().add(model);
        var bytes = new ByteArrayOutputStream();
        resource.save(bytes, Map.of());
        String parameterId = new EObjectIDManager().getOrCreateId(set.getETypeParameters().getFirst());
        var loader = new EcoreResourceLoader(new ResourceLoader(List.of()), List.of());
        var restored = loader.toResource(new ResourceSetImpl(), "example-test", "Example", bytes.toString(java.nio.charset.StandardCharsets.UTF_8), false, false).orElseThrow();
        assertThat(restored).isInstanceOf(EcoreJsonResource.class);
        var diagnostic = Diagnostician.INSTANCE.validate(restored.getContents().getFirst());
        assertThat(diagnostic.getSeverity()).as(diagnostic.toString()).isZero();
        assertThat(((EPackage) restored.getContents().getFirst()).getEClassifiers()).hasSize(9);
        var restoredSet = (EClass) ((EPackage) restored.getContents().getFirst()).getEClassifier("Set");
        assertThat(new EObjectIDManager().getOrCreateId(restoredSet.getETypeParameters().getFirst())).isEqualTo(parameterId);
        restoredSet.getETypeParameters().getFirst().setName("Element");
        bytes.reset();
        restored.save(bytes, Map.of());
        var reopened = loader.toResource(new ResourceSetImpl(), "example-test", "Example", bytes.toString(java.nio.charset.StandardCharsets.UTF_8), false, false).orElseThrow();
        assertThat(Diagnostician.INSTANCE.validate(reopened.getContents().getFirst()).getSeverity()).isZero();
        var reopenedSet = (EClass) ((EPackage) reopened.getContents().getFirst()).getEClassifier("Set");
        assertThat(reopenedSet.getEReferences().getFirst().getEGenericType().getETypeParameter().getName()).isEqualTo("Element");
        assertThat(new EObjectIDManager().getOrCreateId(reopenedSet.getETypeParameters().getFirst())).isEqualTo(parameterId);
    }
}
