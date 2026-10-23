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

import java.util.Objects;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.collaborative.diagrams.api.IConnectorPaletteProvider;
import org.eclipse.sirius.components.collaborative.diagrams.dto.SingleClickOnTwoDiagramElementsTool;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.diagrams.description.IDiagramElementDescription;
import org.eclipse.sirius.components.palette.dto.IPaletteEntry;
import org.eclipse.sirius.components.palette.dto.ITool;
import org.eclipse.sirius.components.palette.dto.Palette;
import org.eclipse.sirius.components.palette.dto.ToolSection;
import org.eclipse.sirius.components.view.View;
import org.eclipse.sirius.components.view.emf.diagram.IDiagramIdProvider;
import org.eclipse.sirius.components.view.emf.diagram.ViewConnectorPaletteProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/** Sirius 2026.7's connector palette does not filter tools against the chosen target description. */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EcoreConnectorPaletteProvider implements IConnectorPaletteProvider {
    private final ViewConnectorPaletteProvider delegate;
    private final String descriptionId;

    public EcoreConnectorPaletteProvider(ViewConnectorPaletteProvider delegate, @Qualifier("ecoreView") View view,
            IDiagramIdProvider ids) {
        this.delegate = delegate;
        this.descriptionId = ids.getId((org.eclipse.sirius.components.view.diagram.DiagramDescription) view.getDescriptions().getFirst());
    }

    @Override
    public boolean canHandle(DiagramDescription description) {
        return this.descriptionId.equals(description.getId());
    }

    @Override
    public Palette handle(IEditingContext editingContext, DiagramContext context, DiagramDescription description,
            Object sourceElement, Object targetElement, Object sourceDescription, Object targetDescription) {
        var palette = this.delegate.handle(editingContext, context, description,
                sourceElement, targetElement, sourceDescription, targetDescription);
        if (!(sourceDescription instanceof IDiagramElementDescription source)
                || !(targetDescription instanceof IDiagramElementDescription target) || palette == null) return palette;
        var entries = palette.paletteEntries().stream().map(entry -> this.filter(entry, source.getId(), target.getId()))
                .filter(Objects::nonNull).toList();
        return new Palette(palette.id(), palette.quickAccessTools().stream()
                .filter(tool -> this.accepts(tool, source.getId(), target.getId())).toList(), entries);
    }

    private IPaletteEntry filter(IPaletteEntry entry, String source, String target) {
        if (entry instanceof ToolSection section) {
            var tools = section.tools().stream().filter(tool -> this.accepts(tool, source, target)).toList();
            return tools.isEmpty() ? null : new ToolSection(section.id(), section.label(), section.iconURL(), tools);
        }
        return entry instanceof ITool tool && !this.accepts(tool, source, target) ? null : entry;
    }

    private boolean accepts(ITool tool, String source, String target) {
        return !(tool instanceof SingleClickOnTwoDiagramElementsTool edgeTool) || edgeTool.candidates().stream()
                .anyMatch(candidate -> candidate.sources().stream().anyMatch(node -> source.equals(node.getId()))
                        && candidate.targets().stream().anyMatch(node -> target.equals(node.getId())));
    }
}
