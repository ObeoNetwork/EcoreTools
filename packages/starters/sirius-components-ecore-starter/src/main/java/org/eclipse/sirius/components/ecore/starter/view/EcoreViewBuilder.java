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

import org.eclipse.sirius.components.view.builder.generated.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.generated.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.diagram.DeleteTool;
import org.eclipse.sirius.components.view.diagram.ImageNodeStyleDescription;
import org.eclipse.sirius.components.view.diagram.LabelEditTool;

/**
 * Used to help creating the Ecore views.
 *
 * @author adieumegard
 */
public class EcoreViewBuilder {

    public static final String EPACKAGE_SVG_ID = "c643d05b-0ae8-3695-85f2-121c865a5e38";
    public static final String ECLASS_SVG_ID = "693c6af9-2f0e-3deb-8681-ad521fbaea2a";

    private final ViewBuilders viewBuilderHelper = new ViewBuilders();
    private final DiagramBuilders diagramBuilderHelper = new DiagramBuilders();

    public ImageNodeStyleDescription createImageNodeStyleDescription(String shapeId, IColorProvider colorProvider) {
        return this.diagramBuilderHelper.newImageNodeStyleDescription()
                .shape(shapeId)
                .color(colorProvider.getColor("transparent"))
                .borderColor(colorProvider.getColor("transparent"))
                .labelColor(colorProvider.getColor("Ecore_Black"))
                .build();
    }

    public DeleteTool createDeleteTool() {
        var changeContext = this.viewBuilderHelper.newChangeContext()
                .expression("aql:self.defaultDelete()");

        return this.diagramBuilderHelper.newDeleteTool()
                .name("Delete from Model")
                .body(changeContext.build())
                .build();
    }

    public LabelEditTool createLabelEditTool() {
        var changeContext = this.viewBuilderHelper.newChangeContext()
                .expression("aql:self.defaultEditLabel(newLabel)");

        return this.diagramBuilderHelper.newLabelEditTool()
                .name("Edit")
                .body(changeContext.build())
                .build();
    }

    public LabelEditTool createLabelEditNameWithCamelCaseTool() {
        var setValueService = this.viewBuilderHelper.newSetValue()
                .featureName("name")
                .valueExpression("aql:self.toCamelCase(newLabel)");

        return this.diagramBuilderHelper.newLabelEditTool()
                .name("Edit Name with Camel Case")
                .body(setValueService.build())
                .build();
    }

    public LabelEditTool createLabelEditEStructuralFeatureNameTool() {
        var changeContext = this.viewBuilderHelper.newChangeContext()
                .expression("aql:self.performEditAsAttribute(newLabel)");

        return this.diagramBuilderHelper.newLabelEditTool()
                .name("Edit EStructuralFeature Name")
                .body(changeContext.build())
                .build();
    }

    public LabelEditTool createLabelEditEReferenceOrEOperationNameTool() {
        var changeContext = this.viewBuilderHelper.newChangeContext()
                .expression("aql:self.performEdit(newLabel)");

        return this.diagramBuilderHelper.newLabelEditTool()
                .name("Edit EReference or EOperation Name")
                .body(changeContext.build())
                .build();
    }

}
