/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package fr.obeo.ecore.editor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.components.emf.services.IDAdapter;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.view.*;
import org.eclipse.sirius.components.view.builder.generated.diagram.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.diagram.*;

/** The Design / Entities diagram, with explicit classifier views and synchronized contents. */
public class EcoreViews {
    public static final String DIAGRAM_NAME = "Ecore Class Diagram";
    private final DiagramBuilders diagramBuilders = new DiagramBuilders();
    private final ViewBuilders viewBuilders = new ViewBuilders();
    private final List<FixedColor> colors = new ArrayList<>();
    private final FixedColor ink = registerColor("Ink", EcoreDiagramStyle.TEXT);
    private final FixedColor secondary = registerColor("Secondary", EcoreDiagramStyle.SECONDARY);
    private final FixedColor border = registerColor("Border", EcoreDiagramStyle.BORDER);
    private final FixedColor relationship = registerColor("Relationship", EcoreDiagramStyle.RELATIONSHIP);
    private final FixedColor canvas = registerColor("Canvas", "var(--ecore-canvas, " + EcoreDiagramStyle.CANVAS + ")");
    private final FixedColor error = registerColor("Error", EcoreDiagramStyle.ERROR);
    private final FixedColor white = registerColor("White", EcoreDiagramStyle.SURFACE);
    private final FixedColor classColor = registerColor("EClass", EcoreDiagramStyle.SURFACE);
    private final FixedColor abstractColor = registerColor("Abstract EClass", EcoreDiagramStyle.SURFACE);
    private final FixedColor enumColor = registerColor("EEnum", EcoreDiagramStyle.SURFACE);
    private final FixedColor dataColor = registerColor("EDataType", EcoreDiagramStyle.SURFACE);
    private final FixedColor packageColor = registerColor("EPackage", EcoreDiagramStyle.SURFACE);
    private final FixedColor noteColor = registerColor("Documentation", EcoreDiagramStyle.NOTE);
    private final FixedColor transparent = registerColor("Transparent", "transparent");
    private final FixedColor commentBorder = registerColor("Comment border", EcoreDiagramStyle.BORDER);
    private final FixedColor constraintColor = registerColor("Constraints", EcoreDiagramStyle.CONSTRAINT);
    private final FixedColor constraintBorder = registerColor("Constraint border", EcoreDiagramStyle.DATATYPE);
    private final FixedColor typeParameterColor = registerColor("Type parameter", EcoreDiagramStyle.SURFACE);
    private final FixedColor typeParameterBorder = registerColor("Type parameter border", EcoreDiagramStyle.BORDER);
    private final FixedColor derivedColor = registerColor("Derived", EcoreDiagramStyle.DATATYPE);

