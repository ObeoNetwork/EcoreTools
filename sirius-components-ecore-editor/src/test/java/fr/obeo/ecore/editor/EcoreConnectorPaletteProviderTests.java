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
import static org.mockito.Mockito.*;
import java.util.List;
import org.eclipse.sirius.components.collaborative.diagrams.dto.SingleClickOnTwoDiagramElementsCandidate;
import org.eclipse.sirius.components.collaborative.diagrams.dto.SingleClickOnTwoDiagramElementsTool;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.diagrams.description.IDiagramElementDescription;
import org.eclipse.sirius.components.diagrams.description.NodeDescription;
import org.eclipse.sirius.components.palette.dto.Palette;
import org.eclipse.sirius.components.palette.dto.PaletteDivider;
import org.eclipse.sirius.components.palette.dto.ToolSection;
import org.eclipse.sirius.components.view.emf.diagram.IDiagramIdProvider;
import org.eclipse.sirius.components.view.emf.diagram.ViewConnectorPaletteProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EcoreConnectorPaletteProviderTests {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void connectorMenuFiltersActualTargetsIncludingToolsInSections(boolean genericTarget) {
        var view = new EcoreViews().create();
        var ids = mock(IDiagramIdProvider.class);
        when(ids.getId((org.eclipse.sirius.components.view.diagram.DiagramDescription) view.getDescriptions().getFirst()))
                .thenReturn("ecore-diagram");
        var delegate = mock(ViewConnectorPaletteProvider.class);
        var provider = new EcoreConnectorPaletteProvider(delegate, view, ids);
        var diagram = mock(DiagramDescription.class);
        when(diagram.getId()).thenReturn("ecore-diagram");
        assertThat(provider.canHandle(diagram)).isTrue();
        var otherDiagram = mock(DiagramDescription.class);
        when(otherDiagram.getId()).thenReturn("other-diagram");
        assertThat(provider.canHandle(otherDiagram)).isFalse();
        var clazz = mock(NodeDescription.class);
        var parameter = mock(NodeDescription.class);
        when(clazz.getId()).thenReturn("class");
        when(parameter.getId()).thenReturn("parameter");
        var reference = tool("Reference", clazz, List.of(clazz, parameter));
        var composition = tool("Composition", clazz, List.of(clazz, parameter));
        var supertype = tool("Supertype", clazz, List.of(clazz));
        var section = new ToolSection("relations", "Relations", List.of("icon"), List.of(reference, composition, supertype));
        var divider = new PaletteDivider("divider");
        var palette = new Palette("palette", List.of(supertype), List.of(section, supertype, divider));
        var target = genericTarget ? parameter : clazz;
        when(delegate.handle(null, null, diagram, null, null, clazz, target)).thenReturn(palette);
        var result = provider.handle(null, null, diagram, null, null, clazz, target);
        var filteredSection = (ToolSection) result.paletteEntries().getFirst();
        assertThat(filteredSection.tools()).containsExactlyElementsOf(genericTarget
                ? List.of(reference, composition) : List.of(reference, composition, supertype));
        assertThat(result.paletteEntries()).containsExactlyElementsOf(genericTarget
                ? List.of(filteredSection, divider) : List.of(filteredSection, supertype, divider));
        assertThat(result.quickAccessTools()).containsExactlyElementsOf(genericTarget ? List.of() : List.of(supertype));
        assertThat(section.tools()).containsExactly(reference, composition, supertype);
    }

    private static SingleClickOnTwoDiagramElementsTool tool(String name, IDiagramElementDescription source,
            List<IDiagramElementDescription> targets) {
        return new SingleClickOnTwoDiagramElementsTool(name, name, List.of(),
                List.of(new SingleClickOnTwoDiagramElementsCandidate(List.of(source), targets)), "");
    }
}
