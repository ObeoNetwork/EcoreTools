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
    private final DiagramBuilders d = new DiagramBuilders();
    private final ViewBuilders v = new ViewBuilders();
    private final List<FixedColor> colors = new ArrayList<>();
    private final FixedColor ink = color("Ink", "#323232");
    private final FixedColor error = color("Error", "#c62828");
    private final FixedColor white = color("White", "#ffffff");
    private final FixedColor classColor = color("EClass", "linear-gradient(180deg, #fffef2 0%, #fffcd8 100%) #fffcd8");
    private final FixedColor abstractColor = color("Abstract EClass", "linear-gradient(180deg, #f7f7f7 0%, #e4e4e4 100%) #e4e4e4");
    private final FixedColor enumColor = color("EEnum", "linear-gradient(180deg, #f2f7e9 0%, #ddecca 100%) #ddecca");
    private final FixedColor dataColor = color("EDataType", "linear-gradient(180deg, #fffdeb 0%, #fffabf 100%) #fffabf");
    private final FixedColor packageColor = color("EPackage", "linear-gradient(180deg, #f2eef4 0%, #d9d2dc 100%) #d9d2dc");
    private final FixedColor noteColor = color("Documentation", "linear-gradient(180deg, #f2f7e7 0%, #dceab7 100%) #dceab7");
    private final FixedColor transparent = color("Transparent", "transparent");
    private final FixedColor commentBorder = color("Comment border", "#888888");
    private final FixedColor constraintColor = color("Constraints", "linear-gradient(180deg, #4676a6 0%, #35628f 100%) #35628f");
    private final FixedColor constraintBorder = color("Constraint border", "#274c72");
    private final FixedColor typeParameterColor = color("Type parameter", "linear-gradient(135deg, #ffffff 0%, #d4e0ee 100%) #d4e0ee");
    private final FixedColor typeParameterBorder = color("Type parameter border", "#5e637a");
    private final FixedColor derivedColor = color("Derived", "#274c72");

    public View create() {
        var clazz = node("EClass", "aql:self.ecoreImportCandidates()->filter(ecore::EClass)", classColor, true);
        clazz.getConditionalStyles().add(d.newConditionalNodeStyle().condition("aql:self.abstract or self.interface")
                .style(style(abstractColor, ink, true)).build());
        var abstractLabel = labelStyle(ink, true);
        abstractLabel.setItalic(true);
        clazz.getInsideLabel().getConditionalStyles().add(d.newConditionalInsideLabelStyle()
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
        for (var feature : List.of(attribute, referenceRow)) {
            var mandatory = labelStyle(ink, false);
            mandatory.setBold(true);
            var mandatoryError = labelStyle(error, false);
            mandatoryError.setBold(true);
            feature.getInsideLabel().getConditionalStyles().add(0, d.newConditionalInsideLabelStyle()
                    .condition("aql:self.lowerBound > 0 and self.ecoreHasError()").style(mandatoryError).build());
            feature.getInsideLabel().getConditionalStyles().add(1, d.newConditionalInsideLabelStyle()
                    .condition("aql:self.lowerBound > 0").style(mandatory).build());
            var mandatoryDerived = labelStyle(derivedColor, false);
            mandatoryDerived.setBold(true);
            feature.getInsideLabel().getConditionalStyles().add(1, d.newConditionalInsideLabelStyle()
                    .condition("aql:self.derived and self.lowerBound > 0 and not self.ecoreHasError()").style(mandatoryDerived).build());
            feature.getInsideLabel().getConditionalStyles().add(2, d.newConditionalInsideLabelStyle()
                    .condition("aql:self.derived and not self.ecoreHasError()").style(labelStyle(derivedColor, false)).build());
        }
        referenceRow.getInsideLabel().setLabelExpression("aql:self.ecoreReferenceRowLabel()");
        referenceRow.getPalette().setLabelEditTool(d.newLabelEditTool().name("Edit reference as attribute")
                .initialDirectEditLabelExpression("aql:self.ecoreReferenceRowLabel()")
                .body(v.newChangeContext().expression("aql:self.ecoreEditReferenceRow(newLabel)").build()).build());
        clazz.getChildrenDescriptions().addAll(List.of(attribute, operation, referenceRow));
        clazz.getBorderNodesDescriptions().add(parameter);
        clazz.getPalette().getNodeTools().addAll(List.of(createTool("EAttribute", null), createTool("EOperation", null), createTool("ETypeParameter", null), createTool("EReference", null)));
        var enumeration = node("EEnum", "aql:self.ecoreImportCandidates()->filter(ecore::EEnum)", enumColor, true);
        enumeration.getChildrenDescriptions().add(node("EEnumLiteral", "aql:self.eLiterals", transparent, false));
        enumeration.getPalette().getNodeTools().add(createTool("EEnumLiteral", null));
        var data = node("EDataType", "aql:self.ecoreImportCandidates()->select(e | e.eClass() = ecore::EDataType)", dataColor, true);
        var instance = node("InstanceClassName", "aql:self", transparent, false);
        instance.setDomainType("ecore::EDataType");
        instance.getInsideLabel().setLabelExpression("aql:self.instanceClassName");
        instance.getInsideLabel().getStyle().setShowIconExpression("aql:false");
        instance.getInsideLabel().getStyle().setItalic(true);
        instance.getPalette().setLabelEditTool(d.newLabelEditTool().name("Edit instance class name")
                .initialDirectEditLabelExpression("aql:self.instanceClassName")
                .body(v.newSetValue().featureName("instanceClassName").valueExpression("aql:newLabel").build()).build());
        instance.getPalette().setDeleteTool(d.newDeleteTool().name("Keep datatype").preconditionExpression("aql:false").build());
        data.getChildrenDescriptions().add(instance);
        var pack = node("EPackage", "aql:self.eSubpackages", packageColor, true);
        pack.getReusedChildNodeDescriptions().addAll(List.of(clazz, enumeration, data, pack));
        var top = List.of(clazz, enumeration, data, pack);
        var note = node("EStringToStringMapEntry", "aql:self.ecoreNotes(diagramContext)", noteColor, false);
        note.setName("Documentation and constraints");
        note.setDefaultWidthExpression("260");
        note.setDefaultHeightExpression("80");
        var commentStyle = (RectangularNodeStyleDescription) note.getStyle();
        commentStyle.setBorderColor(commentBorder);
        commentStyle.setBorderSize(1);
        var constraintStyle = style(constraintColor, constraintBorder, false);
        constraintStyle.setBorderSize(1);
        note.getConditionalStyles().add(d.newConditionalNodeStyle().condition("aql:self.key = 'constraints'")
                .style(constraintStyle).build());
        note.getInsideLabel().setLabelExpression("aql:self.value");
        note.getInsideLabel().setOverflowStrategy(LabelOverflowStrategy.WRAP);
        note.getInsideLabel().getStyle().setShowIconExpression("aql:false");
        var constraintLabel = labelStyle(white, false);
        constraintLabel.setShowIconExpression("aql:false");
        note.getInsideLabel().getConditionalStyles().add(d.newConditionalInsideLabelStyle()
                .condition("aql:self.key = 'constraints'").style(constraintLabel).build());
        note.getPalette().setLabelEditTool(d.newLabelEditTool().name("Edit detail")
                .initialDirectEditLabelExpression("aql:self.value")
                .body(v.newSetValue().featureName("value").valueExpression("aql:newLabel").build()).build());
        var noteTargets = List.of(clazz, enumeration, data, pack, attribute, operation, referenceRow, parameter);
        var relatedImport = importTool(top, "Import related elements", "aql:self.ecoreRelatedElements(null)", "aql:null");
        ((For) relatedImport.getBody().getFirst()).setExpression("aql:selectedObjects->intersection(self.ecoreRelatedElements(diagramContext))");
        for (var target : noteTargets) {
            target.getPalette().getNodeTools().add(EcoreUtil.copy(relatedImport));
            target.getPalette().getQuickAccessTools().add(EcoreUtil.copy(relatedImport));
            for (boolean constraint : List.of(false, true)) {
                target.getPalette().getNodeTools().add(noteTool(constraint));
            }
        }
        var assignNote = d.newEdgeTool().name("Assign annotation").iconURLsExpression("/icons/ecore/EAnnotation.svg")
                .targetElementDescriptions(noteTargets.toArray(NodeDescription[]::new))
                .body(v.newChangeContext().expression("aql:semanticEdgeSource.ecoreAssignNote(semanticEdgeTarget)").build()).build();
        note.getPalette().getEdgeTools().add(assignNote);
        var noteLink = d.newEdgeDescription().name("Annotation assignment").domainType("ecore::EStringToStringMapEntry")
                .centerLabelExpression("")
                .isDomainBasedEdge(true).semanticCandidatesExpression("aql:self.ecoreNotes(diagramContext)")
                .sourceDescriptions(note).targetDescriptions(noteTargets.toArray(NodeDescription[]::new))
                .sourceExpression("aql:self").targetExpression("aql:self.eContainer().eModelElement")
                .style(edgeStyle(ink, ArrowStyle.NONE, ArrowStyle.NONE))
                .palette(d.newEdgePalette().edgeReconnectionTools(d.newTargetEdgeEndReconnectionTool().name("Reassign annotation")
                        .body(v.newChangeContext().expression("aql:edgeSemanticElement.ecoreAssignNote(semanticReconnectionTarget)").build()).build()).build()).build();
        noteLink.getStyle().setLineStyle(LineStyle.DASH);
        for (NodeDescription n : top) {
            n.getPalette().getNodeTools().add(d.newNodeTool().name("Remove from diagram")
                    .body(d.newDeleteView().viewExpression("aql:selectedNode").build()).build());
        }
        var tools = new ArrayList<NodeTool>();
        for (NodeDescription n : top) tools.add(createTool(n.getName(), n));
        tools.add(importTool(top, "Import existing elements", "aql:self.ecoreImportCandidates()", "aql:selectedNode"));
        pack.getPalette().getNodeTools().addAll(tools.stream().map(EcoreUtil::copy).toList());
        tools.add(noteTool(false));
        for (boolean containment : List.of(false, true)) {
            clazz.getPalette().getEdgeTools().add(d.newEdgeTool().name(containment ? "Composition" : "Reference")
                    .targetElementDescriptions(clazz, parameter)
                    .body(v.newChangeContext().expression("aql:semanticEdgeSource.ecoreReference(semanticEdgeTarget, " + containment + ")").build()).build());
        }
        clazz.getPalette().getEdgeTools().add(d.newEdgeTool().name("Supertype").targetElementDescriptions(clazz)
                .body(v.newChangeContext().expression("aql:semanticEdgeSource.ecoreSuperType(semanticEdgeTarget)").build()).build());
        var reference = d.newEdgeDescription().name("EReference").domainType("ecore::EReference")
                .semanticCandidatesExpression("aql:self.ecoreReferences()")
                .preconditionExpression("aql:self.eOpposite = null")
                .isDomainBasedEdge(true).sourceDescriptions(clazz).targetDescriptions(clazz, parameter)
                .sourceExpression("aql:self.eContainingClass").targetExpression("aql:self.ecoreReferenceTarget()")
                .centerLabelExpression("aql:self.ecoreLabel()")
                .style(edgeStyle(ink, ArrowStyle.NONE, ArrowStyle.INPUT_ARROW))
                .conditionalStyles(referenceStyles("self.lowerBound > 0", "self.derived", "self.ecoreHasError()", ArrowStyle.NONE))
                .palette(d.newEdgePalette().centerLabelEditTool(edit()).deleteTool(delete()).edgeReconnectionTools(
                        d.newSourceEdgeEndReconnectionTool().name("Reconnect reference source")
                                .body(v.newChangeContext().expression("aql:edgeSemanticElement.ecoreReconnectReference(semanticReconnectionTarget, true)").build()).build(),
                        d.newTargetEdgeEndReconnectionTool().name("Reconnect reference target")
                                .body(v.newChangeContext().expression("aql:edgeSemanticElement.ecoreReconnectReference(semanticReconnectionTarget, false)").build()).build())
                        .build()).build();
        reference.getPalette().getNodeTools().add(d.newNodeTool().name("Create opposite")
                .iconURLsExpression("/icons/ecore/EReference.svg")
                .preconditionExpression("aql:self.ecoreReferenceTarget().oclIsKindOf(ecore::EClass)")
                .body(v.newChangeContext().expression("aql:self.ecoreCreateOpposite()").build()).build());
        var opposite = EcoreUtil.copy(reference);
        opposite.setName("EOpposite");
        opposite.setPreconditionExpression("aql:self.eOpposite <> null");
        opposite.getTargetDescriptions().remove(parameter);
        opposite.setCenterLabelExpression("");
        opposite.setBeginLabelExpression("aql:self.eOpposite.ecoreLabel()");
        opposite.setEndLabelExpression("aql:self.ecoreLabel()");
        // Sirius' converter enables endpoint editing only if a center edit tool is configured.
        opposite.getPalette().setBeginLabelEditTool(d.newLabelEditTool().name("Edit opposite")
                .initialDirectEditLabelExpression("aql:self.eOpposite.ecoreLabel()")
                .body(v.newChangeContext().expression("aql:self.eOpposite.ecoreEdit(newLabel)").build()).build());
        opposite.getPalette().setEndLabelEditTool(edit());
        opposite.getPalette().getNodeTools().clear();
        opposite.getStyle().setSourceArrowStyle(ArrowStyle.INPUT_ARROW);
        opposite.getConditionalStyles().clear();
        opposite.getConditionalStyles().addAll(List.of(referenceStyles(
                "self.lowerBound > 0 or self.eOpposite.lowerBound > 0", "self.derived or self.eOpposite.derived",
                "self.ecoreHasError() or self.eOpposite.ecoreHasError()", ArrowStyle.INPUT_ARROW)));
        assignNote.getTargetElementDescriptions().addAll(List.of(reference, opposite));
        noteLink.getTargetDescriptions().addAll(List.of(reference, opposite));
        var inheritance = d.newEdgeDescription().name("ESuperTypes").domainType("ecore::EClass")
                .centerLabelExpression("aql:self.ecoreSuperTypeLabel(semanticEdgeTarget)")
                .isDomainBasedEdge(false).sourceDescriptions(clazz).targetDescriptions(clazz)
                .targetExpression("aql:self.ecoreVisibleSuperTypes(diagramContext)").style(edgeStyle(ink, ArrowStyle.NONE, ArrowStyle.INPUT_CLOSED_ARROW))
                .palette(d.newEdgePalette().deleteTool(d.newDeleteTool().name("Remove supertype")
                        .body(v.newChangeContext().expression("aql:semanticEdgeSource.ecoreRemoveSuperType(semanticEdgeTarget)").build()).build())
                        .edgeReconnectionTools(
                                d.newSourceEdgeEndReconnectionTool().name("Reconnect supertype source")
                                        .body(v.newChangeContext().expression("aql:edgeSemanticElement.ecoreReconnectSuperType(semanticOtherEnd, semanticReconnectionTarget, true)").build()).build(),
                                d.newTargetEdgeEndReconnectionTool().name("Reconnect supertype target")
                                        .body(v.newChangeContext().expression("aql:edgeSemanticElement.ecoreReconnectSuperType(semanticReconnectionSource, semanticReconnectionTarget, false)").build()).build())
                        .build()).build();
        inheritance.getStyle().setItalic(true);
        inheritance.getConditionalStyles().add(d.newConditionalEdgeStyle().condition("aql:semanticEdgeTarget.interface")
                .color(ink).borderColor(ink).borderSize(0).edgeWidth(1).fontSize(13).italic(true)
                .sourceArrowStyle(ArrowStyle.NONE).targetArrowStyle(ArrowStyle.INPUT_CLOSED_ARROW)
                .edgeType(EdgeType.MANHATTAN).lineStyle(LineStyle.DASH).showIcon(false).build());
        var diagram = d.newDiagramDescription().name(DIAGRAM_NAME).domainType("ecore::EPackage")
                .iconExpression("aql:'/icons/ecore/EcoreClassDiagram.svg'")
                .style(d.newDiagramStyleDescription().background(white).build())
                .toolbar(d.newDiagramToolbar().expandedByDefault(true).build())
                .titleExpression("aql:self.name + ' class diagram'")
                .nodeDescriptions(clazz, enumeration, data, pack, note).edgeDescriptions(reference, opposite, inheritance, noteLink)
                .palette(d.newDiagramPalette().nodeTools(tools.toArray(NodeTool[]::new))
                        .dropTool(d.newDropTool().name("Import dropped element").body(importViews(top, "self", "aql:selectedNode")).build()).build()).build();
        var view = v.newView().descriptions(diagram).colorPalettes(v.newColorPalette().name("EcoreTools").colors(colors.toArray(FixedColor[]::new)).build()).build();
        var resource = new JSONResourceFactory().createResourceFromPath(id("ecore-view").toString());
        resource.eAdapters().add(new ResourceMetadataAdapter("Ecore Design"));
        view.eAdapters().add(new IDAdapter(id("ecore-view")));
        view.eAllContents().forEachRemaining(o -> o.eAdapters().add(new IDAdapter(id("ecore-view/" + EcoreUtil.getRelativeURIFragmentPath(view, o)))));
        resource.getContents().add(view);
        var diagnostic = Diagnostician.INSTANCE.validate(view);
        if (diagnostic.getSeverity() >= Diagnostic.ERROR) throw new IllegalStateException(diagnostic.toString());
        return view;
    }

    private UUID id(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)); }
    private FixedColor color(String name, String value) {
        var color = v.newFixedColor().name(name).value(value).build(); colors.add(color); return color;
    }
    private RectangularNodeStyleDescription style(FixedColor background, FixedColor border, boolean container) {
        var style = d.newRectangularNodeStyleDescription().background(background).borderColor(border)
                .borderSize(container || border == error ? 1 : 0).borderRadius(container ? 5 : 0);
        if (container) style.childrenLayoutStrategy(d.newListLayoutStrategyDescription().areChildNodesDraggableExpression("aql:false").build());
        return style.build();
    }
    private InsideLabelStyle labelStyle(FixedColor color, boolean header) {
        return d.newInsideLabelStyle().labelColor(color).borderColor(color).borderSize(0).fontSize(14)
                .showIconExpression("aql:true")
                .withHeader(header).bold(header).headerSeparatorDisplayMode(HeaderSeparatorDisplayMode.IF_CHILDREN).build();
    }
    private NodeDescription node(String type, String candidates, FixedColor background, boolean container) {
        return d.newNodeDescription().name(type).domainType("ecore::" + type).semanticCandidatesExpression(candidates)
                .synchronizationPolicy(container ? SynchronizationPolicy.UNSYNCHRONIZED : SynchronizationPolicy.SYNCHRONIZED)
                .defaultWidthExpression(container ? "220" : "180").defaultHeightExpression(container ? "70" : "28")
                .style(style(background, ink, container))
                .conditionalStyles(d.newConditionalNodeStyle().condition("aql:self.ecoreHasError()").style(style(background, error, container)).build())
                .insideLabel(d.newInsideLabelDescription().labelExpression("aql:self.ecoreLabel()")
                        .position(container ? InsideLabelPosition.TOP_CENTER : InsideLabelPosition.MIDDLE_LEFT)
                        .style(labelStyle(ink, container))
                        .conditionalStyles(d.newConditionalInsideLabelStyle().condition("aql:self.ecoreHasError()").style(labelStyle(error, container)).build()).build())
                .palette(d.newNodePalette().labelEditTool(edit()).deleteTool(delete()).build()).build();
    }
    private EdgeStyle edgeStyle(FixedColor color, ArrowStyle source, ArrowStyle target) {
        return d.newEdgeStyle().color(color).borderColor(color).borderSize(0).edgeWidth(1).fontSize(13)
                .sourceArrowStyle(source).targetArrowStyle(target).edgeType(EdgeType.MANHATTAN).showIcon(false).build();
    }
    private ConditionalEdgeStyle[] referenceStyles(String mandatory, String derived, String invalid, ArrowStyle source) {
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
            conditionalEdge(required + " and self.containment", ink, ArrowStyle.FILL_DIAMOND, true),
            conditionalEdge("aql:self.containment", ink, ArrowStyle.FILL_DIAMOND, false),
            conditionalEdge(required, ink, source, true)
        };
    }
    private ConditionalEdgeStyle conditionalEdge(String condition, FixedColor color, ArrowStyle source, boolean bold) {
        return d.newConditionalEdgeStyle().condition(condition).color(color).borderColor(color).borderSize(0)
                .edgeWidth(1).fontSize(13).bold(bold).sourceArrowStyle(source).targetArrowStyle(ArrowStyle.INPUT_ARROW)
                .edgeType(EdgeType.MANHATTAN).showIcon(false).build();
    }
    private LabelEditTool edit() {
        return d.newLabelEditTool().name("Edit label").initialDirectEditLabelExpression("aql:self.ecoreLabel()")
                .body(v.newChangeContext().expression("aql:self.ecoreEdit(newLabel)").build()).build();
    }
    private NodeTool noteTool(boolean constraint) {
        return d.newNodeTool().name(constraint ? "Constraints" : "Documentation")
                .iconURLsExpression("/icons/ecore/EAnnotation.svg")
                .body(v.newChangeContext().expression("aql:self.ecoreNote(" + constraint + ")").build()).build();
    }
    private DeleteTool delete() {
        return d.newDeleteTool().name("Delete from model").body(v.newDeleteElement().build()).build();
    }
    private NodeTool createTool(String type, NodeDescription description) {
        var change = v.newChangeContext().expression("aql:self.ecoreCreate('" + type + "')");
        if (description != null) change.children(d.newCreateView().elementDescription(description)
                .semanticElementExpression("aql:self").parentViewExpression("aql:selectedNode").containmentKind(NodeContainmentKind.CHILD_NODE).build());
        return d.newNodeTool().name("New " + type).iconURLsExpression("/icons/ecore/" + type + ".svg").body(change.build()).build();
    }
    private Operation[] importViews(List<NodeDescription> descriptions, String variable, String parent) {
        var operations = new ArrayList<Operation>();
        for (var description : descriptions) operations.add(v.newIf().conditionExpression("aql:" + variable + ".eClass() = ecore::" + description.getName())
                .children(d.newCreateView().elementDescription(description).semanticElementExpression("aql:" + variable)
                        .parentViewExpression(parent).containmentKind(NodeContainmentKind.CHILD_NODE).build()).build());
        return operations.toArray(Operation[]::new);
    }
    private NodeTool importTool(List<NodeDescription> descriptions, String name, String candidates, String parent) {
        return d.newNodeTool().name(name).iconURLsExpression("/icons/ecore/EClass.svg")
                .dialogDescription(d.newSelectionDialogDescription().multiple(true).descriptionExpression("Select elements to display")
                        .selectionDialogTreeDescription(d.newSelectionDialogTreeDescription().elementsExpression(candidates)
                                .childrenExpression("aql:Sequence{}").isSelectableExpression("aql:true").build()).build())
                .body(v.newFor().expression("aql:selectedObjects").iteratorName("selectedObject").children(importViews(descriptions, "selectedObject", parent)).build()).build();
    }
}