    public View create() {
        var classNode = node("EClass", "aql:self.ecoreImportCandidates()->filter(ecore::EClass)", classColor, true);
        classNode.getConditionalStyles().add(diagramBuilders.newConditionalNodeStyle().condition("aql:self.abstract or self.interface")
                .style(nodeStyle(abstractColor, border, true)).build());
        var abstractLabel = labelStyle(ink, true);
        abstractLabel.setItalic(true);
        classNode.getInsideLabel().getConditionalStyles().add(diagramBuilders.newConditionalInsideLabelStyle()
                .condition("aql:self.abstract or self.interface").style(abstractLabel).build());
        var attribute = node("EAttribute", "aql:self.eAttributes", transparent, false);
        var operation = node("EOperation", "aql:self.eOperations", transparent, false);
        var parameter = node("ETypeParameter", "aql:self.eTypeParameters", typeParameterColor, false);
        parameter.setDefaultWidthExpression("32");
        parameter.setDefaultHeightExpression("32");
        var parameterStyle = (RectangularNodeStyleDescription) parameter.getStyle();
        parameterStyle.setBorderColor(typeParameterBorder);
        parameterStyle.setBorderSize(1);
        parameterStyle.setBorderLineStyle(LineStyle.DASH);
        parameter.getConditionalStyles().forEach(s -> s.getStyle().setBorderLineStyle(LineStyle.DASH));
        parameter.getInsideLabel().setPosition(InsideLabelPosition.MIDDLE_CENTER);
        parameter.getInsideLabel().getStyle().setShowIconExpression("aql:false");
        parameter.getInsideLabel().getConditionalStyles().forEach(s -> s.getStyle().setShowIconExpression("aql:false"));
        var referenceRow = node("EReference", "aql:self.ecoreReferenceRows(diagramContext)", transparent, false);
        this.configureStructuralFeatureLabels(attribute);
        this.configureStructuralFeatureLabels(referenceRow);
        referenceRow.getInsideLabel().setLabelExpression("aql:self.ecoreReferenceRowLabel()");
        referenceRow.getPalette().setLabelEditTool(diagramBuilders.newLabelEditTool().name("Edit reference as attribute")
                .initialDirectEditLabelExpression("aql:self.ecoreReferenceRowLabel()")
                .body(viewBuilders.newChangeContext().expression("aql:self.ecoreEditReferenceRow(newLabel)").build()).build());
        referenceRow.getPalette().setDeleteTool(deleteReferencePairTool());
        classNode.getChildrenDescriptions().addAll(List.of(attribute, operation, referenceRow));
        classNode.getBorderNodesDescriptions().add(parameter);
        classNode.getPalette().getNodeTools().addAll(List.of(createTool("EAttribute", null), createTool("EOperation", null), createTool("ETypeParameter", null), createTool("EReference", null)));
        var enumeration = node("EEnum", "aql:self.ecoreImportCandidates()->filter(ecore::EEnum)", enumColor, true);
        enumeration.getChildrenDescriptions().add(node("EEnumLiteral", "aql:self.eLiterals", transparent, false));
        enumeration.getPalette().getNodeTools().add(createTool("EEnumLiteral", null));
        var dataTypeNode = node("EDataType", "aql:self.ecoreImportCandidates()->select(e | e.eClass() = ecore::EDataType)", dataColor, true);
        var instanceClassNameNode = node("InstanceClassName", "aql:self", transparent, false);
        instanceClassNameNode.setDomainType("ecore::EDataType");
        instanceClassNameNode.getInsideLabel().setLabelExpression("aql:self.instanceClassName");
        instanceClassNameNode.getInsideLabel().getStyle().setShowIconExpression("aql:false");
        instanceClassNameNode.getInsideLabel().getStyle().setItalic(true);
        instanceClassNameNode.getPalette().setLabelEditTool(diagramBuilders.newLabelEditTool().name("Edit instance class name")
                .initialDirectEditLabelExpression("aql:self.instanceClassName")
                .body(viewBuilders.newSetValue().featureName("instanceClassName").valueExpression("aql:newLabel").build()).build());
        instanceClassNameNode.getPalette().setDeleteTool(diagramBuilders.newDeleteTool().name("Keep datatype").preconditionExpression("aql:false").build());
        dataTypeNode.getChildrenDescriptions().add(instanceClassNameNode);
        var packageNode = node("EPackage", "aql:self.eSubpackages", packageColor, true);
        packageNode.setDefaultWidthExpression("360");
        packageNode.setDefaultHeightExpression("240");
        packageNode.getStyle().setChildrenLayoutStrategy(diagramBuilders.newFreeFormLayoutStrategyDescription().build());
        packageNode.getConditionalStyles().forEach(s -> s.getStyle().setChildrenLayoutStrategy(diagramBuilders.newFreeFormLayoutStrategyDescription().build()));
        packageNode.getReusedChildNodeDescriptions().addAll(List.of(classNode, enumeration, dataTypeNode, packageNode));
        var topLevelNodeDescriptions = List.of(classNode, enumeration, dataTypeNode, packageNode);
        var note = node("EStringToStringMapEntry", "aql:self.ecoreNotes(diagramContext)", noteColor, false);
        note.setName("Documentation and constraints");
        note.setDefaultWidthExpression("260");
        note.setDefaultHeightExpression("80");
        var commentStyle = (RectangularNodeStyleDescription) note.getStyle();
        commentStyle.setBorderColor(commentBorder);
        commentStyle.setBorderSize(1);
        commentStyle.setBorderRadius(EcoreDiagramStyle.RADIUS);
        commentStyle.setBorderLineStyle(LineStyle.DASH);
        var constraintStyle = nodeStyle(constraintColor, constraintBorder, false);
        constraintStyle.setBorderSize(1);
        constraintStyle.setBorderRadius(EcoreDiagramStyle.RADIUS);
        note.getConditionalStyles().add(diagramBuilders.newConditionalNodeStyle().condition("aql:self.key = 'constraints'")
                .style(constraintStyle).build());
        note.getInsideLabel().setLabelExpression("aql:self.value");
        note.getInsideLabel().setOverflowStrategy(LabelOverflowStrategy.WRAP);
        note.getInsideLabel().getStyle().setShowIconExpression("aql:false");
        var constraintLabel = labelStyle(constraintBorder, false);
        constraintLabel.setShowIconExpression("aql:false");
        note.getInsideLabel().getConditionalStyles().add(diagramBuilders.newConditionalInsideLabelStyle()
                .condition("aql:self.key = 'constraints'").style(constraintLabel).build());
        note.getPalette().setLabelEditTool(diagramBuilders.newLabelEditTool().name("Edit detail")
                .initialDirectEditLabelExpression("aql:self.value")
                .body(viewBuilders.newSetValue().featureName("value").valueExpression("aql:newLabel").build()).build());
        var noteTargets = List.of(classNode, enumeration, dataTypeNode, packageNode, attribute, operation, referenceRow, parameter);
        var relatedImport = createImportTool(topLevelNodeDescriptions, "Import related elements", "aql:self.ecoreRelatedElements(null)", "aql:null");
        ((For) relatedImport.getBody().getFirst()).setExpression("aql:selectedObjects->intersection(self.ecoreRelatedElements(diagramContext))");
        for (var target : noteTargets) {
            target.getPalette().getNodeTools().add(EcoreUtil.copy(relatedImport));
            target.getPalette().getQuickAccessTools().add(EcoreUtil.copy(relatedImport));
            for (boolean constraint : List.of(false, true)) {
                target.getPalette().getNodeTools().add(createNoteTool(constraint));
            }
        }
        var assignNote = diagramBuilders.newEdgeTool().name("Assign annotation").iconURLsExpression("/icons/ecore/EAnnotation.svg")
                .targetElementDescriptions(noteTargets.toArray(NodeDescription[]::new))
                .body(viewBuilders.newChangeContext().expression("aql:semanticEdgeSource.ecoreAssignNote(semanticEdgeTarget)").build()).build();
        note.getPalette().getEdgeTools().add(assignNote);
        var noteLink = diagramBuilders.newEdgeDescription().name("Annotation assignment").domainType("ecore::EStringToStringMapEntry")
                .centerLabelExpression("")
                .isDomainBasedEdge(true).semanticCandidatesExpression("aql:self.ecoreNotes(diagramContext)")
                .sourceDescriptions(note).targetDescriptions(noteTargets.toArray(NodeDescription[]::new))
                .sourceExpression("aql:self").targetExpression("aql:self.eContainer().eModelElement")
                .style(edgeStyle(relationship, ArrowStyle.NONE, ArrowStyle.NONE))
                .palette(diagramBuilders.newEdgePalette().edgeReconnectionTools(diagramBuilders.newTargetEdgeEndReconnectionTool().name("Reassign annotation")
                        .body(viewBuilders.newChangeContext().expression("aql:edgeSemanticElement.ecoreAssignNote(semanticReconnectionTarget)").build()).build()).build()).build();
        noteLink.getStyle().setLineStyle(LineStyle.DASH);
        for (NodeDescription nodeDescription : topLevelNodeDescriptions) {
            nodeDescription.getPalette().getNodeTools().add(diagramBuilders.newNodeTool().name("Remove from diagram")
                    .body(diagramBuilders.newDeleteView().viewExpression("aql:selectedNode").build()).build());
        }
        var diagramTools = new ArrayList<NodeTool>();
        for (NodeDescription nodeDescription : topLevelNodeDescriptions) diagramTools.add(createTool(nodeDescription.getName(), nodeDescription));
        diagramTools.add(createImportTool(topLevelNodeDescriptions, "Import existing elements", "aql:self.ecoreImportCandidates()", "aql:selectedNode"));
        packageNode.getPalette().getNodeTools().addAll(diagramTools.stream().map(EcoreUtil::copy).toList());
        diagramTools.add(createNoteTool(false));
        for (boolean containment : List.of(false, true)) {
            classNode.getPalette().getEdgeTools().add(diagramBuilders.newEdgeTool().name(containment ? "Composition" : "Reference")
                    .targetElementDescriptions(classNode, parameter)
                    .body(viewBuilders.newChangeContext().expression("aql:semanticEdgeSource.ecoreReference(semanticEdgeTarget, " + containment + ")").build()).build());
        }
        classNode.getPalette().getEdgeTools().add(diagramBuilders.newEdgeTool().name("Supertype").targetElementDescriptions(classNode)
                .body(viewBuilders.newChangeContext().expression("aql:semanticEdgeSource.ecoreSuperType(semanticEdgeTarget)").build()).build());
        classNode.getPalette().getEdgeTools().add(diagramBuilders.newEdgeTool().name("Bidirectional reference")
                .iconURLsExpression("/icons/ecore/EReference.svg").targetElementDescriptions(classNode)
                .body(viewBuilders.newChangeContext().expression("aql:semanticEdgeSource.ecoreBidirectionalReference(semanticEdgeTarget)").build()).build());
        var reference = diagramBuilders.newEdgeDescription().name("EReference").domainType("ecore::EReference")
                .semanticCandidatesExpression("aql:self.ecoreReferences()")
                .preconditionExpression("aql:self.eOpposite = null")
                .isDomainBasedEdge(true).sourceDescriptions(classNode).targetDescriptions(classNode, parameter)
                .sourceExpression("aql:self.eContainingClass").targetExpression("aql:self.ecoreReferenceTarget()")
                .centerLabelExpression("aql:self.ecoreLabel()")
                .style(edgeStyle(relationship, ArrowStyle.NONE, ArrowStyle.INPUT_ARROW))
                .conditionalStyles(referenceEdgeStyles("self.lowerBound > 0", "self.derived", "self.ecoreHasError()", ArrowStyle.NONE))
                .palette(diagramBuilders.newEdgePalette().centerLabelEditTool(labelEditTool()).deleteTool(deleteReferencePairTool()).edgeReconnectionTools(
                        diagramBuilders.newSourceEdgeEndReconnectionTool().name("Reconnect reference source")
                                .body(viewBuilders.newChangeContext().expression("aql:edgeSemanticElement.ecoreReconnectReference(semanticReconnectionTarget, true)").build()).build(),
                        diagramBuilders.newTargetEdgeEndReconnectionTool().name("Reconnect reference target")
                                .body(viewBuilders.newChangeContext().expression("aql:edgeSemanticElement.ecoreReconnectReference(semanticReconnectionTarget, false)").build()).build())
                        .build()).build();
        reference.getPalette().getNodeTools().add(diagramBuilders.newNodeTool().name("Create opposite")
                .iconURLsExpression("/icons/ecore/EReference.svg")
                .preconditionExpression("aql:self.ecoreReferenceTarget().oclIsKindOf(ecore::EClass)")
                .body(viewBuilders.newChangeContext().expression("aql:self.ecoreCreateOpposite()").build()).build());
        var opposite = EcoreUtil.copy(reference);
        opposite.setName("EOpposite");
        opposite.setPreconditionExpression("aql:self.eOpposite <> null");
        opposite.getTargetDescriptions().remove(parameter);
        opposite.setCenterLabelExpression("");
        opposite.setBeginLabelExpression("aql:self.eOpposite.ecoreLabel()");
        opposite.setEndLabelExpression("aql:self.ecoreLabel()");
        // Sirius' converter enables endpoint editing only if a center edit tool is configured.
        opposite.getPalette().setBeginLabelEditTool(diagramBuilders.newLabelEditTool().name("Edit opposite")
                .initialDirectEditLabelExpression("aql:self.eOpposite.ecoreLabel()")
                .body(viewBuilders.newChangeContext().expression("aql:self.eOpposite.ecoreEdit(newLabel)").build()).build());
        opposite.getPalette().setEndLabelEditTool(labelEditTool());
        opposite.getPalette().getNodeTools().clear();
        opposite.getStyle().setSourceArrowStyle(ArrowStyle.INPUT_ARROW);
        opposite.getConditionalStyles().clear();
        opposite.getConditionalStyles().addAll(List.of(referenceEdgeStyles(
                "self.lowerBound > 0 or self.eOpposite.lowerBound > 0", "self.derived or self.eOpposite.derived",
                "self.ecoreHasError() or self.eOpposite.ecoreHasError()", ArrowStyle.INPUT_ARROW)));
        assignNote.getTargetElementDescriptions().addAll(List.of(reference, opposite));
        noteLink.getTargetDescriptions().addAll(List.of(reference, opposite));
        var inheritance = diagramBuilders.newEdgeDescription().name("ESuperTypes").domainType("ecore::EClass")
                .centerLabelExpression("aql:self.ecoreSuperTypeLabel(semanticEdgeTarget)")
                .isDomainBasedEdge(false).sourceDescriptions(classNode).targetDescriptions(classNode)
                .targetExpression("aql:self.ecoreVisibleSuperTypes(diagramContext)").style(edgeStyle(relationship, ArrowStyle.NONE, ArrowStyle.INPUT_CLOSED_ARROW))
                .palette(diagramBuilders.newEdgePalette().deleteTool(diagramBuilders.newDeleteTool().name("Remove supertype")
                        .body(viewBuilders.newChangeContext().expression("aql:semanticEdgeSource.ecoreRemoveSuperType(semanticEdgeTarget)").build()).build())
                        .edgeReconnectionTools(
                                diagramBuilders.newSourceEdgeEndReconnectionTool().name("Reconnect supertype source")
                                        .body(viewBuilders.newChangeContext().expression("aql:edgeSemanticElement.ecoreReconnectSuperType(semanticOtherEnd, semanticReconnectionTarget, true)").build()).build(),
                                diagramBuilders.newTargetEdgeEndReconnectionTool().name("Reconnect supertype target")
                                        .body(viewBuilders.newChangeContext().expression("aql:edgeSemanticElement.ecoreReconnectSuperType(semanticReconnectionSource, semanticReconnectionTarget, false)").build()).build())
                        .build()).build();
        inheritance.getStyle().setItalic(true);
        inheritance.getConditionalStyles().add(diagramBuilders.newConditionalEdgeStyle().condition("aql:not self.eSuperTypes->includes(semanticEdgeTarget)")
                .color(relationship).background(transparent).borderColor(relationship).borderSize(0).edgeWidth(1).fontSize(EcoreDiagramStyle.EDGE_SIZE).italic(true)
                .sourceArrowStyle(ArrowStyle.NONE).targetArrowStyle(ArrowStyle.INPUT_CLOSED_ARROW)
                .edgeType(EdgeType.MANHATTAN).lineStyle(LineStyle.DOT).showIcon(false).build());
        inheritance.getConditionalStyles().add(diagramBuilders.newConditionalEdgeStyle().condition("aql:semanticEdgeTarget.interface")
                .color(relationship).background(transparent).borderColor(relationship).borderSize(0).edgeWidth(1).fontSize(EcoreDiagramStyle.EDGE_SIZE).italic(true)
                .sourceArrowStyle(ArrowStyle.NONE).targetArrowStyle(ArrowStyle.INPUT_CLOSED_ARROW)
                .edgeType(EdgeType.MANHATTAN).lineStyle(LineStyle.DASH).showIcon(false).build());
        var diagram = diagramBuilders.newDiagramDescription().name(DIAGRAM_NAME).domainType("ecore::EPackage")
                .iconExpression("aql:'/icons/ecore/EcoreClassDiagram.svg'")
                .style(diagramBuilders.newDiagramStyleDescription().background(canvas).build())
                .toolbar(diagramBuilders.newDiagramToolbar().expandedByDefault(true).build())
                .titleExpression("aql:self.name + ' class diagram'")
                .nodeDescriptions(classNode, enumeration, dataTypeNode, packageNode, note).edgeDescriptions(reference, opposite, inheritance, noteLink)
                .palette(diagramBuilders.newDiagramPalette().nodeTools(diagramTools.toArray(NodeTool[]::new))
                        .dropTool(diagramBuilders.newDropTool().name("Import dropped element").body(createImportViewOperations(topLevelNodeDescriptions, "self", "aql:selectedNode")).build()).build()).build();
        var view = viewBuilders.newView().descriptions(diagram).colorPalettes(viewBuilders.newColorPalette().name("EcoreTools").colors(colors.toArray(FixedColor[]::new)).build()).build();
        this.attachStableIdsAndValidate(view);
        return view;
    }

