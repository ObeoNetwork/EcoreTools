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

import static org.eclipse.sirius.components.ecore.starter.configuration.EcoreRepresentationDescriptionProvider.ENTITIES_IN_A_CLASS_DIAGRAM_VIEW_DIAGRAM_ID;

import java.util.List;

import org.eclipse.sirius.components.ecore.starter.configuration.EcorePackageQueryUtils;
import org.eclipse.sirius.components.ecore.starter.view.descriptions.BiDirectionalEReferenceDescriptionProvider;
import org.eclipse.sirius.components.ecore.starter.view.descriptions.EClassDescriptionProvider;
import org.eclipse.sirius.components.ecore.starter.view.descriptions.EDataTypeDescriptionProvider;
import org.eclipse.sirius.components.ecore.starter.view.descriptions.EEnumDescriptionProvider;
import org.eclipse.sirius.components.ecore.starter.view.descriptions.EPackageDescriptionProvider;
import org.eclipse.sirius.components.ecore.starter.view.descriptions.EReferenceDescriptionProvider;
import org.eclipse.sirius.components.ecore.starter.view.descriptions.ESuperTypesDescriptionProvider;
import org.eclipse.sirius.components.view.RepresentationDescription;
import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.builder.providers.IDiagramElementDescriptionProvider;
import org.eclipse.sirius.components.view.builder.providers.IRepresentationDescriptionProvider;
import org.eclipse.sirius.components.view.diagram.DiagramPalette;
import org.eclipse.sirius.components.view.diagram.DiagramToolSection;
import org.eclipse.sirius.components.view.diagram.SynchronizationPolicy;

/**
 * Used to create Ecore diagram view.
 *
 * @author adieumegard
 */
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class EntitiesInAClassDiagramSynchronizedViewDiagramDescriptionProvider implements IRepresentationDescriptionProvider {

    private final DiagramBuilders diagramBuilderHelper = new DiagramBuilders();

    private final EcoreViewBuilder viewBuilder = new EcoreViewBuilder();

    @Override
    public RepresentationDescription create(IColorProvider colorProvider) {
        var diagramDescriptionBuilder = this.diagramBuilderHelper.newDiagramDescription();
        var diagramDescription = diagramDescriptionBuilder
                .autoLayout(true)
                .domainType(EcorePackageQueryUtils.getEPackageDataTypeQualifiedName())
                .name(ENTITIES_IN_A_CLASS_DIAGRAM_VIEW_DIAGRAM_ID)
                .titleExpression("Ecore model synchronized").build();


        var cache = new ViewDiagramElementFinder();
        var diagramElementDescriptionProviders = List.of(
                new EPackageDescriptionProvider(colorProvider, SynchronizationPolicy.SYNCHRONIZED),
                new EClassDescriptionProvider(colorProvider, SynchronizationPolicy.SYNCHRONIZED),
                new EEnumDescriptionProvider(colorProvider, SynchronizationPolicy.SYNCHRONIZED),
                new EDataTypeDescriptionProvider(colorProvider, SynchronizationPolicy.SYNCHRONIZED),
                new BiDirectionalEReferenceDescriptionProvider(colorProvider, SynchronizationPolicy.SYNCHRONIZED),
                new EReferenceDescriptionProvider(colorProvider, SynchronizationPolicy.SYNCHRONIZED),
                new ESuperTypesDescriptionProvider(colorProvider, SynchronizationPolicy.SYNCHRONIZED)
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
                .nodeTools(this.viewBuilder.createNodeToolCreateEPackage(),
                        this.viewBuilder.createNodeToolCreateEClass(),
                        this.viewBuilder.createNodeToolCreateEClassInterface(),
                        this.viewBuilder.createNodeToolCreateEClassAbstract(),
                        this.viewBuilder.createNodeToolCreateEEnum(),
                        this.viewBuilder.createNodeToolCreateEDataType())
                .build();
    }

}
