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

import java.util.Objects;

import org.eclipse.sirius.components.ecore.starter.configuration.EcorePackageQueryUtils;
import org.eclipse.sirius.components.ecore.starter.view.EcoreViewBuilder;
import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.builder.providers.IEdgeDescriptionProvider;
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
public class BiDirectionalEReferenceDescriptionProvider implements IEdgeDescriptionProvider {

    public static final String NAME = "Bi-Directional EReference Edge";

    private final IColorProvider colorProvider;

    private final DiagramBuilders diagramBuilderHelper = new DiagramBuilders();

    private final EcoreViewBuilder ecoreViewBuilder = new EcoreViewBuilder();

    private final SynchronizationPolicy synchronizationPolicy;

    public BiDirectionalEReferenceDescriptionProvider(IColorProvider colorProvider, SynchronizationPolicy synchronizationPolicy) {
        this.colorProvider = Objects.requireNonNull(colorProvider);
        this.synchronizationPolicy = Objects.requireNonNull(synchronizationPolicy);
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
                .conditionalStyles(this.diagramBuilderHelper.newConditionalEdgeStyle()
                        .condition("feature:containment")
                        .lineStyle(LineStyle.SOLID)
                        .color(this.colorProvider.getColor("Ecore_Black"))
                        .sourceArrowStyle(ArrowStyle.FILL_DIAMOND)
                        .targetArrowStyle(ArrowStyle.INPUT_ARROW)
                        .build())
                .conditionalStyles(this.diagramBuilderHelper.newConditionalEdgeStyle()
                        .condition("feature:container")
                        .lineStyle(LineStyle.SOLID)
                        .color(this.colorProvider.getColor("Ecore_Black"))
                        .sourceArrowStyle(ArrowStyle.INPUT_ARROW)
                        .targetArrowStyle(ArrowStyle.FILL_DIAMOND)
                        .build())
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