    private void attachStableIdsAndValidate(View view) {
        var resource = new JSONResourceFactory().createResourceFromPath(stableId("ecore-view").toString());
        resource.eAdapters().add(new ResourceMetadataAdapter("Ecore Design"));
        // Description IDs must survive restarts so saved diagrams can find their descriptions.
        view.eAdapters().add(new IDAdapter(stableId("ecore-view")));
        view.eAllContents().forEachRemaining(o -> o.eAdapters().add(new IDAdapter(stableId("ecore-view/" + EcoreUtil.getRelativeURIFragmentPath(view, o)))));
        resource.getContents().add(view);
        var diagnostic = Diagnostician.INSTANCE.validate(view);
        if (diagnostic.getSeverity() >= Diagnostic.ERROR) throw new IllegalStateException(diagnostic.toString());
    }

    private void configureStructuralFeatureLabels(NodeDescription feature) {
        // Conditional styles use first-match priority: error, then derived, then mandatory.
        var mandatory = labelStyle(secondary, false);
        mandatory.setBold(true);
        var mandatoryError = labelStyle(error, false);
        mandatoryError.setBold(true);
        feature.getInsideLabel().getConditionalStyles().add(0, diagramBuilders.newConditionalInsideLabelStyle()
                .condition("aql:self.lowerBound > 0 and self.ecoreHasError()").style(mandatoryError).build());
        feature.getInsideLabel().getConditionalStyles().add(1, diagramBuilders.newConditionalInsideLabelStyle()
                .condition("aql:self.lowerBound > 0").style(mandatory).build());
        var mandatoryDerived = labelStyle(derivedColor, false);
        mandatoryDerived.setBold(true);
        feature.getInsideLabel().getConditionalStyles().add(1, diagramBuilders.newConditionalInsideLabelStyle()
                .condition("aql:self.derived and self.lowerBound > 0 and not self.ecoreHasError()").style(mandatoryDerived).build());
        feature.getInsideLabel().getConditionalStyles().add(2, diagramBuilders.newConditionalInsideLabelStyle()
                .condition("aql:self.derived and not self.ecoreHasError()").style(labelStyle(derivedColor, false)).build());
    }

