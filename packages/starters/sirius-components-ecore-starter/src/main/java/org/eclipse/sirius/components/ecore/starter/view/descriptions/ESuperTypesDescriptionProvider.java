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

import org.eclipse.emf.ecore.EcorePackage;
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
 * Used to create the EAttribute node description.
 *
 * @author adieumegard
 */
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class ESuperTypesDescriptionProvider implements IEdgeDescriptionProvider {

    public static final String NAME = "ESuperTypes Edge";

    private final IColorProvider colorProvider;

    private final DiagramBuilders diagramBuilderHelper = new DiagramBuilders();

    private final EcoreViewBuilder ecoreViewBuilder = new EcoreViewBuilder();

    private final SynchronizationPolicy synchronizationPolicy;

    public ESuperTypesDescriptionProvider(IColorProvider colorProvider, SynchronizationPolicy synchronizationPolicy) {
        this.colorProvider = Objects.requireNonNull(colorProvider);
        this.synchronizationPolicy = Objects.requireNonNull(synchronizationPolicy);
    }

    @Override
    public EdgeDescription create() {
        EdgeDescription edgeDescription = this.diagramBuilderHelper.newEdgeDescription()
                .name(NAME)
                .domainType(EcorePackageQueryUtils.getEClassDataTypeQualifiedName())
                .targetNodesExpression("aql:self.getDirectSuperTypesOrMostSpecificVisibleOnes(previousDiagram, cache)")
                .isDomainBasedEdge(false)
                .labelExpression("aql:self.superTypesLabel()")
                .style(this.diagramBuilderHelper.newEdgeStyle()
                        .lineStyle(LineStyle.SOLID)
                        .color(this.colorProvider.getColor("Ecore_Gray"))
                        .targetArrowStyle(ArrowStyle.INPUT_CLOSED_ARROW)
                        .build())
                .conditionalStyles(this.diagramBuilderHelper.newConditionalEdgeStyle()
                        .condition("aql:semanticEdgeTarget.interface")
                        .lineStyle(LineStyle.DASH)
                        .color(this.colorProvider.getColor("Ecore_Gray"))
                        .targetArrowStyle(ArrowStyle.INPUT_CLOSED_ARROW)
                        .build())
                .conditionalStyles(this.diagramBuilderHelper.newConditionalEdgeStyle()
                        .condition("aql:not self.eSuperTypes->includes(semanticEdgeTarget)")
                        .lineStyle(LineStyle.DOT)
                        .color(this.colorProvider.getColor("Ecore_Gray"))
                        .targetArrowStyle(ArrowStyle.INPUT_CLOSED_ARROW)
                        .build())
                .synchronizationPolicy(this.synchronizationPolicy)
                .build();

        return edgeDescription;
    }

    @Override
    public void link(DiagramDescription diagramDescription, IViewDiagramElementFinder cache) {
        var optionalESuperTypeEdgeDescription = cache.getEdgeDescription(NAME);
        var optionalEClassNodeDescription = cache.getNodeDescription(EClassDescriptionProvider.NAME);

        if (optionalESuperTypeEdgeDescription.isPresent() && optionalEClassNodeDescription.isPresent()) {
            diagramDescription.getEdgeDescriptions().add(optionalESuperTypeEdgeDescription.get());
            optionalESuperTypeEdgeDescription.get().setPalette(this.createEdgePalette());
            optionalESuperTypeEdgeDescription.get().getSourceNodeDescriptions().add(optionalEClassNodeDescription.get());
            optionalESuperTypeEdgeDescription.get().getTargetNodeDescriptions().add(optionalEClassNodeDescription.get());
        }
    }

    private EdgePalette createEdgePalette() {
        return this.diagramBuilderHelper.newEdgePalette()
                .deleteTool(this.ecoreViewBuilder.createDeleteToolDeleteEdgeReference(EcorePackage.Literals.ECLASS__ESUPER_TYPES))
                .build();
    }

}
