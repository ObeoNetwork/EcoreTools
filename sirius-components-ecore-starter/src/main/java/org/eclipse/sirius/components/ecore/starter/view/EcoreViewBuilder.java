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

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
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
import org.eclipse.sirius.components.view.diagram.SourceEdgeEndReconnectionTool;
import org.eclipse.sirius.components.view.diagram.TargetEdgeEndReconnectionTool;

/**
 * Used to help creating the Ecore views.
 *
 * @author adieumegard
 */
public class EcoreViewBuilder {

    private static final String AQL_TRUE = "aql:true";
    private static final String NEW_INSTANCE = "newInstance";
    private static final String AQL_NEW_INSTANCE = "aql:newInstance";
    private static final String NAME = "name";
    private static final String AQL_SELF_DEFAULT_DELETE = "aql:self.defaultDelete()";

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

    /**
     * Create a standard delete tool.
     *
     * @return
     */
    public DeleteTool createDeleteTool() {
        var changeContext = this.viewBuilderHelper.newChangeContext()
                .expression(AQL_SELF_DEFAULT_DELETE);

        return this.diagramBuilderHelper.newDeleteTool()
                .name("Delete from Model")
                .body(changeContext.build())
                .build();
    }

    /**
     * Create a Delete tool that deletes an EClass and the EReferences whose eType is the EClass.
     *
     * @return
     */
    public DeleteTool createDeleteClassTool() {
        var changeContextReferencingERefs = this.viewBuilderHelper.newChangeContext()
                .expression("aql:self.deleteReferencingEReferences()");

        var changeContext = this.viewBuilderHelper.newChangeContext()
                .expression(AQL_SELF_DEFAULT_DELETE);

        return this.diagramBuilderHelper.newDeleteTool()
                .name("Delete including referencing EReferences")
                .body(changeContextReferencingERefs.build(), changeContext.build())
                .build();
    }

    /**
     * Create a Delete tool that deletes an {@link EReference} and its {@code eOpposite} {@link EReference}. <br/>
     * Relies on
     * {@linkplain org.eclipse.sirius.components.ecore.starter.services.EcoreService.deleteEOppositeEReference(EReference)}
     *
     * @return
     */
    public DeleteTool createDeleteBiDirectionalEReferenceTool() {
        var changeContextEOppositeERef = this.viewBuilderHelper.newChangeContext()
                .expression("aql:self.deleteEOppositeEReference()");

        var changeContext = this.viewBuilderHelper.newChangeContext()
                .expression(AQL_SELF_DEFAULT_DELETE);

        return this.diagramBuilderHelper.newDeleteTool()
                .name("Delete along with eOpposite")
                .body(changeContextEOppositeERef.build(), changeContext.build())
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
                .name("Unset reference")
                .body(changeContext.build())
                .build();
    }

    /**
     * Label edit tool setting the value of the Label attribute (computed through the configuration-injected
     * {@linkplain org.eclipse.sirius.components.emf.services.LabelFeatureProvider.LabelFeatureProvider(String,
     * Switch<EAttribute>, Switch<Boolean>)}) to the provided input value.
     * <br/>
     * Relies on {@linkplain org.eclipse.sirius.components.view.emf.CanonicalServices.defaultEditLabel(EObject,
     * String)}.
     *
     * @return
     */
    public LabelEditTool createLabelEditTool() {
        var changeContext = this.viewBuilderHelper.newChangeContext()
                .expression("aql:self.defaultEditLabel(newLabel)");

        return this.diagramBuilderHelper.newLabelEditTool()
                .name("Edit")
                .body(changeContext.build())
                .build();
    }

    /**
     * Label edit tool setting the value of the {@code name} attribute to the provided input value and refactoring the
     * label value as Camel case. <br/>
     * Relies on {@linkplain org.eclipse.emf.ecoretools.design.service.DesignServices.toCamelCase(EObject, String)}.
     *
     * @return
     */
    public LabelEditTool createLabelEditNameWithCamelCaseTool() {
        var setValueService = this.viewBuilderHelper.newSetValue()
                .featureName(NAME)
                .valueExpression("aql:self.toCamelCase(newLabel)");

        return this.diagramBuilderHelper.newLabelEditTool()
                .name("Edit Name with Camel Case")
                .body(setValueService.build())
                .build();
    }

    /**
     * Label edit tool setting the value of the {@code name} attribute to the provided input value.
     *
     * @return
     */
    public LabelEditTool createLabelEditNameNoCamelCaseTool() {
        var setValueService = this.viewBuilderHelper.newSetValue()
                .featureName(NAME)
                .valueExpression("aql:newLabel");

        return this.diagramBuilderHelper.newLabelEditTool()
                .name("Edit Name no Camel Case")
                .body(setValueService.build())
                .build();
    }

    /**
     * Label edit tool setting the value of the {@code instanceClassName} attribute to the provided input value.
     *
     * @return
     */
    public LabelEditTool createLabelEditInstanceClassNameTool() {
        var setValueService = this.viewBuilderHelper.newSetValue()
                .featureName(EcorePackageQueryUtils.getEClassifierInstanceClassNameFeatureName(false))
                .valueExpression("aql:newLabel");

        return this.diagramBuilderHelper.newLabelEditTool()
                .name("Edit Name Instance Direct Edit")
                .body(setValueService.build())
                .build();
    }

