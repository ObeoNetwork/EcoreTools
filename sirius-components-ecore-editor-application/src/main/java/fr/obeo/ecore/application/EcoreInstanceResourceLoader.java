/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package fr.obeo.ecore.application;

import java.util.List;
import java.util.Optional;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.sirius.components.emf.migration.api.IMigrationParticipant;
import org.eclipse.sirius.web.application.editingcontext.services.ResourceLoader;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import fr.obeo.ecore.editor.EcoreResourceLoader;

/** Also supplies Ecore schemas to temporary resource sets used by native Studio. */
@Service
@Primary
public class EcoreInstanceResourceLoader extends EcoreResourceLoader {
    private final EcoreInstanceSupport instanceSupport;

    public EcoreInstanceResourceLoader(ResourceLoader defaultResourceLoader, List<IMigrationParticipant> migrationParticipants,
            EcoreInstanceSupport instanceSupport) {
        super(defaultResourceLoader, migrationParticipants);
        this.instanceSupport = instanceSupport;
    }

    @Override
    public Optional<Resource> toResource(ResourceSet resourceSet, String id, String name, String content,
            boolean applyMigrations, boolean readOnly) {
        // Temporary Studio resource sets have no owning project to favor when namespaces conflict.
        this.instanceSupport.initializePackages(resourceSet, null);
        return super.toResource(resourceSet, id, name, content, applyMigrations, readOnly);
    }
}
