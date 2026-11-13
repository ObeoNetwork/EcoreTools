/*******************************************************************************
 * Copyright (c) 2026 Obeo.
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
package fr.obeo.ecore.application;

import java.util.List;

import org.eclipse.sirius.web.application.project.services.api.IProjectTemplateProvider;
import org.eclipse.sirius.web.application.project.services.api.ProjectTemplate;
import org.eclipse.sirius.web.application.project.services.api.ProjectTemplateNature;
import org.eclipse.sirius.web.application.studio.services.StudioProjectTemplateProvider;
import org.springframework.stereotype.Service;

/**
 * Offers the EcoreTools example when creating a project.
 */
@Service
public class EcoreProjectTemplateProvider implements IProjectTemplateProvider {

    public static final String TEMPLATE_ID = "Ecore example";

    @Override
    public List<ProjectTemplate> getProjectTemplates() {
        return List.of(new ProjectTemplate(TEMPLATE_ID, "Example", "/project-templates/ecore-example.svg",
                List.of(new ProjectTemplateNature(StudioProjectTemplateProvider.STUDIO_NATURE)), List.of()));
    }
}
