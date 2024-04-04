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
import org.eclipse.sirius.components.view.builder.providers.INodeDescriptionProvider;
import org.eclipse.sirius.components.view.diagram.DiagramDescription;
import org.eclipse.sirius.components.view.diagram.DiagramFactory;
import org.eclipse.sirius.components.view.diagram.IconLabelNodeStyleDescription;
import org.eclipse.sirius.components.view.diagram.NodeDescription;
import org.eclipse.sirius.components.view.diagram.NodePalette;
import org.eclipse.sirius.components.view.diagram.SynchronizationPolicy;

/**
 * Used to create the EEnumLiteral node description.
 *
 * @author adieumegard
 */
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class EEnumLiteralDescriptionProvider implements INodeDescriptionProvider {

    public static final String NAME = "EEnumLiteral Node";

    private final IColorProvider colorProvider;

    private final DiagramBuilders diagramBuilderHelper = new DiagramBuilders();

    private final EcoreViewBuilder ecoreViewBuilder = new EcoreViewBuilder();

    private final SynchronizationPolicy synchronizationPolicy;

    public EEnumLiteralDescriptionProvider(IColorProvider colorProvider, SynchronizationPolicy synchronizationPolicy) {
        this.colorProvider = Objects.requireNonNull(colorProvider);
        this.synchronizationPolicy = Objects.requireNonNull(synchronizationPolicy);
    }

    @Override
    public NodeDescription create() {

        return this.diagramBuilderHelper.newNodeDescription()
                .name(NAME)
                .domainType(EcorePackageQueryUtils.getEEnumLiteralDataTypeQualifiedName())
                .semanticCandidatesExpression(EcorePackageQueryUtils.getEEnumLiteralContainmentFeatureName(true))
                .childrenLayoutStrategy(DiagramFactory.eINSTANCE.createFreeFormLayoutStrategyDescription())
                .labelExpression("aql:self.name")
                .defaultHeightExpression("aql:100")
                .defaultWidthExpression("aql:120")
                .keepAspectRatio(true)
                .style(this.getRectangularNodeStyleDescription("Ecore_Gray", "Ecore_Black", "Ecore_Black"))
                .synchronizationPolicy(this.synchronizationPolicy)
                .build();
    }

    @Override
    public void link(DiagramDescription diagramDescription, IViewDiagramElementFinder cache) {
        cache.getNodeDescription(NAME).ifPresent(nodeDescription -> {
            nodeDescription.setPalette(this.createNodePalette());
            diagramDescription.getNodeDescriptions().add(nodeDescription);
        });
    }

    private NodePalette createNodePalette() {
        return this.diagramBuilderHelper.newNodePalette()
                .deleteTool(this.ecoreViewBuilder.createDeleteTool())
                .labelEditTool(this.ecoreViewBuilder.createLabelEditNameNoCamelCaseTool())
                .build();
    }

    private IconLabelNodeStyleDescription getRectangularNodeStyleDescription(String borderColor, String labelColor, String backgroundColor) {
        return this.diagramBuilderHelper.newIconLabelNodeStyleDescription()
                .color(this.colorProvider.getColor(backgroundColor))
                .borderColor(this.colorProvider.getColor(borderColor))
                .labelColor(this.colorProvider.getColor(labelColor))
                .bold(false)
                .showIcon(true)
                .build();
    }

}
