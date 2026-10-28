/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package fr.obeo.ecore.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.sirius.components.collaborative.workbenchconfiguration.api.IWorkbenchConfigurationCustomizer;
import org.eclipse.sirius.components.collaborative.workbenchconfiguration.dto.DefaultViewConfiguration;
import org.eclipse.sirius.components.collaborative.workbenchconfiguration.dto.WorkbenchConfiguration;
import org.eclipse.sirius.components.collaborative.workbenchconfiguration.dto.WorkbenchSidePanelConfiguration;
import org.eclipse.sirius.components.core.RepresentationMetadata;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextRepresentationDescriptionProvider;
import org.eclipse.sirius.components.core.api.IRepresentationMetadataProvider;
import org.eclipse.sirius.components.forms.Form;
import org.eclipse.sirius.components.representations.IRepresentationDescription;

/** Registers the documentation form and its workbench view. */
public class EcoreDocumentationConfiguration implements IWorkbenchConfigurationCustomizer,
        IEditingContextRepresentationDescriptionProvider, IRepresentationMetadataProvider {
    private final EcoreDocumentationPropertiesConfigurer documentation;

    public EcoreDocumentationConfiguration(EcoreDocumentationPropertiesConfigurer documentation) {
        this.documentation = documentation;
    }

    @Override
    public WorkbenchConfiguration customize(String editingContextId, WorkbenchConfiguration configuration) {
        var panels = configuration.workbenchPanels().stream().map(panel -> {
            if (!"right".equals(panel.id()) || panel.views().stream().anyMatch(view -> "ecore-documentation".equals(view.id()))) return panel;
            var views = new ArrayList<>(panel.views());
            views.add(new DefaultViewConfiguration("ecore-documentation", false));
            return new WorkbenchSidePanelConfiguration(panel.id(), panel.isOpen(), views);
        }).toList();
        return new WorkbenchConfiguration(configuration.mainPanel(), panels);
    }

    @Override
    public List<IRepresentationDescription> getRepresentationDescriptions(IEditingContext editingContext) {
        return List.of(this.documentation.formDescription());
    }

    @Override
    public Optional<RepresentationMetadata> getMetadata(String editingContextId, String representationId) {
        if (!representationId.startsWith(EcoreDocumentationEventProcessorFactory.PREFIX)) return Optional.empty();
        return Optional.of(RepresentationMetadata.newRepresentationMetadata(representationId).kind(Form.KIND)
                .label("Documentation").descriptionId(EcoreDocumentationPropertiesConfigurer.DESCRIPTION_ID).iconURLs(List.of()).build());
    }
}
