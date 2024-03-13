/*******************************************************************************
 * Copyright (c) 2024 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.components.ecore.starter.configuration;

import java.util.List;

import org.eclipse.sirius.web.services.api.projects.IProjectTemplateProvider;
import org.eclipse.sirius.web.services.api.projects.ProjectTemplate;
import org.springframework.context.annotation.Configuration;

/**
 * Provides Ecore-specific project templates.
 *
 * @author adieumegard
 */
@Configuration
public class EcoreProjectTemplatesProvider implements IProjectTemplateProvider {

    public static final String ECORE_TEMPLATE_ID = "ecore-template";

    @Override
    public List<ProjectTemplate> getProjectTemplates() {
        var ecoreTemplate = ProjectTemplate.newProjectTemplate(ECORE_TEMPLATE_ID)
                .label("Ecore")
                .imageURL("/images/Ecore-Template.png")
                .natures(List.of())
                .build();
        return List.of(ecoreTemplate);
    }

}
