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

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextProcessor;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.web.application.document.dto.DocumentDTO;
import org.eclipse.sirius.web.application.document.dto.Stereotype;
import org.eclipse.sirius.web.application.document.services.api.IStereotypeHandler;
import org.eclipse.sirius.web.application.document.services.api.IStereotypeProvider;
import org.eclipse.sirius.components.emf.migration.api.IMigrationParticipant;
import org.eclipse.sirius.web.application.editingcontext.services.ResourceLoader;
import org.eclipse.sirius.web.application.views.explorer.services.ExplorerDescriptionProvider;
import org.eclipse.sirius.web.domain.boundedcontexts.semanticdata.services.api.ISemanticDataSearchService;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import fr.obeo.ecore.editor.EcoreJsonResource;
import fr.obeo.ecore.editor.EcoreResourceLoader;

/** Project-local metamodel snapshots and native model creation for saved Ecore definitions. */
@Service
public class EcoreInstanceSupport implements IEditingContextProcessor, IStereotypeProvider, IStereotypeHandler {
    static final String ECORE = "ecore-editor:ecore";
    private static final String INSTANCE = "ecore-editor:instance:";
    private final ISemanticDataSearchService semanticDataSearchService;
    private final EcoreResourceLoader metamodelLoader;
    private final EPackage.Registry globalPackageRegistry;

    public EcoreInstanceSupport(ISemanticDataSearchService semanticDataSearchService, ResourceLoader defaultResourceLoader,
            List<IMigrationParticipant> migrationParticipants, EPackage.Registry globalPackageRegistry) {
        this.semanticDataSearchService = semanticDataSearchService;
        this.metamodelLoader = new EcoreResourceLoader(defaultResourceLoader, migrationParticipants);
        this.globalPackageRegistry = globalPackageRegistry;
    }

    @Override
    @Transactional(readOnly = true)
    public void preProcess(IEditingContext editingContext) {
        if (!(editingContext instanceof IEMFEditingContext emfEditingContext)) {
            return;
        }
        this.initializePackages(emfEditingContext.getDomain().getResourceSet(), editingContext.getId());
    }

    /**
     * Registers saved metamodel snapshots once per target resource set, including nested packages.
     * Existing namespace registrations win; otherwise packages owned by {@code ownerId} take precedence.
     * A null owner gives no project priority, and ambiguous namespaces remain unregistered.
     * The target must be a {@link ResourceSetImpl} when a new namespace is encountered.
     */
    @Transactional(readOnly = true)
    public void initializePackages(ResourceSet targetResourceSet, String ownerId) {
        if (targetResourceSet.eAdapters().stream().anyMatch(PackagesInitialized.class::isInstance)) {
            return;
        }
        // shortcut: Refresh snapshots on context load; structural instance migration requires an explicit versioning workflow.
        var snapshotResourceSet = new ResourceSetImpl();
        snapshotResourceSet.getPackageRegistry().putAll(this.globalPackageRegistry);
        var ownerSnapshotResources = this.loadMetamodelSnapshots(snapshotResourceSet, ownerId);

        var packagesByNamespace = new LinkedHashMap<String, List<EPackage>>();
        var snapshotContents = snapshotResourceSet.getAllContents();
        while (snapshotContents.hasNext()) {
            if (snapshotContents.next() instanceof EPackage ePackage && ePackage.getNsURI() != null && !ePackage.getNsURI().isBlank()
                    && ePackage.getName() != null && !ePackage.getName().isBlank()) {
                packagesByNamespace.computeIfAbsent(ePackage.getNsURI(), namespaceUri -> new ArrayList<>()).add(ePackage);
            }
        }
        packagesByNamespace.forEach((namespaceUri, packages) ->
                this.registerNamespaceSnapshots(targetResourceSet, namespaceUri, packages, ownerSnapshotResources));
        targetResourceSet.eAdapters().add(new PackagesInitialized());
    }

    /** Loads all saved Ecore documents into the snapshot set and returns those owned by the preferred project. */
    private List<Resource> loadMetamodelSnapshots(ResourceSet snapshotResourceSet, String ownerId) {
        var ownerSnapshotResources = new ArrayList<Resource>();
        for (var semanticData : this.semanticDataSearchService.findAllByDomains(List.of(EcorePackage.eNS_URI))) {
            for (var document : semanticData.getDocuments()) {
                var rootElements = JsonParser.parseString(document.getContent()).getAsJsonObject().getAsJsonArray("content");
                boolean containsEPackage = rootElements != null && rootElements.asList().stream().anyMatch(root ->
                        root.isJsonObject() && root.getAsJsonObject().has("eClass")
                                && "ecore:EPackage".equals(root.getAsJsonObject().get("eClass").getAsString()));
                if (containsEPackage) {
                    this.metamodelLoader.toResource(snapshotResourceSet, document.getId().toString(), document.getName(), document.getContent(), false, true)
                            .filter(resource -> semanticData.getId().toString().equals(ownerId)).ifPresent(ownerSnapshotResources::add);
                }
            }
        }
        return ownerSnapshotResources;
    }

