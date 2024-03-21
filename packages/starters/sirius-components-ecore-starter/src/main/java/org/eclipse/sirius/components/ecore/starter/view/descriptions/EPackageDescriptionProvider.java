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

import static org.eclipse.sirius.components.ecore.starter.view.EcoreViewBuilder.EPACKAGE_SVG_ID;

import java.util.Objects;

import org.eclipse.sirius.components.ecore.starter.view.EcoreViewBuilder;
import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.builder.providers.INodeDescriptionProvider;
import org.eclipse.sirius.components.view.diagram.DiagramDescription;
import org.eclipse.sirius.components.view.diagram.DiagramFactory;
import org.eclipse.sirius.components.view.diagram.NodeDescription;
import org.eclipse.sirius.components.view.diagram.NodePalette;
import org.eclipse.sirius.components.view.diagram.SynchronizationPolicy;

/**
 * Used to create the Package node description.
 *
 * @author adieumegard
 */
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class EPackageDescriptionProvider implements INodeDescriptionProvider {

    public static final String NAME = "EPackage Node";

    private final IColorProvider colorProvider;

    private final DiagramBuilders diagramBuilderHelper = new DiagramBuilders();

    private final EcoreViewBuilder ecoreViewBuilder = new EcoreViewBuilder();

    private final SynchronizationPolicy synchronizationPolicy;

    public EPackageDescriptionProvider(IColorProvider colorProvider, SynchronizationPolicy synchronizationPolicy) {
        this.colorProvider = Objects.requireNonNull(colorProvider);
        this.synchronizationPolicy = Objects.requireNonNull(synchronizationPolicy);
    }

    @Override
    public NodeDescription create() {

        return this.diagramBuilderHelper.newNodeDescription()
                .name(NAME)
                .domainType("ecore::EPackage")
                .semanticCandidatesExpression("feature:eSubpackages")
                .childrenLayoutStrategy(DiagramFactory.eINSTANCE.createFreeFormLayoutStrategyDescription())
                .labelExpression("aql:self.name")
                .defaultHeightExpression("aql:15")
                .defaultWidthExpression("aql:20")
                .keepAspectRatio(true)
                .style(this.ecoreViewBuilder.createImageNodeStyleDescription(EPACKAGE_SVG_ID, this.colorProvider))
                .synchronizationPolicy(this.synchronizationPolicy)
                .palette(this.createNodePalette())
                .build();
    }

    @Override
    public void link(DiagramDescription diagramDescription, IViewDiagramElementFinder cache) {
        cache.getNodeDescription(NAME).ifPresent(nodeDescription -> {
            diagramDescription.getNodeDescriptions().add(nodeDescription);
            cache.getNodeDescription(EClassDescriptionProvider.NAME).ifPresent(eclassNodeDescription -> nodeDescription.setPalette(this.createNodePalette()));
        });
    }

    private NodePalette createNodePalette() {

        return this.diagramBuilderHelper.newNodePalette()
                .deleteTool(this.ecoreViewBuilder.createDeleteTool())
                .labelEditTool(this.ecoreViewBuilder.createLabelEditTool())
                .build();
    }


}
