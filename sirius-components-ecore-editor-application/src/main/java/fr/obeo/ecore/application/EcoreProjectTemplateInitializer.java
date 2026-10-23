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

import java.util.Objects;
import java.util.UUID;
import fr.obeo.ecore.editor.EcoreJsonResource;

import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextPersistenceService;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.components.events.ICause;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.sirius.web.application.project.services.api.ISemanticDataInitializer;
import org.springframework.stereotype.Service;

/**
 * Initializes the semantic contents of the Example template.
 */
@Service
public class EcoreProjectTemplateInitializer implements ISemanticDataInitializer {

    private final IEditingContextPersistenceService editingContextPersistenceService;

    public EcoreProjectTemplateInitializer(IEditingContextPersistenceService editingContextPersistenceService) {
        this.editingContextPersistenceService = Objects.requireNonNull(editingContextPersistenceService);
    }

    @Override
    public boolean canHandle(String projectTemplateId) {
        return EcoreProjectTemplateProvider.TEMPLATE_ID.equals(projectTemplateId);
    }

    @Override
    public void handle(ICause cause, IEditingContext editingContext, String projectTemplateId) {
        if (this.canHandle(projectTemplateId) && editingContext instanceof IEMFEditingContext emfEditingContext) {
            var resource = new EcoreJsonResource(new JSONResourceFactory().createResourceURI(UUID.randomUUID().toString()));
            resource.eAdapters().add(new ResourceMetadataAdapter("Ecore example.ecore"));
            var model = EcoreExample.load();
            resource.getContents().add(model);
            emfEditingContext.getDomain().getResourceSet().getResources().add(resource);
            this.editingContextPersistenceService.persist(
                    new ExampleInitialization(UUID.randomUUID(), cause, emfEditingContext, model), editingContext);
        }
    }

    record ExampleInitialization(UUID id, ICause causedBy, IEMFEditingContext editingContext, EPackage model) implements ICause { }
}
