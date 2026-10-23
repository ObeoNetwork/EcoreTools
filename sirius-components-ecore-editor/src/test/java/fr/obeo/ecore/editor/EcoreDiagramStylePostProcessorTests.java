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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import java.util.List;
import java.util.Set;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.sirius.components.collaborative.diagrams.RectangularNodeAppearanceHandler;
import org.eclipse.sirius.components.core.api.IObjectSearchService;
import org.eclipse.sirius.components.diagrams.CollapsingState;
import org.eclipse.sirius.components.diagrams.FreeFormLayoutStrategy;
import org.eclipse.sirius.components.diagrams.Label;
import org.eclipse.sirius.components.diagrams.LabelStyle;
import org.eclipse.sirius.components.diagrams.LabelVisibility;
import org.eclipse.sirius.components.diagrams.LineStyle;
import org.eclipse.sirius.components.diagrams.ListLayoutStrategy;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.components.diagrams.RectangularNodeStyle;
import org.eclipse.sirius.components.diagrams.ViewModifier;
import org.eclipse.sirius.components.diagrams.components.BorderNodePosition;
import org.eclipse.sirius.components.diagrams.renderer.LabelAppearanceHandler;
import org.eclipse.sirius.components.view.emf.diagram.IDiagramIdProvider;
import org.junit.jupiter.api.Test;

class EcoreDiagramStylePostProcessorTests {
    @Test
    void cardBackgroundFollowsDisplayedChildrenAndKeepsUserCustomizations() {
        var processor = new EcoreDiagramStylePostProcessor(new EcoreViews().create(),
                new IDiagramIdProvider.NoOp(), mock(IObjectSearchService.class));
        var card = node("class", true);
        var member = node("attribute", false);
        var empty = processor.style(card);
        assertThat(((RectangularNodeStyle) empty.getStyle()).getBackground()).isEqualTo(EcoreDiagramStyle.HEADER_BACKGROUND);
        assertThat(processor.style(empty)).isSameAs(empty);
        var withPort = processor.style(Node.newNode(empty).borderNodes(List.of(member)).build());
        assertThat(((RectangularNodeStyle) withPort.getStyle()).getBackground()).isEqualTo(EcoreDiagramStyle.HEADER_BACKGROUND);
        for (var state : List.of(ViewModifier.Normal, ViewModifier.Faded, ViewModifier.Hidden)) {
            var child = Node.newNode(member).state(state).build();
            var populated = processor.style(Node.newNode(empty).childNodes(List.of(child)).build());
            assertThat(((RectangularNodeStyle) populated.getStyle()).getBackground())
                    .isEqualTo(state == ViewModifier.Hidden ? EcoreDiagramStyle.HEADER_BACKGROUND : EcoreDiagramStyle.SURFACE);
            assertThat(((RectangularNodeStyle) populated.getStyle()).getBorderColor()).isEqualTo(EcoreDiagramStyle.ERROR);
            var cleared = processor.style(Node.newNode(populated).childNodes(List.of()).build());
            assertThat(((RectangularNodeStyle) cleared.getStyle()).getBackground()).isEqualTo(EcoreDiagramStyle.HEADER_BACKGROUND);
        }
        var nested = processor.style(Node.newNode(node("package", true)).childNodes(List.of(card)).build());
        assertThat(((RectangularNodeStyle) nested.getStyle()).getBackground()).isEqualTo(EcoreDiagramStyle.SURFACE);
        assertThat(((RectangularNodeStyle) nested.getChildNodes().getFirst().getStyle()).getBackground())
                .isEqualTo(EcoreDiagramStyle.HEADER_BACKGROUND);
        var customized = Node.newNode(card).style(RectangularNodeStyle.newRectangularNodeStyle((RectangularNodeStyle) card.getStyle())
                .background("#abcdef").build()).customizedStyleProperties(Set.of(RectangularNodeAppearanceHandler.BACKGROUND)).build();
        assertThat(processor.style(customized)).isSameAs(customized);
        assertThat(processor.style(member)).isSameAs(member);
    }

    private Node node(String id, boolean card) {
        return Node.newNode(id).type(card ? "listNode" : "freeFormNode").targetObjectId(id).targetObjectKind("ecore")
                .targetObjectLabel(id).descriptionId(id).initialBorderNodePosition(BorderNodePosition.NONE)
                .state(ViewModifier.Normal).modifiers(Set.of()).collapsingState(CollapsingState.EXPANDED)
                .outsideLabels(List.of()).borderNodes(List.of()).childNodes(List.of()).decorators(List.of())
                .customizedStyleProperties(Set.of()).style(RectangularNodeStyle.newRectangularNodeStyle()
                        .background(EcoreDiagramStyle.SURFACE).borderColor(EcoreDiagramStyle.ERROR).borderSize(1).borderRadius(6)
                        .borderStyle(LineStyle.Solid).childrenLayoutStrategy(card ? ListLayoutStrategy.newListLayoutStrategy().build()
                                : new FreeFormLayoutStrategy()).build()).build();
    }

    @Test
    void endpointsHaveIndependentStylesAndPreserveCustomizations() {
        var services = new EcoreServices();
        var pack = services.ecoreExample();
        var reference = ((EClass) pack.getEClassifier("Library")).getEReferences().getFirst();
        reference.setContainment(false);
        reference.setLowerBound(1);
        services.ecoreCreateOpposite(reference);
        var opposite = reference.getEOpposite();
        var processor = new EcoreDiagramStylePostProcessor(new EcoreViews().create(),
                new IDiagramIdProvider.NoOp(), mock(IObjectSearchService.class));
        var original = new Label("label", "edge", "text", LabelStyle.newLabelStyle()
                .color("#63708a").fontSize(17).italic(true).underline(true).iconURL(List.of())
                .background("transparent").borderColor("transparent").borderStyle(LineStyle.Solid)
                .visibility(LabelVisibility.visible).build(), Set.of());
        for (boolean derived : new boolean[]{false, true}) {
            for (boolean invalid : new boolean[]{false, true}) {
                opposite.setDerived(derived);
                opposite.setName(invalid ? "invalid name" : "owner");
                var begin = processor.style(original, opposite);
                var end = processor.style(original, reference);
                assertThat(begin.style().isBold()).isFalse();
                assertThat(begin.style().getColor()).isEqualTo(invalid ? "#c62828" : derived ? "#2d6f9f" : "#63708a");
                assertThat(end.style().isBold()).isTrue();
                assertThat(end.style().getColor()).isEqualTo("#63708a");
                assertThat(begin.style().getFontSize()).isEqualTo(17);
                assertThat(begin.style().isItalic()).isTrue();
                assertThat(begin.style().isUnderline()).isTrue();
                assertThat(processor.style(begin, opposite)).isSameAs(begin);
            }
        }
        var customized = new Label(original.id(), original.type(), original.text(), original.style(),
                Set.of(LabelAppearanceHandler.COLOR, LabelAppearanceHandler.BOLD));
        assertThat(processor.style(customized, reference)).isSameAs(customized);
        assertThat(processor.style(customized, opposite)).isSameAs(customized);
        assertThat(processor.style(null, reference)).isNull();
    }
}
