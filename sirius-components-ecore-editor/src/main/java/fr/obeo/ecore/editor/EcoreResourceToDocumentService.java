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

import java.util.Optional;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.web.application.editingcontext.services.DocumentData;
import org.eclipse.sirius.web.application.editingcontext.services.ResourceToDocumentService;
import org.eclipse.sirius.web.application.editingcontext.services.api.IResourceToDocumentService;
import org.springframework.context.annotation.Primary;

/** Stores Ecore documents with the extension used by EMF when opening downloaded models. */
@Primary
public class EcoreResourceToDocumentService implements IResourceToDocumentService {
    private final ResourceToDocumentService delegate;

    public EcoreResourceToDocumentService(ResourceToDocumentService delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<DocumentData> toDocument(Resource resource, boolean applyMigrationParticipants) {
        ensureEcoreExtension(resource);
        return this.delegate.toDocument(resource, applyMigrationParticipants);
    }

    static void ensureEcoreExtension(Resource resource) {
        if (resource.getContents().stream().anyMatch(EPackage.class::isInstance)) {
            resource.eAdapters().stream().filter(ResourceMetadataAdapter.class::isInstance)
                    .map(ResourceMetadataAdapter.class::cast).filter(metadata -> !metadata.isReadOnly())
                    .filter(metadata -> !metadata.getName().endsWith(".ecore"))
                    .forEach(metadata -> metadata.setName(metadata.getName() + ".ecore"));
        }
    }
}
