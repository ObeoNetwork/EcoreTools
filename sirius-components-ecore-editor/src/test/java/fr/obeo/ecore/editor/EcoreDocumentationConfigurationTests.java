/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package fr.obeo.ecore.editor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.eclipse.sirius.components.collaborative.workbenchconfiguration.dto.DefaultViewConfiguration;
import org.eclipse.sirius.components.collaborative.workbenchconfiguration.dto.WorkbenchConfiguration;
import org.eclipse.sirius.components.collaborative.workbenchconfiguration.dto.WorkbenchMainPanelConfiguration;
import org.eclipse.sirius.components.collaborative.workbenchconfiguration.dto.WorkbenchSidePanelConfiguration;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.junit.jupiter.api.Test;

class EcoreDocumentationConfigurationTests {
    @Test
    void addsOneRightViewWithoutChangingExistingConfiguration() {
        var documentation = new EcoreDocumentationPropertiesConfigurer(mock(IIdentityService.class), object -> false);
        var customizer = new EcoreDocumentationConfiguration(documentation);
        var left = new WorkbenchSidePanelConfiguration("left", true, List.of(new DefaultViewConfiguration("studio", true)));
        var right = new WorkbenchSidePanelConfiguration("right", true, List.of(new DefaultViewConfiguration("details", true)));
        var original = new WorkbenchConfiguration(new WorkbenchMainPanelConfiguration("main", List.of()), List.of(left, right));
        var updated = customizer.customize("context", original);
        assertThat(updated.workbenchPanels().getFirst()).isEqualTo(left);
        assertThat(updated.workbenchPanels().getLast().views()).extracting(view -> view.id()).containsExactly("details", "ecore-documentation");
        assertThat(customizer.customize("context", updated)).isEqualTo(updated);
        assertThat(customizer.getMetadata("context", "documentation://?objectIds=[x]")).isPresent();
        assertThat(customizer.getMetadata("context", "details://?objectIds=[x]")).isEmpty();
    }
}
