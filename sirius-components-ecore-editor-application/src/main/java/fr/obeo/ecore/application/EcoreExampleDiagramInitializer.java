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
    private final IRepresentationDescriptionSearchService descriptions;
    private final IDiagramCreationService diagrams;
    private final IRepresentationMetadataPersistenceService metadata;
    private final IRepresentationPersistenceService representations;
    private final IIdentityService identity;
    private final String descriptionId;
    private final Map<String, String> nodeDescriptions;

    public EcoreExampleDiagramInitializer(IRepresentationDescriptionSearchService descriptions,
            IDiagramCreationService diagrams, IRepresentationMetadataPersistenceService metadata,
            IRepresentationPersistenceService representations, IIdentityService identity,
            @Qualifier("ecoreView") View view, IDiagramIdProvider ids) {
        this.descriptions = descriptions;
        this.diagrams = diagrams;
        this.metadata = metadata;
        this.representations = representations;
        this.identity = identity;
        var description = (org.eclipse.sirius.components.view.diagram.DiagramDescription) view.getDescriptions().getFirst();
        this.descriptionId = ids.getId(description);
        this.nodeDescriptions = description.getNodeDescriptions().stream()
                .collect(Collectors.toMap(node -> node.getName(), ids::getId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    public void onSemanticDataUpdatedEvent(SemanticDataUpdatedEvent event) {
        if (event.causedBy() instanceof EcoreProjectTemplateInitializer.ExampleInitialization initialization) {
            var context = initialization.editingContext();
            var model = initialization.model();
            var description = this.descriptions.findById(context, this.descriptionId)
                    .filter(DiagramDescription.class::isInstance).map(DiagramDescription.class::cast).orElseThrow();
            var diagram = this.diagrams.create(context, description, model);
            var diagramContext = new DiagramContext(diagram);
            var contents = new java.util.ArrayList<org.eclipse.emf.ecore.EObject>(model.getEClassifiers());
            contents.addAll(model.getESubpackages());
            for (var element : contents) {
                if (element instanceof org.eclipse.emf.ecore.ENamedElement named
                        && !List.of("C", "Thing").contains(named.getName())) {
                    diagramContext.viewCreationRequests().add(ViewCreationRequest.newViewCreationRequest()
                            .parentElementId(diagram.getId()).descriptionId(this.nodeDescriptions.get(element.eClass().getName()))
                            .targetObjectId(this.identity.getId(element)).containmentKind(NodeContainmentKind.CHILD_NODE).build());
                }
            }
            diagram = this.diagrams.refresh(context, diagramContext).orElseThrow();
            // A second refresh synchronizes references to the newly created type-parameter ports.
            diagram = this.diagrams.refresh(context, new DiagramContext(diagram)).orElseThrow();
            diagram = new EcoreExampleLayout(this.identity).apply(model, diagram);
            var variables = new VariableManager();
            variables.put(VariableManager.SELF, model);
            var representationMetadata = RepresentationMetadata.newRepresentationMetadata(diagram.getId())
                    .kind(diagram.getKind()).label("Example").descriptionId(diagram.getDescriptionId())
                    .iconURLs(description.getIconURLsProvider().apply(variables)).build();
            this.metadata.save(event, context, representationMetadata, diagram.getTargetObjectId());
            this.representations.save(event, context, diagram);
        }
    }
}
