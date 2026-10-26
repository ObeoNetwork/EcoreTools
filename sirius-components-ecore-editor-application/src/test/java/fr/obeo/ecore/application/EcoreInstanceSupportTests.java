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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.impl.EPackageRegistryImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.emfjson.resource.JsonResource;
import org.eclipse.sirius.web.application.editingcontext.services.ResourceLoader;
import org.eclipse.sirius.web.domain.boundedcontexts.semanticdata.Document;
import org.eclipse.sirius.web.domain.boundedcontexts.semanticdata.SemanticData;
import org.eclipse.sirius.web.domain.boundedcontexts.semanticdata.services.api.ISemanticDataSearchService;
import fr.obeo.ecore.editor.EcoreJsonResource;
import fr.obeo.ecore.editor.EcoreResourceLoader;
import org.junit.jupiter.api.Test;

class EcoreInstanceSupportTests {
    private final ISemanticDataSearchService semanticDataSearchService = mock(ISemanticDataSearchService.class);
    private final EcoreResourceLoader resourceLoader = new EcoreResourceLoader(new ResourceLoader(List.of()), List.of());
    private final EPackage.Registry globalPackageRegistry = new EPackageRegistryImpl();
    private final EcoreInstanceSupport support = new EcoreInstanceSupport(semanticDataSearchService, new ResourceLoader(List.of()), List.of(), globalPackageRegistry);

    private IEMFEditingContext createEditingContext(String id) {
        globalPackageRegistry.put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
        var resourceSet = new ResourceSetImpl();
        resourceSet.getPackageRegistry().putAll(globalPackageRegistry);
        var context = mock(IEMFEditingContext.class, RETURNS_DEEP_STUBS);
        when(context.getId()).thenReturn(id);
        when(context.getDomain().getResourceSet()).thenReturn(resourceSet);
        return context;
    }

    private SemanticData createPersistedMetamodel(String namespace) throws Exception {
        var model = EcoreExample.load();
        model.setNsURI(namespace);
        var resource = new EcoreJsonResource(new JSONResourceFactory().createResourceURI(UUID.randomUUID().toString()));
        resource.getContents().add(model);
        var serializedModel = new ByteArrayOutputStream();
        resource.save(serializedModel, java.util.Map.of(JsonResource.OPTION_SCHEMA_LOCATION, true));
        var document = mock(Document.class);
        when(document.getId()).thenReturn(UUID.fromString(resource.getURI().path().substring(1)));
        when(document.getName()).thenReturn("metamodel.ecore");
        when(document.getContent()).thenReturn(serializedModel.toString(StandardCharsets.UTF_8));
        var definition = mock(SemanticData.class);
        when(definition.getId()).thenReturn(UUID.randomUUID());
        when(definition.getDocuments()).thenReturn(Set.of(document));
        return definition;
    }

    @Test
    void createsAnEcoreRootEvenWithoutAnySavedMetamodel() {
        var context = createEditingContext(UUID.randomUUID().toString());
        support.preProcess(context);
        assertThat(support.getStereotypes(context)).extracting(s -> s.label()).containsExactly("Ecore model");
        var document = support.handle(context, EcoreInstanceSupport.ECORE, "My model").orElseThrow();
        assertThat(document.name()).isEqualTo("My model.ecore");
        var resource = context.getDomain().getResourceSet().getResources().getFirst();
        assertThat(resource).isInstanceOf(EcoreJsonResource.class);
        var root = (EPackage) resource.getContents().getFirst();
        assertThat(root.getNsURI()).isEqualTo("urn:ecore-editor:" + document.id());
        assertThat(root.getName()).isEqualTo("model");
        assertThat(support.handle(context, "ecore-editor:instance:missing", "Invalid")).isEmpty();
        assertThat(support.handle(context, EcoreInstanceSupport.ECORE, " ")).isEmpty();
        assertThat(context.getDomain().getResourceSet().getResources()).hasSize(1);
        assertThat(support.canHandle(context, "domain")).isFalse();
        assertThat(support.canHandle(context, "")).isFalse();
        assertThat(support.getStereotypes(mock(IEditingContext.class))).isEmpty();
    }

