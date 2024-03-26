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
import org.eclipse.sirius.components.view.diagram.NodeDescription;
import org.eclipse.sirius.components.view.diagram.NodePalette;
import org.eclipse.sirius.components.view.diagram.RectangularNodeStyleDescription;
import org.eclipse.sirius.components.view.diagram.SynchronizationPolicy;

/**
 * Used to create the EClass node description.
 *
 * @author adieumegard
 */
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class EClassDescriptionProvider implements INodeDescriptionProvider {

    public static final String NAME = "EClass Node";

    private final IColorProvider colorProvider;

    private final DiagramBuilders diagramBuilderHelper = new DiagramBuilders();

    private final EcoreViewBuilder ecoreViewBuilder = new EcoreViewBuilder();

    private final SynchronizationPolicy synchronizationPolicy;

    public EClassDescriptionProvider(IColorProvider colorProvider, SynchronizationPolicy synchronizationPolicy) {
        this.colorProvider = Objects.requireNonNull(colorProvider);
        this.synchronizationPolicy = Objects.requireNonNull(synchronizationPolicy);
    }

    @Override
    public NodeDescription create() {

        return this.diagramBuilderHelper.newNodeDescription()
                .name(NAME)
                .domainType(EcorePackageQueryUtils.getEClassDataTypeQualifiedName())
                .semanticCandidatesExpression(EcorePackageQueryUtils.getEClassContainmentFeatureName(true))
                .childrenLayoutStrategy(DiagramFactory.eINSTANCE.createListLayoutStrategyDescription())
                .labelExpression("aql:self.name")
                .defaultHeightExpression("aql:100")
                .defaultWidthExpression("aql:120")
                .keepAspectRatio(false)
                .style(this.getRectangularNodeStyleDescription("Ecore_Gray", "Ecore_Black", "Ecore_EClass"))
                .conditionalStyles(this.diagramBuilderHelper.newConditionalNodeStyle()
                        .condition(EcorePackageQueryUtils.getEClassInterfaceFeatureName(true))
                        .style(this.getRectangularNodeStyleDescription("Ecore_Gray", "Ecore_Black", "Ecore_AbstractEClass"))
                        .build())
                .conditionalStyles(this.diagramBuilderHelper.newConditionalNodeStyle()
                        .condition(EcorePackageQueryUtils.getEClassAbstractFeatureName(true))
                        .style(this.getRectangularNodeStyleDescription("Ecore_Gray", "Ecore_Black", "Ecore_AbstractEClass"))
                        .build())
                .synchronizationPolicy(this.synchronizationPolicy)
                .palette(this.createNodePalette())
                .childrenDescriptions(new EAttributeDescriptionProvider(this.colorProvider, this.synchronizationPolicy).create())
                .childrenDescriptions(new EOperationDescriptionProvider(this.colorProvider, this.synchronizationPolicy).create())
                .build();
    }

    @Override
    public void link(DiagramDescription diagramDescription, IViewDiagramElementFinder cache) {
        cache.getNodeDescription(NAME).ifPresent(nodeDescription -> diagramDescription.getNodeDescriptions().add(nodeDescription));
    }

    private NodePalette createNodePalette() {
        return this.diagramBuilderHelper.newNodePalette()
                .deleteTool(this.ecoreViewBuilder.createDeleteTool())
                .labelEditTool(this.ecoreViewBuilder.createLabelEditNameWithCamelCaseTool())
                .build();
    }

    private RectangularNodeStyleDescription getRectangularNodeStyleDescription(String borderColor, String labelColor, String backgroundColor) {
        return this.diagramBuilderHelper.newRectangularNodeStyleDescription()
                .color(this.colorProvider.getColor(backgroundColor))
                .borderColor(this.colorProvider.getColor(borderColor))
                .labelColor(this.colorProvider.getColor(labelColor))
                .bold(false)
                .borderRadius(8)
                .withHeader(true)
                .displayHeaderSeparator(true)
                .showIcon(true)
                .build();
    }

}