    private UUID stableId(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)); }
    private FixedColor registerColor(String name, String value) {
        var color = viewBuilders.newFixedColor().name(name).value(value).build();
        colors.add(color);
        return color;
    }
    private RectangularNodeStyleDescription nodeStyle(FixedColor background, FixedColor border, boolean container) {
        // Palette colors are shared objects; identity selects the dedicated error token.
        var style = diagramBuilders.newRectangularNodeStyleDescription().background(background).borderColor(border)
                .borderSize(container || border == error ? 1 : 0).borderRadius(container ? EcoreDiagramStyle.RADIUS : 0);
        if (container) style.childrenLayoutStrategy(diagramBuilders.newListLayoutStrategyDescription().areChildNodesDraggableExpression("aql:false").build());
        return style.build();
    }
    private InsideLabelStyle labelStyle(FixedColor color, boolean header) {
        return diagramBuilders.newInsideLabelStyle().labelColor(color).borderColor(color).borderSize(0).fontSize(header ? EcoreDiagramStyle.TITLE_SIZE : EcoreDiagramStyle.MEMBER_SIZE)
                .showIconExpression("aql:true")
                .withHeader(header).bold(header).headerSeparatorDisplayMode(HeaderSeparatorDisplayMode.IF_CHILDREN).build();
    }
    private NodeDescription node(String type, String candidates, FixedColor background, boolean container) {
        return diagramBuilders.newNodeDescription().name(type).domainType("ecore::" + type).semanticCandidatesExpression(candidates)
                .synchronizationPolicy(container ? SynchronizationPolicy.UNSYNCHRONIZED : SynchronizationPolicy.SYNCHRONIZED)
                .defaultWidthExpression(container ? "180" : "0").defaultHeightExpression(container ? "44" : "0")
                .style(nodeStyle(background, border, container))
                .conditionalStyles(diagramBuilders.newConditionalNodeStyle().condition("aql:self.ecoreHasError()").style(nodeStyle(background, error, container)).build())
                .insideLabel(diagramBuilders.newInsideLabelDescription().labelExpression("aql:self.ecoreLabel()")
                        .position(container ? InsideLabelPosition.TOP_LEFT : InsideLabelPosition.MIDDLE_LEFT)
                        .style(labelStyle(container ? ink : secondary, container))
                        .conditionalStyles(diagramBuilders.newConditionalInsideLabelStyle().condition("aql:self.ecoreHasError()").style(labelStyle(error, container)).build()).build())
                .palette(diagramBuilders.newNodePalette().labelEditTool(labelEditTool()).deleteTool(deleteElementTool()).build()).build();
    }
    private EdgeStyle edgeStyle(FixedColor color, ArrowStyle source, ArrowStyle target) {
        return diagramBuilders.newEdgeStyle().color(color).background(transparent).borderColor(color).borderSize(0).edgeWidth(1).fontSize(EcoreDiagramStyle.EDGE_SIZE)
                .sourceArrowStyle(source).targetArrowStyle(target).edgeType(EdgeType.MANHATTAN).showIcon(false).build();
    }
    /** Ordered by first-match priority: errors, derived references, then ordinary references. */
    private ConditionalEdgeStyle[] referenceEdgeStyles(String mandatory, String derived, String invalid, ArrowStyle source) {
        String required = "aql:(" + mandatory + ")";
        String derivedCondition = "aql:(" + derived + ")";
        String errorCondition = "aql:(" + invalid + ")";
        return new ConditionalEdgeStyle[] {
            conditionalEdge(errorCondition + " and (" + mandatory + ") and self.containment", error, ArrowStyle.FILL_DIAMOND, true),
            conditionalEdge(errorCondition + " and self.containment", error, ArrowStyle.FILL_DIAMOND, false),
            conditionalEdge(errorCondition + " and (" + mandatory + ")", error, source, true),
            conditionalEdge(errorCondition, error, source, false),
            conditionalEdge(derivedCondition + " and (" + mandatory + ") and self.containment", derivedColor, ArrowStyle.FILL_DIAMOND, true),
            conditionalEdge(derivedCondition + " and self.containment", derivedColor, ArrowStyle.FILL_DIAMOND, false),
            conditionalEdge(derivedCondition + " and (" + mandatory + ")", derivedColor, source, true),
            conditionalEdge(derivedCondition, derivedColor, source, false),
            conditionalEdge(required + " and self.containment", relationship, ArrowStyle.FILL_DIAMOND, true),
            conditionalEdge("aql:self.containment", relationship, ArrowStyle.FILL_DIAMOND, false),
            conditionalEdge(required, relationship, source, true)
        };
    }
    private ConditionalEdgeStyle conditionalEdge(String condition, FixedColor color, ArrowStyle source, boolean bold) {
        return diagramBuilders.newConditionalEdgeStyle().condition(condition).color(color).background(transparent).borderColor(color).borderSize(0)
                .edgeWidth(1).fontSize(EcoreDiagramStyle.EDGE_SIZE).bold(bold).sourceArrowStyle(source).targetArrowStyle(ArrowStyle.INPUT_ARROW)
                .edgeType(EdgeType.MANHATTAN).showIcon(false).build();
    }
    private LabelEditTool labelEditTool() {
        return diagramBuilders.newLabelEditTool().name("Edit label").initialDirectEditLabelExpression("aql:self.ecoreLabel()")
                .body(viewBuilders.newChangeContext().expression("aql:self.ecoreEdit(newLabel)").build()).build();
    }
    private NodeTool createNoteTool(boolean constraint) {
        return diagramBuilders.newNodeTool().name(constraint ? "Constraints" : "Documentation")
                .iconURLsExpression("/icons/ecore/EAnnotation.svg")
                .body(viewBuilders.newChangeContext().expression("aql:self.ecoreNote(" + constraint + ")").build()).build();
    }
    private DeleteTool deleteElementTool() {
        return diagramBuilders.newDeleteTool().name("Delete from model").body(viewBuilders.newDeleteElement().build()).build();
    }
    private DeleteTool deleteReferencePairTool() {
        return diagramBuilders.newDeleteTool().name("Delete reference and opposite")
                .body(viewBuilders.newFor().expression("aql:self.ecoreReferencesToDelete()").iteratorName("reference")
                        .children(viewBuilders.newChangeContext().expression("aql:reference").children(viewBuilders.newDeleteElement().build()).build()).build()).build();
    }
    /** A null description creates only the semantic object; synchronized member views appear automatically. */
    private NodeTool createTool(String type, NodeDescription description) {
        var change = viewBuilders.newChangeContext().expression("aql:self.ecoreCreate('" + type + "')");
        if (description != null) change.children(diagramBuilders.newCreateView().elementDescription(description)
                .semanticElementExpression("aql:self").parentViewExpression("aql:selectedNode").containmentKind(NodeContainmentKind.CHILD_NODE).build());
        var elementsToSelect = switch (type) {
            case "EAttribute", "EReference" -> "aql:self.eStructuralFeatures->last()";
            case "EOperation" -> "aql:self.eOperations->last()";
            case "ETypeParameter" -> "aql:self.eTypeParameters->last()";
            case "EEnumLiteral" -> "aql:self.eLiterals->last()";
            default -> null;
        };
        return diagramBuilders.newNodeTool().name("New " + type).iconURLsExpression("/icons/ecore/" + type + ".svg")
                .elementsToSelectExpression(elementsToSelect).body(change.build()).build();
    }
    private Operation[] createImportViewOperations(List<NodeDescription> descriptions, String variable, String parent) {
        var operations = new ArrayList<Operation>();
        for (var description : descriptions) operations.add(viewBuilders.newIf().conditionExpression("aql:" + variable + ".eClass() = ecore::" + description.getName())
                .children(diagramBuilders.newCreateView().elementDescription(description).semanticElementExpression("aql:" + variable)
                        .parentViewExpression(parent).containmentKind(NodeContainmentKind.CHILD_NODE).build()).build());
        return operations.toArray(Operation[]::new);
    }
    private NodeTool createImportTool(List<NodeDescription> descriptions, String name, String candidates, String parent) {
        return diagramBuilders.newNodeTool().name(name).iconURLsExpression("/icons/ecore/EClass.svg")
                .dialogDescription(diagramBuilders.newSelectionDialogDescription().multiple(true).descriptionExpression("Select elements to display")
                        .selectionDialogTreeDescription(diagramBuilders.newSelectionDialogTreeDescription().elementsExpression(candidates)
                                .childrenExpression("aql:Sequence{}").isSelectableExpression("aql:true").build()).build())
                .body(viewBuilders.newFor().expression("aql:selectedObjects").iteratorName("selectedObject").children(createImportViewOperations(descriptions, "selectedObject", parent)).build()).build();
    }
}