    @Test
    void registersIndependentSnapshotsAndPersistsInstancesAcrossContextReload() throws Exception {
        var definition = createPersistedMetamodel("urn:runtime-test");
        when(semanticDataSearchService.findAllByDomains(anyList())).thenReturn(List.of(definition));
        var originalContext = createEditingContext(UUID.randomUUID().toString());
        support.preProcess(originalContext);
        var stereotypes = support.getStereotypes(originalContext);
        assertThat(stereotypes).extracting(s -> s.label()).contains("example", "subPackage")
                .noneMatch(label -> label.startsWith("Instance of "));
        assertThat(stereotypes).filteredOn(s -> s.label().equals("example")).hasSize(1);
        var instanceStereotype = stereotypes.stream().filter(s -> s.label().equals("example")).findFirst().orElseThrow();
        assertThat(instanceStereotype.id()).isEqualTo("ecore-editor:instance:urn:runtime-test");
        var document = support.handle(originalContext, instanceStereotype.id(), "instances").orElseThrow();
        var resource = originalContext.getDomain().getResourceSet().getResources().getFirst();
        assertThat(resource.getContents()).isEmpty();
        var ePackage = originalContext.getDomain().getResourceSet().getPackageRegistry().getEPackage("urn:runtime-test");
        resource.getContents().add(EcoreUtil.create((EClass) ePackage.getEClassifier("E")));
        assertThat(resource.getContents()).hasSize(1);
        assertThat(resource.getContents().getFirst().eClass().getName()).isEqualTo("E");

        var serializedModel = new ByteArrayOutputStream();
        resource.save(serializedModel, java.util.Map.of(JsonResource.OPTION_SCHEMA_LOCATION, true));

        var reloadedContext = createEditingContext(UUID.randomUUID().toString());
        support.preProcess(reloadedContext);
        assertThat(reloadedContext.getDomain().getResourceSet().getPackageRegistry().getEPackage("urn:runtime-test"))
                .isNotSameAs(originalContext.getDomain().getResourceSet().getPackageRegistry().getEPackage("urn:runtime-test"));
        var reloadedResource = resourceLoader.toResource(reloadedContext.getDomain().getResourceSet(), document.id().toString(), document.name(),
                serializedModel.toString(StandardCharsets.UTF_8), false, false).orElseThrow();
        assertThat(reloadedResource.getErrors()).isEmpty();
        assertThat(reloadedResource.getContents()).hasSize(1);
        assertThat(reloadedResource.getContents().getFirst().eClass().getName()).isEqualTo("E");
        assertThat(reloadedResource.getContents().getFirst().eClass().getEPackage().getNsURI()).isEqualTo("urn:runtime-test");

        var duplicate = createPersistedMetamodel("urn:runtime-test");
        when(semanticDataSearchService.findAllByDomains(anyList())).thenReturn(List.of(definition, duplicate));
        var ambiguousContext = createEditingContext(UUID.randomUUID().toString());
        support.preProcess(ambiguousContext);
        assertThat(support.getStereotypes(ambiguousContext)).extracting(s -> s.label()).containsExactly("Ecore model");
        var pinnedResource = resourceLoader.toResource(ambiguousContext.getDomain().getResourceSet(), document.id().toString(), document.name(),
                serializedModel.toString(StandardCharsets.UTF_8), false, false).orElseThrow();
        assertThat(pinnedResource.getContents().getFirst().eClass().getName()).isEqualTo("E");
        assertThat(pinnedResource.getContents().getFirst().eClass().getEPackage().eResource().getURI())
                .isEqualTo(originalContext.getDomain().getResourceSet().getPackageRegistry().getEPackage("urn:runtime-test").eResource().getURI());
    }

    @Test
    void samePackageNamesKeepBothEntriesAndShowTheirNamespaceUris() throws Exception {
        var first = createPersistedMetamodel("urn:first");
        var second = createPersistedMetamodel("urn:second");
        when(semanticDataSearchService.findAllByDomains(anyList())).thenReturn(List.of(first, second));
        var context = createEditingContext(UUID.randomUUID().toString());
        support.preProcess(context);
        assertThat(support.getStereotypes(context)).extracting(s -> s.id(), s -> s.label()).contains(
                tuple("ecore-editor:instance:urn:first", "example (urn:first)"),
                tuple("ecore-editor:instance:urn:second", "example (urn:second)"));
        assertThat(support.getStereotypes(context)).extracting(s -> s.label()).doesNotContain("example");
        assertThat(support.handle(context, "ecore-editor:instance:urn:first", "First instances")).isPresent();
        assertThat(support.handle(context, "ecore-editor:instance:urn:second", "Second instances")).isPresent();
        assertThat(context.getDomain().getResourceSet().getResources()).hasSize(2);
    }

