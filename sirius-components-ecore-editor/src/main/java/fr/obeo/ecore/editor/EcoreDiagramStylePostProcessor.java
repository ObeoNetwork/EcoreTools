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
    private final Map<String, String> colors;
    private final EcoreServices services = new EcoreServices();

    public EcoreDiagramStylePostProcessor(@Qualifier("ecoreView") View view, IDiagramIdProvider ids,
            IObjectSearchService objectSearch) {
        this.objectSearch = objectSearch;
        var description = (DiagramDescription) view.getDescriptions().getFirst();
        this.diagramDescriptionId = ids.getId(description);
        this.oppositeDescriptionId = ids.getId(description.getEdgeDescriptions().stream()
                .filter(edge -> "EOpposite".equals(edge.getName())).findFirst().orElseThrow());
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
            var updated = edge;
            if (this.oppositeDescriptionId.equals(edge.getDescriptionId())) {
                var reference = this.objectSearch.getObject(editingContext, edge.getTargetObjectId())
                        .filter(EReference.class::isInstance).map(EReference.class::cast);
                if (reference.isPresent() && reference.get().getEOpposite() != null) {
                    var begin = this.style(edge.getBeginLabel(), reference.get().getEOpposite());
                    var end = this.style(edge.getEndLabel(), reference.get());
                    if (begin != edge.getBeginLabel() || end != edge.getEndLabel()) {
                        updated = Edge.newEdge(edge).beginLabel(begin).endLabel(end).build();
                        changed = true;
                    }
                }
            }
            edges.add(updated);
        }
        return changed ? Optional.of(Diagram.newDiagram(context.diagram()).nodes(nodes).edges(edges).build()) : Optional.empty();
    }

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

    Label style(Label label, EReference reference) {
        if (label == null) return null;
        var previous = label.style();
        String color = label.customizedStyleProperties().contains(LabelAppearanceHandler.COLOR) ? previous.getColor()
                : this.colors.get(this.services.ecoreHasError(reference) ? "Error" : reference.isDerived() ? "Derived" : "Relationship");
        boolean bold = label.customizedStyleProperties().contains(LabelAppearanceHandler.BOLD) ? previous.isBold()
                : reference.getLowerBound() > 0;
        if (color.equals(previous.getColor()) && bold == previous.isBold()) return label;
        var style = LabelStyle.newLabelStyle().color(color).bold(bold)
                .fontSize(previous.getFontSize()).italic(previous.isItalic()).underline(previous.isUnderline())
                .strikeThrough(previous.isStrikeThrough()).iconURL(previous.getIconURL())
                .background(previous.getBackground()).borderColor(previous.getBorderColor())
                .borderSize(previous.getBorderSize()).borderRadius(previous.getBorderRadius())
                .borderStyle(previous.getBorderStyle()).maxWidth(previous.getMaxWidth())
                .visibility(previous.getVisibility()).build();
        return new Label(label.id(), label.type(), label.text(), style, label.customizedStyleProperties());
    }
}
