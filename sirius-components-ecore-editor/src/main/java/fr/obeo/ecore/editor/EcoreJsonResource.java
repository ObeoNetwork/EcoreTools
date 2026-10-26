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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.sirius.components.emf.services.EObjectIDManager;
import org.eclipse.sirius.emfjson.resource.JsonResource;
import org.eclipse.sirius.emfjson.resource.JsonResourceImpl;

/** Preserves IDs of the compact Ecore objects which EMF JSON serializes without IDs. */
public final class EcoreJsonResource extends JsonResourceImpl {
    public EcoreJsonResource(URI uri) {
        super(uri, Map.of(JsonResource.OPTION_ID_MANAGER, new EObjectIDManager(),
                JsonResource.OPTION_DISPLAY_DYNAMIC_INSTANCES, true));
        this.setIntrinsicIDToEObjectMap(new HashMap<>());
    }

    @Override
    protected void doSave(OutputStream output, Map<?, ?> options) throws IOException {
        super.doSave(output, this.withIdPreservingHandler(options));
    }

    @Override
    protected void doLoad(InputStream input, Map<?, ?> options) throws IOException {
        super.doLoad(input, this.withIdPreservingHandler(options));
    }

    private Map<Object, Object> withIdPreservingHandler(Map<?, ?> options) {
        var resourceOptions = new HashMap<Object, Object>();
        if (options != null) {
            resourceOptions.putAll(options);
        }
        var delegate = (JsonResource.IEObjectHandler) resourceOptions.get(JsonResource.OPTION_EOBJECT_HANDLER);
        resourceOptions.put(JsonResource.OPTION_EOBJECT_HANDLER, new IdPreservingEObjectHandler(delegate));
        return resourceOptions;
    }

    /** Copies IDs to JSON on save and back to objects on load, including compact containment encodings. */
    private void synchronizeIdsRecursively(EObject object, JsonObject json, boolean saving) {
        if (saving) {
            json.addProperty("id", this.getURIFragment(object));
        } else if (json.has("id")) {
            this.setID(object, json.get("id").getAsString());
        }
        JsonObject featureValues = json.has("data") ? json.getAsJsonObject("data") : json;
        for (var containment : object.eClass().getEAllContainments()) {
            var serializedChildren = featureValues.get(containment.getName());
            if (serializedChildren == null || serializedChildren.isJsonNull()) {
                continue;
            }
            if (containment.isMany()) {
                var children = (List<?>) object.eGet(containment);
                var childElements = serializedChildren.getAsJsonArray();
                // EMF JSON preserves containment-list order, including objects omitted by its ID handler.
                for (int index = 0; index < children.size(); index++) {
                    this.synchronizeIdsRecursively((EObject) children.get(index), childElements.get(index).getAsJsonObject(), saving);
                }
            } else if (object.eGet(containment) instanceof EObject child) {
                this.synchronizeIdsRecursively(child, serializedChildren.getAsJsonObject(), saving);
            }
        }
    }

    private final class IdPreservingEObjectHandler implements JsonResource.IEObjectHandler {
        private final JsonResource.IEObjectHandler delegate;

        private IdPreservingEObjectHandler(JsonResource.IEObjectHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public JsonElement processSerializedContent(JsonElement json, EObject object) {
            EcoreJsonResource.this.synchronizeIdsRecursively(object, json.getAsJsonObject(), true);
            return this.delegate == null ? json : this.delegate.processSerializedContent(json, object);
        }

        @Override
        public EObject processDeserializedContent(EObject object, JsonElement json) {
            EcoreJsonResource.this.synchronizeIdsRecursively(object, json.getAsJsonObject(), false);
            return this.delegate == null ? object : this.delegate.processDeserializedContent(object, json);
        }
    }
}
