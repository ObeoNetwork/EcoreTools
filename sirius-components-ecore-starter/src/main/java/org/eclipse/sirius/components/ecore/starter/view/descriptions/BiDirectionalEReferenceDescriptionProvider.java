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
package org.eclipse.sirius.components.ecore.starter.view.descriptions;

import org.eclipse.sirius.components.ecore.starter.configuration.EcorePackageQueryUtils;
import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.diagram.ArrowStyle;
import org.eclipse.sirius.components.view.diagram.DiagramDescription;
import org.eclipse.sirius.components.view.diagram.EdgeDescription;
import org.eclipse.sirius.components.view.diagram.EdgePalette;
import org.eclipse.sirius.components.view.diagram.LineStyle;
import org.eclipse.sirius.components.view.diagram.SynchronizationPolicy;

/**
 * Used to create the EReference edge description for EReferences with eOpposite.
 *
 * @author adieumegard
 */
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class BiDirectionalEReferenceDescriptionProvider extends AbstractEReferenceDescriptionProvider {

    public static final String NAME = "Bi-Directional EReference Edge";

    public BiDirectionalEReferenceDescriptionProvider(IColorProvider colorProvider, SynchronizationPolicy synchronizationPolicy) {
        super(colorProvider, synchronizationPolicy);
    }

    @Override
    public EdgeDescription create() {

        return this.diagramBuilderHelper.newEdgeDescription()
                .name(NAME)
                .domainType(EcorePackageQueryUtils.getEReferenceDataTypeQualifiedName())
                .semanticCandidatesExpression("aql:self.getEOppositeEReferences(previousDiagram, cache)")
                .targetNodesExpression("feature:eType")
                .sourceNodesExpression("feature:eContainer")
                .isDomainBasedEdge(true)
                .beginLabelExpression("aql:self.renderEOpposite()")
                .labelExpression("aql:''")
                .endLabelExpression("aql:self.render()")
                .style(this.diagramBuilderHelper.newEdgeStyle()
                        .lineStyle(LineStyle.SOLID)
                        .color(this.colorProvider.getColor("Ecore_Black"))
                        .targetArrowStyle(ArrowStyle.INPUT_ARROW)
                        .sourceArrowStyle(ArrowStyle.INPUT_ARROW)
                        .build())
                .conditionalStyles(this.conditionalEdgeStyle("aql:self.required", ArrowStyle.INPUT_ARROW, ArrowStyle.INPUT_ARROW, true))
                .conditionalStyles(this.conditionalEdgeStyle("aql:self.containment and not self.required", ArrowStyle.FILL_DIAMOND, ArrowStyle.INPUT_ARROW, false))
                .conditionalStyles(this.conditionalEdgeStyle("aql:self.container and not self.required", ArrowStyle.INPUT_ARROW, ArrowStyle.FILL_DIAMOND, false))
                .conditionalStyles(this.conditionalEdgeStyle("aql:self.containment and self.required", ArrowStyle.FILL_DIAMOND, ArrowStyle.INPUT_ARROW, true))
                .conditionalStyles(this.conditionalEdgeStyle("aql:self.container and self.required", ArrowStyle.INPUT_ARROW, ArrowStyle.FILL_DIAMOND, true))
                .synchronizationPolicy(this.synchronizationPolicy)
                .palette(this.createEdgePalette())
                .build();
    }

    @Override
    public void link(DiagramDescription diagramDescription, IViewDiagramElementFinder cache) {
        var optionalEReferenceEdgeDescription = cache.getEdgeDescription(NAME);
        var optionalEClassNodeDescription = cache.getNodeDescription(EClassDescriptionProvider.NAME);

        if (optionalEReferenceEdgeDescription.isPresent() && optionalEClassNodeDescription.isPresent()) {
            diagramDescription.getEdgeDescriptions().add(optionalEReferenceEdgeDescription.get());
            optionalEReferenceEdgeDescription.get().getSourceNodeDescriptions().add(optionalEClassNodeDescription.get());
            optionalEReferenceEdgeDescription.get().getTargetNodeDescriptions().add(optionalEClassNodeDescription.get());
        }
    }

    private EdgePalette createEdgePalette() {
        return this.diagramBuilderHelper.newEdgePalette()
                .deleteTool(this.ecoreViewBuilder.createDeleteBiDirectionalEReferenceTool())
                .endLabelEditTool(this.ecoreViewBuilder.createLabelEditEReferenceOrEOperationNameTool())
                .edgeReconnectionTools(this.ecoreViewBuilder.createEdgeReconnectBiDirectionalEReferenceSource())
                .edgeReconnectionTools(this.ecoreViewBuilder.createEdgeReconnectBiDirectionalEReferenceTarget())
                .build();
    }

}
