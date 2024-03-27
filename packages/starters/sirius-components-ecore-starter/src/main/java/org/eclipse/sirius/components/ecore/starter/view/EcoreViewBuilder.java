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

import org.eclipse.sirius.components.ecore.starter.configuration.EcorePackageQueryUtils;
import org.eclipse.sirius.components.view.builder.generated.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.generated.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.diagram.DeleteTool;
import org.eclipse.sirius.components.view.diagram.ImageNodeStyleDescription;
import org.eclipse.sirius.components.view.diagram.LabelEditTool;
import org.eclipse.sirius.components.view.diagram.NodeTool;

/**
 * Used to help creating the Ecore views.
 *
 * @author adieumegard
 */
public class EcoreViewBuilder {

    /**
     *
     */
    private static final String NAME = "name";
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
                .featureName(NAME)
                .valueExpression("aql:self.toCamelCase(newLabel)");

        return this.diagramBuilderHelper.newLabelEditTool()
                .name("Edit Name with Camel Case")
                .body(setValueService.build())
                .build();
    }

    public LabelEditTool createLabelEditNameNoCamelCaseTool() {
        var setValueService = this.viewBuilderHelper.newSetValue()
                .featureName(NAME)
                .valueExpression("aql:newLabel");

        return this.diagramBuilderHelper.newLabelEditTool()
                .name("Edit Name no Camel Case")
                .body(setValueService.build())
                .build();
    }

    public LabelEditTool createLabelEditInstanceClassNameTool() {
        var setValueService = this.viewBuilderHelper.newSetValue()
                .featureName(EcorePackageQueryUtils.getEClassifierInstanceClassNameFeatureName(false))
                .valueExpression("aql:newLabel");

        return this.diagramBuilderHelper.newLabelEditTool()
                .name("Edit Name Instance Direct Edit")
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

    public NodeTool createNodeToolCreateEEnumLiteral() {

        var setValueName = this.viewBuilderHelper.newSetValue()
                .featureName(NAME)
                .valueExpression("aql:'EEnumLiteral' + self.eContainer().eContents()->filter(ecore::EEnumLiteral)->size()");

        var changeContextNewInstance = this.viewBuilderHelper.newChangeContext()
                .expression("aql:newInstance")
                .children(setValueName.build());

        var createInstance = this.viewBuilderHelper.newCreateInstance()
                .typeName(EcorePackageQueryUtils.getEEnumLiteralDataTypeQualifiedName())
                .referenceName(EcorePackageQueryUtils.getEEnumLiteralContainmentFeatureName(false))
                .variableName("newInstance")
                .children(changeContextNewInstance.build());

        return this.diagramBuilderHelper.newNodeTool()
                .name("EEnumLiteral")
                .iconURLsExpression("/icons/full/obj16/EEnumLiteral.gif")
                .body(createInstance.build())
                .build();
    }

}