    @Test
    void ambiguousNamespacesAreExcludedButTheDefiningProjectUsesItsOwnModel() throws Exception {
        var first = createPersistedMetamodel("urn:duplicate");
        var second = createPersistedMetamodel("urn:duplicate");
        when(semanticDataSearchService.findAllByDomains(anyList())).thenReturn(List.of(first, second));
        var other = createEditingContext(UUID.randomUUID().toString());
        support.preProcess(other);
        assertThat(support.getStereotypes(other)).extracting(s -> s.label()).containsExactly("Ecore model");
        var owner = createEditingContext(first.getId().toString());
        support.preProcess(owner);
        assertThat(support.getStereotypes(owner)).extracting(s -> s.label()).contains("example");
    }

    @Test
    void neverReplacesBuiltInPackagesAndRejectsIncompleteReloads() throws Exception {
        var builtIn = createPersistedMetamodel(EcorePackage.eNS_URI);
        when(semanticDataSearchService.findAllByDomains(anyList())).thenReturn(List.of(builtIn));
        var context = createEditingContext(UUID.randomUUID().toString());
        support.preProcess(context);
        assertThat(context.getDomain().getResourceSet().getPackageRegistry().getEPackage(EcorePackage.eNS_URI)).isSameAs(EcorePackage.eINSTANCE);
        var persisted = createPersistedMetamodel("urn:missing");
        when(semanticDataSearchService.findById(UUID.fromString(context.getId()))).thenReturn(Optional.of(persisted));
        assertThatThrownBy(() -> support.postProcess(context)).isInstanceOf(IllegalStateException.class).hasMessageContaining("saved data have been preserved");
    }

    @Test
    void editableMetamodelDocumentsTakePrecedenceOverSnapshotsAfterLoading() throws Exception {
        var definition = createPersistedMetamodel("urn:editable");
        when(semanticDataSearchService.findAllByDomains(anyList())).thenReturn(List.of(definition));
        var context = createEditingContext(definition.getId().toString());
        support.preProcess(context);
        var saved = definition.getDocuments().iterator().next();
        var resourceSet = context.getDomain().getResourceSet();
        var editable = resourceLoader.toResource(resourceSet, saved.getId().toString(), saved.getName(), saved.getContent(), false, false).orElseThrow();
        support.postProcess(context);
        assertThat(resourceSet.getResource(editable.getURI(), false)).isSameAs(editable);
        assertThat(resourceSet.getResources()).containsExactly(editable);
    }

    @Test
    void studioTemporaryResourceSetsLoadInstancesWithoutAnEditingContext() throws Exception {
        var definition = createPersistedMetamodel("urn:studio-instance");
        when(semanticDataSearchService.findAllByDomains(anyList())).thenReturn(List.of(definition));
        var context = createEditingContext(UUID.randomUUID().toString());
        support.preProcess(context);
        var stereotype = support.getStereotypes(context).stream().filter(s -> s.label().equals("example")).findFirst().orElseThrow();
        var document = support.handle(context, stereotype.id(), "instances").orElseThrow();
        var ePackage = context.getDomain().getResourceSet().getPackageRegistry().getEPackage("urn:studio-instance");
        context.getDomain().getResourceSet().getResources().getFirst().getContents().add(EcoreUtil.create((EClass) ePackage.getEClassifier("E")));
        var serializedModel = new ByteArrayOutputStream();
        context.getDomain().getResourceSet().getResources().getFirst().save(serializedModel, java.util.Map.of(JsonResource.OPTION_SCHEMA_LOCATION, true));
        var studio = new ResourceSetImpl();
        studio.getPackageRegistry().putAll(globalPackageRegistry);
        var studioLoader = new EcoreInstanceResourceLoader(new ResourceLoader(List.of()), List.of(), support);
        var reloadedResource = studioLoader.toResource(studio, document.id().toString(), document.name(), serializedModel.toString(StandardCharsets.UTF_8), false, true).orElseThrow();
        assertThat(reloadedResource.getContents().getFirst().eClass().getName()).isEqualTo("E");
        assertThat(studio.getResources()).containsExactly(reloadedResource);
        support.initializePackages(studio, null);
        verify(semanticDataSearchService, times(2)).findAllByDomains(anyList());
    }
}
