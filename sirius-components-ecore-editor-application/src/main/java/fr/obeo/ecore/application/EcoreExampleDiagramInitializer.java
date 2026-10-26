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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.sirius.components.collaborative.api.IRepresentationMetadataPersistenceService;
import org.eclipse.sirius.components.collaborative.api.IRepresentationPersistenceService;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.collaborative.diagrams.api.IDiagramCreationService;
import org.eclipse.sirius.components.core.RepresentationMetadata;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.core.api.IRepresentationDescriptionSearchService;
import org.eclipse.sirius.components.diagrams.ViewCreationRequest;
import org.eclipse.sirius.components.diagrams.components.NodeContainmentKind;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.view.View;
import org.eclipse.sirius.components.view.emf.diagram.IDiagramIdProvider;
import org.eclipse.sirius.components.representations.VariableManager;
import org.eclipse.sirius.web.domain.boundedcontexts.semanticdata.events.SemanticDataUpdatedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** Creates the saved example diagram once, using the same lifecycle as Sirius Web starter templates. */
@Service
public class EcoreExampleDiagramInitializer {
    private final IRepresentationDescriptionSearchService descriptionSearchService;
    private final IDiagramCreationService diagramCreationService;
    private final IRepresentationMetadataPersistenceService metadataPersistenceService;
    private final IRepresentationPersistenceService representationPersistenceService;
    private final IIdentityService identityService;
    private final String ecoreDiagramDescriptionId;
    private final Map<String, String> nodeDescriptionIdsByType;

    public EcoreExampleDiagramInitializer(IRepresentationDescriptionSearchService descriptionSearchService,
            IDiagramCreationService diagramCreationService, IRepresentationMetadataPersistenceService metadataPersistenceService,
            IRepresentationPersistenceService representationPersistenceService, IIdentityService identityService,
            @Qualifier("ecoreView") View view, IDiagramIdProvider diagramIdProvider) {
        this.descriptionSearchService = descriptionSearchService;
        this.diagramCreationService = diagramCreationService;
        this.metadataPersistenceService = metadataPersistenceService;
        this.representationPersistenceService = representationPersistenceService;
        this.identityService = identityService;
        var description = (org.eclipse.sirius.components.view.diagram.DiagramDescription) view.getDescriptions().getFirst();
        this.ecoreDiagramDescriptionId = diagramIdProvider.getId(description);
        this.nodeDescriptionIdsByType = description.getNodeDescriptions().stream()
                .collect(Collectors.toMap(node -> node.getName(), diagramIdProvider::getId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    public void onSemanticDataUpdatedEvent(SemanticDataUpdatedEvent event) {
        if (!(event.causedBy() instanceof EcoreProjectTemplateInitializer.ExampleInitialization initialization)) {
            return;
        }
        var editingContext = initialization.editingContext();
        var model = initialization.model();
        var description = this.descriptionSearchService.findById(editingContext, this.ecoreDiagramDescriptionId)
                .filter(DiagramDescription.class::isInstance).map(DiagramDescription.class::cast).orElseThrow();
        var diagram = this.diagramCreationService.create(editingContext, description, model);
        var diagramContext = new DiagramContext(diagram);
        var initialNodeCandidates = new java.util.ArrayList<org.eclipse.emf.ecore.EObject>(model.getEClassifiers());
        initialNodeCandidates.addAll(model.getESubpackages());
        var initiallyHiddenElementNames = List.of("C", "Thing");
        for (var element : initialNodeCandidates) {
            if (element instanceof org.eclipse.emf.ecore.ENamedElement named
                    && !initiallyHiddenElementNames.contains(named.getName())) {
                diagramContext.viewCreationRequests().add(ViewCreationRequest.newViewCreationRequest()
                        .parentElementId(diagram.getId()).descriptionId(this.nodeDescriptionIdsByType.get(element.eClass().getName()))
                        .targetObjectId(this.identityService.getId(element)).containmentKind(NodeContainmentKind.CHILD_NODE).build());
            }
        }

        diagram = this.diagramCreationService.refresh(editingContext, diagramContext).orElseThrow();
        // A second refresh synchronizes references to the newly created type-parameter ports.
        diagram = this.diagramCreationService.refresh(editingContext, new DiagramContext(diagram)).orElseThrow();
        diagram = new EcoreExampleLayout(this.identityService).apply(model, diagram);

        var variableManager = new VariableManager();
        variableManager.put(VariableManager.SELF, model);
        var representationMetadata = RepresentationMetadata.newRepresentationMetadata(diagram.getId())
                .kind(diagram.getKind()).label("Example").descriptionId(diagram.getDescriptionId())
                .iconURLs(description.getIconURLsProvider().apply(variableManager)).build();
        this.metadataPersistenceService.save(event, editingContext, representationMetadata, diagram.getTargetObjectId());
        this.representationPersistenceService.save(event, editingContext, diagram);
    }
}
