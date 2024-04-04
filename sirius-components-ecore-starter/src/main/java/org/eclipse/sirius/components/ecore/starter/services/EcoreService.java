/*******************************************************************************
 * Copyright (c) 2024 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.components.ecore.starter.services;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.sirius.components.collaborative.diagrams.api.IDiagramContext;
import org.eclipse.sirius.components.core.api.IEditService;
import org.eclipse.sirius.components.core.api.IObjectService;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.components.diagrams.ViewCreationRequest;
import org.eclipse.sirius.components.diagrams.components.NodeContainmentKind;
import org.eclipse.sirius.components.view.diagram.NodeDescription;

/**
 * Java Service for the Ecore view.
 *
 * @author adieumegard
 */
public class EcoreService {

    private final IObjectService objectService;

    private final IEditService editService;

    public EcoreService(IObjectService objectService, IEditService editService) {
        this.objectService = Objects.requireNonNull(objectService);
        this.editService = Objects.requireNonNull(editService);
    }

    /**
     * Delete all EReference whose eType is {@code eclass}.
     *
     * @param eclass
     *            The eclass to match with
     * @return
     */
    public EObject deleteReferencingEReferences(EObject eclass) {
        TreeIterator<EObject> treeIterator = eclass.eResource().getAllContents();
        while (treeIterator.hasNext()) {
            EObject obj = treeIterator.next();
            if (obj instanceof EReference) {
                EReference ref = (EReference) obj;
                EClassifier eType = ref.getEType();
                if (eType != null && eType.equals(eclass)) {
                    this.editService.delete(ref);
                }
            }
        }
        return eclass;
    }

    public EObject drop(EObject self, Node selectedNode, IDiagramContext diagramContext, Map<NodeDescription, org.eclipse.sirius.components.diagrams.description.NodeDescription> convertedNodes) {
        var parentElementId = Optional.ofNullable(selectedNode).map(Node::getId).orElse(diagramContext.getDiagram().getId());

        var targetObjectId = this.objectService.getId(self);

        String nodeDescriptionName = self.eClass().getName() + " Node";

        var descriptionId = convertedNodes.entrySet().stream().filter(entry -> entry.getKey().getName().equals(nodeDescriptionName)).findFirst().map(entry -> entry.getValue().getId()).orElse(null);

        if (parentElementId != null && targetObjectId != null && descriptionId != null) {
            var viewCreationRequest = ViewCreationRequest.newViewCreationRequest().parentElementId(parentElementId).targetObjectId(targetObjectId).descriptionId(descriptionId)
                    .containmentKind(NodeContainmentKind.CHILD_NODE).build();
            diagramContext.getViewCreationRequests().add(viewCreationRequest);
        }

        return self;
    }

}
