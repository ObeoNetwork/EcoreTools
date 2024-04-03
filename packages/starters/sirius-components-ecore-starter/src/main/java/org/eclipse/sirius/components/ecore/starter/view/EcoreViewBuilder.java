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

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.sirius.components.ecore.starter.configuration.EcorePackageQueryUtils;
import org.eclipse.sirius.components.view.builder.generated.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.generated.SetValueBuilder;
import org.eclipse.sirius.components.view.builder.generated.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.diagram.DeleteTool;
import org.eclipse.sirius.components.view.diagram.EdgeTool;
import org.eclipse.sirius.components.view.diagram.ImageNodeStyleDescription;
import org.eclipse.sirius.components.view.diagram.LabelEditTool;
import org.eclipse.sirius.components.view.diagram.NodeDescription;
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
    private static final String AQL_TRUE = "aql:true";
    private static final String NEW_INSTANCE = "newInstance";
    private static final String AQL_NEW_INSTANCE = "aql:newInstance";
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

    public DeleteTool createDeleteClassTool() {
        var changeContextReferencingERefs = this.viewBuilderHelper.newChangeContext()
                .expression("aql:self.deleteReferencingEReferences()");

        var changeContext = this.viewBuilderHelper.newChangeContext()
                .expression("aql:self.defaultDelete()");

        return this.diagramBuilderHelper.newDeleteTool()
                .name("Delete including referencing EReferences")
                .body(changeContextReferencingERefs.build(), changeContext.build())
                .build();
    }

    /**
     * Create a delete tool unreferencing edge target from edge source.
     *
     * @param ref
     *            The EReference to be navigated
     * @return
     */
    public DeleteTool createDeleteToolDeleteEdgeReference(EReference ref) {
        var unsetRef = this.viewBuilderHelper.newUnsetValue()
                .featureName(ref.getName())
                .elementExpression("aql:semanticEdgeTarget");

        var changeContext = this.viewBuilderHelper.newChangeContext()
                .expression("aql:semanticEdgeSource")
                .children(unsetRef.build());

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

    private NodeTool createNodeToolCreate(EObject object, EReference containmentRef, SetValueBuilder... valuesBuilders) {
        String name = object.eClass().getName();
        var setValueName = this.viewBuilderHelper.newSetValue()
                .featureName(NAME)
                .valueExpression("aql:'New" + name + "' + self.eContainer().eContents()->filter(ecore::" + name + ")->size()");

        var changeContextNewInstance = this.viewBuilderHelper.newChangeContext()
                .expression(AQL_NEW_INSTANCE)
                .children(setValueName.build());

        for (SetValueBuilder setValueBuilder : valuesBuilders) {
            changeContextNewInstance.children(setValueBuilder.build());
        }

        var createInstance = this.viewBuilderHelper.newCreateInstance()
                .typeName("ecore::" + name)
                .referenceName(containmentRef.getName())
                .variableName(NEW_INSTANCE)
                .children(changeContextNewInstance.build());

        return this.diagramBuilderHelper.newNodeTool()
                .name(name)
                .iconURLsExpression("/icons/full/obj16/" + name + ".gif")
                .body(createInstance.build())
                .build();
    }

    public NodeTool createNodeToolCreateEPackage() {
        return this.createNodeToolCreate(EcoreFactory.eINSTANCE.createEPackage(), EcorePackage.Literals.EPACKAGE__ESUBPACKAGES);
    }

    public NodeTool createNodeToolCreateEClass() {
        return this.createNodeToolCreate(EcoreFactory.eINSTANCE.createEClass(), EcorePackage.Literals.EPACKAGE__ECLASSIFIERS);
    }

    public NodeTool createNodeToolCreateEClassInterface() {
        var setValueInterface = this.viewBuilderHelper.newSetValue()
                .featureName(EcorePackageQueryUtils.getEClassInterfaceFeatureName(false))
                .valueExpression(AQL_TRUE);
        var nodeTool = this.createNodeToolCreate(EcoreFactory.eINSTANCE.createEClass(), EcorePackage.Literals.EPACKAGE__ECLASSIFIERS, setValueInterface);
        nodeTool.setName("Interface");
        nodeTool.setIconURLsExpression("/images/EClass_interface.gif");
        return nodeTool;
    }

    public NodeTool createNodeToolCreateEClassAbstract() {
        var setValueAbstract = this.viewBuilderHelper.newSetValue()
                .featureName(EcorePackageQueryUtils.getEClassAbstractFeatureName(false))
                .valueExpression(AQL_TRUE);
        var nodeTool = this.createNodeToolCreate(EcoreFactory.eINSTANCE.createEClass(), EcorePackage.Literals.EPACKAGE__ECLASSIFIERS, setValueAbstract);
        nodeTool.setName("Abstract EClass");
        nodeTool.setIconURLsExpression("/images/EClass_abstract.gif");
        return nodeTool;
    }

    public NodeTool createNodeToolCreateEEnum() {
        return this.createNodeToolCreate(EcoreFactory.eINSTANCE.createEEnum(), EcorePackage.Literals.EPACKAGE__ECLASSIFIERS);
    }

    public NodeTool createNodeToolCreateEDataType() {
        return this.createNodeToolCreate(EcoreFactory.eINSTANCE.createEDataType(), EcorePackage.Literals.EPACKAGE__ECLASSIFIERS);
    }

    public NodeTool createNodeToolCreateEEnumLiteral() {
        var setValueValue = this.viewBuilderHelper.newSetValue()
                .featureName("value")
                .valueExpression("aql:self.eContainer().eContents()->filter(ecore::EEnumLiteral)->size()-1");
        return this.createNodeToolCreate(EcoreFactory.eINSTANCE.createEEnumLiteral(), EcorePackage.Literals.EENUM__ELITERALS, setValueValue);
    }

    public NodeTool createNodeToolCreateEAttribute() {
        return this.createNodeToolCreate(EcoreFactory.eINSTANCE.createEAttribute(), EcorePackage.Literals.ECLASS__ESTRUCTURAL_FEATURES);
    }

    public NodeTool createNodeToolCreateEOperation() {
        return this.createNodeToolCreate(EcoreFactory.eINSTANCE.createEOperation(), EcorePackage.Literals.ECLASS__EOPERATIONS);
    }

    public EdgeTool createEdgeToolCreateSuperType(NodeDescription eClassNodeDescription) {
        /*
         * createTypeArgumentsIfNeeded service returns void, thus we shall intercept it to avoid problems during tool
         * execution (thus using "= null")
         */
        var sideEffectIf = this.viewBuilderHelper.newIf()
                .conditionExpression("aql:self.createTypeArgumentsIfNeeded(semanticEdgeTarget) = null");

        var setESuperTypes = this.viewBuilderHelper.newSetValue()
                .featureName(EcorePackageQueryUtils.getEClassESuperTypesFeatureName(false))
                .valueExpression("var:semanticEdgeTarget")
                .children(sideEffectIf.build());

        var changeContext = this.viewBuilderHelper.newChangeContext()
                .expression("var:semanticEdgeSource")
                .children(setESuperTypes.build());

        return this.diagramBuilderHelper.newEdgeTool()
                .name("SuperType")
                .iconURLsExpression("/images/Inheritance.gif")
                .targetElementDescriptions(eClassNodeDescription)
                .body(changeContext.build())
                .build();
    }

    /**
     * Create a Tool to create an EReference.
     *
     * @param eClassNodeDescription
     *            The target node description
     * @param composition
     *            Whether of not the created EReferences will be containment
     * @param eOpposite
     *            Whether of not the eOpposite EReferences will be created too and linked
     * @return
     */
    public EdgeTool createEdgeToolCreateEReference(NodeDescription eClassNodeDescription, boolean composition, boolean eOpposite) {
        /*
         * setEType service returns void, thus we shall intercept it to avoid problems during tool
         * execution (thus using "= null")
         */
        var sideEffectIf = this.viewBuilderHelper.newIf()
                .conditionExpression("aql:self.setEType(semanticEdgeTarget) = null");

        var setValueName = this.viewBuilderHelper.newSetValue()
                .featureName(NAME)
                .valueExpression("aql:semanticEdgeTarget.name.toLower() + self.eContainer().eContents()->filter(ecore::EReference)->size()");

        var changeContextNewInstance = this.viewBuilderHelper.newChangeContext()
                .expression(AQL_NEW_INSTANCE)
                .children(setValueName.build(), sideEffectIf.build());

        if (composition) {
            var setValueContainment = this.viewBuilderHelper.newSetValue()
                    .featureName(EcorePackageQueryUtils.getEReferenceAttributeContainmentFeatureName(false))
                    .valueExpression(AQL_TRUE);

            var setValueUpperBound = this.viewBuilderHelper.newSetValue()
                    .featureName(EcorePackageQueryUtils.getEReferenceUpperBoundFeatureName(false))
                    .valueExpression("aql:-1");

            changeContextNewInstance.children(setValueContainment.build(), setValueUpperBound.build());
        }

        var createInstanceERef = this.viewBuilderHelper.newCreateInstance()
                .typeName(EcorePackageQueryUtils.getEReferenceDataTypeQualifiedName())
                .referenceName(EcorePackageQueryUtils.getEReferenceContainmentFeatureName(false))
                .variableName(NEW_INSTANCE)
                .children(changeContextNewInstance.build());

        var changeContext = this.viewBuilderHelper.newChangeContext()
                .expression("var:semanticEdgeSource")
                .children(createInstanceERef.build());

        if (eOpposite) {
            var setValueEOpposite = this.viewBuilderHelper.newSetValue()
                    .featureName(EcorePackageQueryUtils.getEReferenceEOppositeFeatureName(false))
                    .valueExpression(AQL_NEW_INSTANCE);

            String eOppPostfix = "eOpp";
            var setValueEOppositeEOpposite = this.viewBuilderHelper.newSetValue()
                    .featureName(EcorePackageQueryUtils.getEReferenceEOppositeFeatureName(false))
                    .valueExpression(AQL_NEW_INSTANCE + eOppPostfix);

            var sideEffectIfEOpposite = this.viewBuilderHelper.newIf()
                    .conditionExpression("aql:self.setEType(semanticEdgeSource) = null");

            var setValueNameEOpposite = this.viewBuilderHelper.newSetValue()
                    .featureName(NAME)
                    .valueExpression("aql:semanticEdgeSource.name.toLower() + self.eContainer().eContents()->filter(ecore::EReference)->size()");

            var changeContextNewInstanceEOpposite = this.viewBuilderHelper.newChangeContext()
                    .expression(AQL_NEW_INSTANCE + eOppPostfix)
                    .children(setValueNameEOpposite.build(), sideEffectIfEOpposite.build(), setValueEOpposite.build());

            var changeContextFirstERefInstance = this.viewBuilderHelper.newChangeContext()
                    .expression(AQL_NEW_INSTANCE)
                    .children(setValueEOppositeEOpposite.build());

            var createInstanceERefEOpoosite = this.viewBuilderHelper.newCreateInstance()
                    .typeName(EcorePackageQueryUtils.getEReferenceDataTypeQualifiedName())
                    .referenceName(EcorePackageQueryUtils.getEReferenceContainmentFeatureName(false))
                    .variableName(NEW_INSTANCE + eOppPostfix)
                    .children(changeContextNewInstanceEOpposite.build(), changeContextFirstERefInstance.build());

            var changeContextNewEOppositeInstance = this.viewBuilderHelper.newChangeContext()
                    .expression("aql:semanticEdgeTarget")
                    .children(createInstanceERefEOpoosite.build());

            createInstanceERef.children(changeContextNewEOppositeInstance.build());
        }

        var toolName = "Reference";
        if (composition) {
            toolName = "Composition";
        }
        if (eOpposite) {
            toolName += " with Opposite Reference";
        }

        return this.diagramBuilderHelper.newEdgeTool()
                .name(toolName)
                .iconURLsExpression("/icons/full/ctool16/CreateEClass_eStructuralFeatures_EReference.gif")
                .targetElementDescriptions(eClassNodeDescription)
                .body(changeContext.build())
                .build();
    }

}
