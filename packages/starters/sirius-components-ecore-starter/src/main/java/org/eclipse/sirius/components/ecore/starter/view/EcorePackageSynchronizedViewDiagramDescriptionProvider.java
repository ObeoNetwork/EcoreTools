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
package org.eclipse.sirius.components.ecore.starter.view;

import static org.eclipse.sirius.components.ecore.starter.configuration.EcoreRepresentationDescriptionProvider.ECORE_VIEW_DIAGRAM_ID;

import java.util.List;

import org.eclipse.sirius.components.ecore.starter.view.descriptions.EClassDescriptionProvider;
import org.eclipse.sirius.components.ecore.starter.view.descriptions.EPackageDescriptionProvider;
import org.eclipse.sirius.components.view.RepresentationDescription;
import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.generated.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.builder.providers.IDiagramElementDescriptionProvider;
import org.eclipse.sirius.components.view.builder.providers.IRepresentationDescriptionProvider;
import org.eclipse.sirius.components.view.diagram.DiagramPalette;
import org.eclipse.sirius.components.view.diagram.DiagramToolSection;
import org.eclipse.sirius.components.view.diagram.NodeTool;
import org.eclipse.sirius.components.view.diagram.SynchronizationPolicy;

/**
 * Used to create Ecore diagram view.
 *
 * @author adieumegard
 */
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class EcorePackageSynchronizedViewDiagramDescriptionProvider implements IRepresentationDescriptionProvider {

    private final DiagramBuilders diagramBuilderHelper = new DiagramBuilders();

    private final ViewBuilders viewBuilderHelper = new ViewBuilders();

    @Override
    public RepresentationDescription create(IColorProvider colorProvider) {
        var diagramDescriptionBuilder = this.diagramBuilderHelper.newDiagramDescription();
        var diagramDescription = diagramDescriptionBuilder
                .autoLayout(true)
                .domainType("ecore::Package")
                .name(ECORE_VIEW_DIAGRAM_ID)
                .titleExpression("Ecore model synchronized").build();


        var cache = new ViewDiagramElementFinder();
        var diagramElementDescriptionProviders = List.of(
                new EPackageDescriptionProvider(colorProvider, SynchronizationPolicy.SYNCHRONIZED),
                new EClassDescriptionProvider(colorProvider, SynchronizationPolicy.SYNCHRONIZED)
        );

        diagramElementDescriptionProviders.stream().map(IDiagramElementDescriptionProvider::create).forEach(cache::put);

        diagramElementDescriptionProviders.forEach(diagramElementDescriptionProvider -> diagramElementDescriptionProvider.link(diagramDescription, cache));

        var palette = this.createDiagramPalette(cache);
        diagramDescription.setPalette(palette);

        return diagramDescription;
    }

    private DiagramPalette createDiagramPalette(IViewDiagramElementFinder cache) {
        return this.diagramBuilderHelper.newDiagramPalette()
                .toolSections(this.createDiagramToolSection(cache))
                .build();
    }

    private DiagramToolSection createDiagramToolSection(IViewDiagramElementFinder cache) {
        return this.diagramBuilderHelper.newDiagramToolSection()
                .name("Creation Tools")
                .nodeTools(this.createNodeToolCreateEPackage(cache),
                        this.createNodeToolCreateEClass(cache))
                .build();
    }

    private NodeTool createNodeToolCreateEPackage(IViewDiagramElementFinder cache) {

        var setValueName = this.viewBuilderHelper.newSetValue()
                .featureName("name")
                .valueExpression("aql:'EPackage' + self.eContainer().eContents()->filter(ecore::EPackage)->size()");

        var changeContextNewInstance = this.viewBuilderHelper.newChangeContext()
                .expression("aql:newInstance")
                .children(setValueName.build());

        var createInstance = this.viewBuilderHelper.newCreateInstance()
                .typeName("ecore::EPackage")
                .referenceName("eSubpackages")
                .variableName("newInstance")
                .children(changeContextNewInstance.build());

        return this.diagramBuilderHelper.newNodeTool()
                .name("EPackage")
                .iconURLsExpression("/icons/full/obj16/EPackage.gif")
                .body(createInstance.build())
                .build();
    }

    private NodeTool createNodeToolCreateEClass(IViewDiagramElementFinder cache) {

        var setValueName = this.viewBuilderHelper.newSetValue()
                .featureName("name")
                .valueExpression("aql:'EClass' + self.eContainer().eContents()->filter(ecore::EClass)->size()");

        var changeContextNewInstance = this.viewBuilderHelper.newChangeContext()
                .expression("aql:newInstance")
                .children(setValueName.build());

        var createInstance = this.viewBuilderHelper.newCreateInstance()
                .typeName("ecore::EClass")
                .referenceName("eClassifiers")
                .variableName("newInstance")
                .children(changeContextNewInstance.build());

        return this.diagramBuilderHelper.newNodeTool()
                .name("EClass")
                .iconURLsExpression("/icons/full/obj16/EClass.gif")
                .body(createInstance.build())
                .build();
    }

}
