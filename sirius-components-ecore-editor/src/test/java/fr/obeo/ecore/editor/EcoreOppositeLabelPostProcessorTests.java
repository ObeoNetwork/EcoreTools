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
import org.eclipse.sirius.components.core.api.IObjectSearchService;
import org.eclipse.sirius.components.diagrams.Label;
import org.eclipse.sirius.components.diagrams.LabelStyle;
import org.eclipse.sirius.components.diagrams.LabelVisibility;
import org.eclipse.sirius.components.diagrams.LineStyle;
import org.eclipse.sirius.components.diagrams.renderer.LabelAppearanceHandler;
import org.eclipse.sirius.components.view.emf.diagram.IDiagramIdProvider;
import org.junit.jupiter.api.Test;

class EcoreOppositeLabelPostProcessorTests {
    @Test
    void endpointsHaveIndependentStylesAndPreserveCustomizations() {
        var services = new EcoreServices();
        var pack = services.ecoreExample();
        var reference = ((EClass) pack.getEClassifier("Library")).getEReferences().getFirst();
        reference.setContainment(false);
        reference.setLowerBound(1);
        services.ecoreCreateOpposite(reference);
        var opposite = reference.getEOpposite();
        var processor = new EcoreOppositeLabelPostProcessor(new EcoreViews().create(),
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
