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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.eclipse.sirius.components.view.FixedColor;
import org.eclipse.sirius.components.view.diagram.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.function.ServerRequest;

class EcoreDiagramStyleTests {
    @Test
    void paletteMeetsTextAndNonTextContrastOnDiagramSurfaces() {
        for (String background : List.of(EcoreDiagramStyle.SURFACE, EcoreDiagramStyle.CANVAS,
                "#f9f9fc", "#f6fafa", "#f7f9fb", "#faf8fc")) {
            for (String text : List.of(EcoreDiagramStyle.TEXT, EcoreDiagramStyle.SECONDARY, EcoreDiagramStyle.RELATIONSHIP,
                    EcoreDiagramStyle.CLASS, EcoreDiagramStyle.ENUM, EcoreDiagramStyle.DATATYPE, EcoreDiagramStyle.PACKAGE, EcoreDiagramStyle.ERROR)) {
                assertThat(contrast(text, background)).as("%s on %s", text, background).isGreaterThanOrEqualTo(4.5);
            }
            assertThat(contrast(EcoreDiagramStyle.BORDER, background)).isGreaterThanOrEqualTo(3);
        }
        for (String header : List.of("#e9e9f3", "#deeceb", "#e2ebf2", "#ede8f3")) {
            assertThat(contrast(EcoreDiagramStyle.TEXT, header)).isGreaterThanOrEqualTo(4.5);
            assertThat(contrast(EcoreDiagramStyle.BORDER, header)).isGreaterThanOrEqualTo(3);
        }
        assertThat(contrast(EcoreDiagramStyle.SECONDARY, EcoreDiagramStyle.NOTE)).isGreaterThanOrEqualTo(4.5);
        assertThat(contrast(EcoreDiagramStyle.DATATYPE, EcoreDiagramStyle.CONSTRAINT)).isGreaterThanOrEqualTo(4.5);
    }

    @Test
    void cardsUseCompactNeutralStylesAndKeepNativeLayoutAndHeaderStructure() {
        var view = new EcoreViews().create();
        view.getColorPalettes().forEach(palette -> palette.getColors().forEach(color ->
                assertThat(((FixedColor) color).getValue()).doesNotContain("gradient")));
        var diagram = (DiagramDescription) view.getDescriptions().getFirst();
        assertThat(((FixedColor) diagram.getStyle().getBackground()).getValue()).isEqualTo("var(--ecore-canvas, " + EcoreDiagramStyle.CANVAS + ")");
        for (var node : diagram.getNodeDescriptions().subList(0, 4)) {
            var style = (RectangularNodeStyleDescription) node.getStyle();
            assertThat(((FixedColor) style.getBackground()).getValue()).isEqualTo(EcoreDiagramStyle.SURFACE);
            assertThat(style.getBorderSize()).isEqualTo(1);
            assertThat(style.getBorderRadius()).isEqualTo(6);
            assertThat(style.getChildrenLayoutStrategy()).isInstanceOf("EPackage".equals(node.getName())
                    ? FreeFormLayoutStrategyDescription.class : ListLayoutStrategyDescription.class);
            assertThat(node.getInsideLabel().getPosition()).isEqualTo(InsideLabelPosition.TOP_LEFT);
            assertThat(node.getInsideLabel().getStyle().getFontSize()).isEqualTo(14);
            assertThat(node.getInsideLabel().getStyle().isBold()).isTrue();
            assertThat(Integer.parseInt(node.getDefaultHeightExpression())).isLessThanOrEqualTo("EPackage".equals(node.getName()) ? 240 : 44);
            for (var member : node.getChildrenDescriptions()) {
                assertThat(member.getInsideLabel().getStyle().getFontSize()).isEqualTo(13);
                assertThat(member.getInsideLabel().getStyle().isBold()).isFalse();
            }
        }
        for (var edge : diagram.getEdgeDescriptions()) {
            assertThat(edge.getStyle().getFontSize()).isEqualTo(12);
            assertThat(edge.getStyle().isShowIcon()).isFalse();
            assertThat(edge.getPalette()).isNotNull();
            assertThat(((FixedColor) edge.getStyle().getBackground()).getValue()).isEqualTo("transparent");
            edge.getConditionalStyles().forEach(style ->
                    assertThat(((FixedColor) style.getBackground()).getValue()).isEqualTo("transparent"));
        }
    }

    @Test
    void stylesheetIsPackagedAndInstalledThroughTheNativeIndexHook() throws Exception {
        var request = mock(ServerRequest.class);
        var servlet = mock(HttpServletRequest.class);
        when(request.servletRequest()).thenReturn(servlet);
        when(servlet.getContextPath()).thenReturn("/modeler");
        assertThat(new EcoreDiagramStyles().process(request, "<html><head></head><body></body></html>"))
                .contains("href=\"/modeler/ecore-editor/diagram.css\"");
        try (var css = getClass().getResourceAsStream("/static/ecore-editor/diagram.css")) {
            assertThat(css).isNotNull();
            assertThat(new String(css.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .contains(".react-flow:has([data-testid=\"rf__background\"][style*=\"--ecore-canvas\"])", ":focus-visible", "outline: 2px solid",
                            "border-left: 4px solid var(--ecore-type-accent)", EcoreDiagramStyle.CLASS, EcoreDiagramStyle.ENUM,
                            EcoreDiagramStyle.DATATYPE, EcoreDiagramStyle.PACKAGE, "img[src$=\"/EClass_interface.svg\"]");
        }
    }

    private double contrast(String foreground, String background) {
        double first = luminance(foreground), second = luminance(background);
        return (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
    }

    private double luminance(String hex) {
        double[] weights = {.2126, .7152, .0722};
        double result = 0;
        for (int channel = 0; channel < 3; channel++) {
            double value = Integer.parseInt(hex.substring(1 + 2 * channel, 3 + 2 * channel), 16) / 255.0;
            result += weights[channel] * (value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4));
        }
        return result;
    }
}
