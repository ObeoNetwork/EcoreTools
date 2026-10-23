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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import com.google.gson.JsonParser;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.components.emf.migration.MigrationService;
import org.eclipse.sirius.components.emf.migration.api.IMigrationParticipant;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.emfjson.resource.JsonResource;
import org.eclipse.sirius.web.application.editingcontext.services.ResourceLoader;
import org.eclipse.sirius.web.application.editingcontext.services.api.IResourceLoader;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;

/** Uses the Ecore-aware JSON resource for EPackage documents and delegates other documents unchanged. */
@Primary
public class EcoreResourceLoader implements IResourceLoader {
    private final ResourceLoader delegate;
    private final List<IMigrationParticipant> migrations;

    public EcoreResourceLoader(ResourceLoader delegate, List<IMigrationParticipant> migrations) {
        this.delegate = delegate;
        this.migrations = migrations;
    }

    @Override
    public Optional<Resource> toResource(ResourceSet resources, String id, String name, String content,
            boolean applyMigrations, boolean readOnly) {
        if (!content.contains("EPackage") || JsonParser.parseString(content).getAsJsonObject().getAsJsonArray("content")
                .asList().stream().noneMatch(root -> root.getAsJsonObject().get("eClass").getAsString().equals("ecore:EPackage"))) {
            return this.delegate.toResource(resources, id, name, content, applyMigrations, readOnly);
        }
        var resource = new EcoreJsonResource(new JSONResourceFactory().createResourceURI(id));
        var options = new HashMap<Object, Object>();
        if (applyMigrations) {
            var migration = new MigrationService(this.migrations);
            options.put(JsonResource.OPTION_EXTENDED_META_DATA, migration);
            options.put(JsonResource.OPTION_JSON_RESSOURCE_PROCESSOR, migration);
        }
        try (var input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            resources.getResources().add(resource);
            resource.eAdapters().add(new ResourceMetadataAdapter(name, readOnly));
            resource.load(input, options);
            EcoreResourceToDocumentService.ensureEcoreExtension(resource);
            return Optional.of(resource);
        } catch (IOException | IllegalArgumentException exception) {
            LoggerFactory.getLogger(EcoreResourceLoader.class).warn("Cannot load Ecore document {}", id, exception);
            resources.getResources().remove(resource);
            return Optional.empty();
        }
    }
}
