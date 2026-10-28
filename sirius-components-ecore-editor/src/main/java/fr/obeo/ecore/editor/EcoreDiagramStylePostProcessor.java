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

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.collaborative.diagrams.RectangularNodeAppearanceHandler;
import org.eclipse.sirius.components.collaborative.diagrams.api.IDiagramPostProcessor;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IObjectSearchService;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.Edge;
import org.eclipse.sirius.components.diagrams.Label;
import org.eclipse.sirius.components.diagrams.LabelStyle;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.components.diagrams.RectangularNodeStyle;
import org.eclipse.sirius.components.diagrams.ViewModifier;
import org.eclipse.sirius.components.diagrams.renderer.LabelAppearanceHandler;
import org.eclipse.sirius.components.view.FixedColor;
import org.eclipse.sirius.components.view.View;
import org.eclipse.sirius.components.view.diagram.DiagramDescription;
import org.eclipse.sirius.components.view.emf.diagram.IDiagramIdProvider;
import org.springframework.beans.factory.annotation.Qualifier;

/** Styles that depend on the rendered children or on an opposite reference at each endpoint. */
public class EcoreDiagramStylePostProcessor implements IDiagramPostProcessor {
    private final IObjectSearchService objectSearch;
    private final String diagramDescriptionId;
    private final String oppositeDescriptionId;
    private final String referenceDescriptionId;
    private final Map<String, String> colors;
    private final EcoreServices services = new EcoreServices();

    public EcoreDiagramStylePostProcessor(@Qualifier("ecoreView") View view, IDiagramIdProvider ids,
            IObjectSearchService objectSearch) {
        this.objectSearch = objectSearch;
        var description = (DiagramDescription) view.getDescriptions().getFirst();
        this.diagramDescriptionId = ids.getId(description);
        this.oppositeDescriptionId = ids.getId(description.getEdgeDescriptions().stream()
                .filter(edge -> "EOpposite".equals(edge.getName())).findFirst().orElseThrow());
        this.referenceDescriptionId = ids.getId(description.getEdgeDescriptions().stream()
                .filter(edge -> "EReference".equals(edge.getName())).findFirst().orElseThrow());
        this.colors = view.getColorPalettes().stream().flatMap(palette -> palette.getColors().stream())
                .filter(FixedColor.class::isInstance).map(FixedColor.class::cast)
                .collect(Collectors.toMap(FixedColor::getName, FixedColor::getValue));
    }

    @Override
    public boolean canHandle(IEditingContext editingContext, DiagramContext context) {
        return this.diagramDescriptionId.equals(context.diagram().getDescriptionId());
    }

    @Override
    public Optional<Diagram> postProcess(IEditingContext editingContext, DiagramContext context) {
        var edges = new ArrayList<Edge>();
        var nodes = context.diagram().getNodes().stream().map(this::style).toList();
        boolean changed = !nodes.equals(context.diagram().getNodes());
        for (var edge : context.diagram().getEdges()) {
            var styledEdge = this.styleReferenceLabels(editingContext, edge);
            changed |= styledEdge != edge;
            edges.add(styledEdge);
        }
        return changed ? Optional.of(Diagram.newDiagram(context.diagram()).nodes(nodes).edges(edges).build()) : Optional.empty();
    }

    private Edge styleReferenceLabels(IEditingContext editingContext, Edge edge) {
        if (!this.oppositeDescriptionId.equals(edge.getDescriptionId()) && !this.referenceDescriptionId.equals(edge.getDescriptionId())) {
            return edge;
        }
        var reference = this.objectSearch.getObject(editingContext, edge.getTargetObjectId())
                .filter(EReference.class::isInstance).map(EReference.class::cast);
        if (reference.isEmpty()) {
            return edge;
        }
        if (this.referenceDescriptionId.equals(edge.getDescriptionId())) {
            var centerLabel = this.style(edge.getCenterLabel(), reference.get());
            return centerLabel != edge.getCenterLabel() ? Edge.newEdge(edge).centerLabel(centerLabel).build() : edge;
        }
        if (reference.get().getEOpposite() == null) return edge;
        var beginLabel = this.style(edge.getBeginLabel(), reference.get().getEOpposite());
        var endLabel = this.style(edge.getEndLabel(), reference.get());
        // Styling returns the original objects when unchanged, so identity avoids unnecessary diagram updates.
        return beginLabel != edge.getBeginLabel() || endLabel != edge.getEndLabel()
                ? Edge.newEdge(edge).beginLabel(beginLabel).endLabel(endLabel).build() : edge;
    }

    /** Recursively styles child nodes, preserving the original node when nothing changes. */
    Node style(Node node) {
        var children = node.getChildNodes().stream().map(this::style).toList();
        var style = node.getStyle();
        if (style instanceof RectangularNodeStyle rectangle && node.getInsideLabel() != null && node.getInsideLabel().isIsHeader()
                && !node.getCustomizedStyleProperties().contains(RectangularNodeAppearanceHandler.BACKGROUND)) {
            // Border ports do not open a content compartment; only displayed children do.
            String background = children.stream().anyMatch(child -> child.getState() != ViewModifier.Hidden)
                    ? EcoreDiagramStyle.CONTENT_BACKGROUND : EcoreDiagramStyle.HEADER_BACKGROUND;
            if (!background.equals(rectangle.getBackground())) {
                style = RectangularNodeStyle.newRectangularNodeStyle(rectangle).background(background).build();
            }
        }
        return style != node.getStyle() || !children.equals(node.getChildNodes())
                ? Node.newNode(node).style(style).childNodes(children).build() : node;
    }

    /** Preserves user overrides and returns the original label when unchanged; a missing label stays null. */
    Label style(Label label, EReference reference) {
        if (label == null) {
            return null;
        }
        var previousStyle = label.style();
        String color = label.customizedStyleProperties().contains(LabelAppearanceHandler.COLOR) ? previousStyle.getColor()
                : this.colors.get(this.services.ecoreHasError(reference) ? "Error" : reference.isDerived() ? "Derived" : "Ink");
        boolean bold = label.customizedStyleProperties().contains(LabelAppearanceHandler.BOLD) ? previousStyle.isBold()
                : reference.getLowerBound() > 0;
        if (color.equals(previousStyle.getColor()) && bold == previousStyle.isBold()) {
            return label;
        }
        var style = LabelStyle.newLabelStyle().color(color).bold(bold)
                .fontSize(previousStyle.getFontSize()).italic(previousStyle.isItalic()).underline(previousStyle.isUnderline())
                .strikeThrough(previousStyle.isStrikeThrough()).iconURL(previousStyle.getIconURL())
                .background(previousStyle.getBackground()).borderColor(previousStyle.getBorderColor())
                .borderSize(previousStyle.getBorderSize()).borderRadius(previousStyle.getBorderRadius())
                .borderStyle(previousStyle.getBorderStyle()).maxWidth(previousStyle.getMaxWidth())
                .visibility(previousStyle.getVisibility()).build();
        return new Label(label.id(), label.type(), label.text(), style, label.customizedStyleProperties());
    }
}