    /**
     * Label edit tool setting the attributes of an {@linkplain EAttribute} based on the provided input. Will allow to
     * set the {@linkplain EAttribute} dataType and cardinality values.
     *
     * @return
     */
    public LabelEditTool createLabelEditEStructuralFeatureNameTool() {
        var changeContext = this.viewBuilderHelper.newChangeContext()
                .expression("aql:self.performEditAsAttribute(newLabel)");

        return this.diagramBuilderHelper.newLabelEditTool()
                .name("Edit EStructuralFeature Name")
                .body(changeContext.build())
                .build();
    }

    /**
     * Label edit tool setting the attributes of an {@linkplain EOperation} based on the provided input. Will allow to
     * set the {@linkplain EOperation} dataType, parameters, and cardinality values. <br/>
     * Relies on {@linkplain org.eclipse.emf.ecoretools.design.service.DesignServices.performEdit(EAttribute, String)}
     * or {@linkplain org.eclipse.emf.ecoretools.design.service.DesignServices.performEdit(EOperation, String)}.
     *
     * @return
     */
    public LabelEditTool createLabelEditEReferenceOrEOperationNameTool() {
        var changeContext = this.viewBuilderHelper.newChangeContext()
                .expression("aql:self.performEdit(newLabel)");

        return this.diagramBuilderHelper.newLabelEditTool()
                .name("Edit EReference or EOperation Name")
                .body(changeContext.build())
                .build();
    }

    /**
     * Utility method to create a Node creation tool.
     * @param object
     * @param containmentRef
     * @param valuesBuilders
     * @return
     */
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

    /**
     * Create an Edge Reconnection Tool to reconnect the source end of the ESuperType edge. <br/>
     * Relies on
     * {@linkplain org.eclipse.sirius.components.ecore.starter.services.EcoreDesignServices.reconnectESuperTypeSource(EObject,
     * EObject, EObject, EObject)}
     *
     * @return
     */
    public SourceEdgeEndReconnectionTool createEdgeReconnectESupertypeSource() {
        var changeContext = this.viewBuilderHelper.newChangeContext()
            .expression("aql:edgeSemanticElement.reconnectESuperTypeSource(semanticReconnectionTarget, semanticReconnectionSource, semanticOtherEnd)");

        return this.diagramBuilderHelper.newSourceEdgeEndReconnectionTool()
                .name("Reconnect ESuperType from Source")
                .body(changeContext.build())
                .build();
    }

    /**
     * Create an Edge Reconnection Tool to reconnect the target end of the ESuperType edge. <br/>
     * Relies on
     * {@linkplain org.eclipse.sirius.components.ecore.starter.services.EcoreDesignServices.reconnectESuperTypeTarget(EObject,
     * EObject, EObject, EObject)}
     *
     * @return
     */
    public TargetEdgeEndReconnectionTool createEdgeReconnectESupertypeTarget() {
        var changeContext = this.viewBuilderHelper.newChangeContext()
            .expression("aql:edgeSemanticElement.reconnectESuperTypeTarget(semanticReconnectionTarget, semanticReconnectionSource, semanticOtherEnd)");

        return this.diagramBuilderHelper.newTargetEdgeEndReconnectionTool()
                .name("Reconnect ESuperType from Target")
                .body(changeContext.build())
                .build();
    }

    /**
     * Create an Edge Reconnection Tool to reconnect the source end of an EReference edge. <br/>
     * Relies on
     * {@linkplain org.eclipse.sirius.components.ecore.starter.services.EcoreDesignServices.reconnectEReferenceEdgeSource(EObject,
     * EObject)}
     *
     * @return
     */
    public SourceEdgeEndReconnectionTool createEdgeReconnectEReferenceSource() {
        var changeContext = this.viewBuilderHelper.newChangeContext()
            .expression("aql:edgeSemanticElement.reconnectEReferenceEdgeSource(semanticReconnectionTarget)");

        return this.diagramBuilderHelper.newSourceEdgeEndReconnectionTool()
                .name("Reconnect EReference from Source")
                .body(changeContext.build())
                .build();
    }

    /**
     * Create an Edge Reconnection Tool to reconnect the target end of an EReference edge. <br/>
     * Relies on
     * {@linkplain org.eclipse.sirius.components.ecore.starter.services.EcoreDesignServices.reconnectEReferenceEdgeTarget(EObject,
     * EObject)}
     *
     * @return
     */
    public TargetEdgeEndReconnectionTool createEdgeReconnectEReferenceTarget() {
        var changeContext = this.viewBuilderHelper.newChangeContext()
            .expression("aql:edgeSemanticElement.reconnectEReferenceEdgeTarget(semanticReconnectionTarget)");

        return this.diagramBuilderHelper.newTargetEdgeEndReconnectionTool()
                .name("Reconnect EReference from Target")
                .body(changeContext.build())
                .build();
    }

}