    private void registerNamespaceSnapshots(ResourceSet targetResourceSet, String namespaceUri,
            List<EPackage> packages, List<Resource> ownerSnapshotResources) {
        var packageRegistry = targetResourceSet.getPackageRegistry();
        if (packageRegistry.containsKey(namespaceUri)) {
            return;
        }
        // Native JSON pins schemas by document URI, including when several models share a namespace.
        var targetResources = (ResourceSetImpl) targetResourceSet;
        if (targetResources.getURIResourceMap() == null) {
            targetResources.setURIResourceMap(new HashMap<>());
        }
        packages.forEach(ePackage -> {
            ePackage.eAdapters().add(new MetamodelSnapshot());
            targetResources.getURIResourceMap().put(ePackage.eResource().getURI(), ePackage.eResource());
        });
        var ownerPackages = packages.stream().filter(ePackage -> ownerSnapshotResources.contains(ePackage.eResource())).toList();
        var preferredPackages = ownerPackages.isEmpty() ? packages : ownerPackages;
        if (preferredPackages.size() == 1) {
            packageRegistry.put(namespaceUri, preferredPackages.getFirst());
        } else {
            LoggerFactory.getLogger(EcoreInstanceSupport.class).warn("Ecore namespace {} is defined more than once; use unique namespace URIs", namespaceUri);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void postProcess(IEditingContext editingContext) {
        if (!(editingContext instanceof IEMFEditingContext emfEditingContext)) {
            return;
        }
        var resourceSet = (ResourceSetImpl) emfEditingContext.getDomain().getResourceSet();
        // Once documents are loaded, URI lookups must prefer this project's editable objects.
        if (resourceSet.getURIResourceMap() != null) {
            resourceSet.getResources().forEach(resource -> resourceSet.getURIResourceMap().remove(resource.getURI()));
        }
        // Refuse a partial load so a missing metamodel cannot silently remove a saved instance document.
        this.semanticDataSearchService.findById(UUID.fromString(editingContext.getId())).ifPresent(data -> {
            for (var document : data.getDocuments()) {
                boolean documentLoaded = resourceSet.getResources().stream()
                        .anyMatch(resource -> ("/" + document.getId()).equals(resource.getURI().path()));
                if (!documentLoaded) {
                    throw new IllegalStateException("Cannot load document " + document.getName()
                            + "; saved data have been preserved. Check that its metamodel exists and has a unique namespace URI.");
                }
            }
        });
    }

    private List<EPackage> getMetamodelSnapshots(IEditingContext editingContext) {
        if (!(editingContext instanceof IEMFEditingContext emfEditingContext)) {
            return List.of();
        }
        return emfEditingContext.getDomain().getResourceSet().getPackageRegistry().values().stream()
                .filter(EPackage.class::isInstance).map(EPackage.class::cast)
                .filter(ePackage -> ePackage.eAdapters().stream().anyMatch(MetamodelSnapshot.class::isInstance)).toList();
    }

    @Override
    public List<Stereotype> getStereotypes(IEditingContext editingContext) {
        if (!(editingContext instanceof IEMFEditingContext)) {
            return List.of();
        }
        var stereotypes = new ArrayList<Stereotype>();
        stereotypes.add(new Stereotype(ECORE, "Ecore model"));
        var packages = this.getMetamodelSnapshots(editingContext);
        var nameCounts = new HashMap<String, Integer>();
        packages.forEach(ePackage -> nameCounts.merge(ePackage.getName(), 1, Integer::sum));
        packages.forEach(ePackage -> stereotypes.add(new Stereotype(INSTANCE + ePackage.getNsURI(), nameCounts.get(ePackage.getName()) > 1
                ? ePackage.getName() + " (" + ePackage.getNsURI() + ")" : ePackage.getName())));
        return stereotypes;
    }

    @Override
    public boolean canHandle(IEditingContext editingContext, String stereotypeId) {
        return ECORE.equals(stereotypeId) || (stereotypeId != null && stereotypeId.startsWith(INSTANCE));
    }

    @Override
    public Optional<DocumentDTO> handle(IEditingContext editingContext, String stereotypeId, String name) {
        if (!(editingContext instanceof IEMFEditingContext emfEditingContext) || name == null || name.isBlank()) {
            return Optional.empty();
        }
        boolean knownInstanceStereotype = this.getMetamodelSnapshots(editingContext).stream()
                .anyMatch(ePackage -> (INSTANCE + ePackage.getNsURI()).equals(stereotypeId));
        if (!ECORE.equals(stereotypeId) && !knownInstanceStereotype) {
            return Optional.empty();
        }
        var documentId = UUID.randomUUID();
        var factory = new JSONResourceFactory();
        Resource resource;
        String documentName = name;
        if (ECORE.equals(stereotypeId)) {
            resource = new EcoreJsonResource(factory.createResourceURI(documentId.toString()));
            if (!documentName.endsWith(".ecore")) {
                documentName += ".ecore";
            }
            var ePackage = EcoreFactory.eINSTANCE.createEPackage();
            ePackage.setName("model");
            ePackage.setNsPrefix("model");
            ePackage.setNsURI("urn:ecore-editor:" + documentId);
            resource.getContents().add(ePackage);
        } else {
            resource = factory.createResourceFromPath(documentId.toString());
        }
        resource.eAdapters().add(new ResourceMetadataAdapter(documentName));
        emfEditingContext.getDomain().getResourceSet().getResources().add(resource);
        return Optional.of(new DocumentDTO(documentId, documentName, ExplorerDescriptionProvider.DOCUMENT_KIND));
    }

    private static final class MetamodelSnapshot extends AdapterImpl { }
    private static final class PackagesInitialized extends AdapterImpl { }
}
