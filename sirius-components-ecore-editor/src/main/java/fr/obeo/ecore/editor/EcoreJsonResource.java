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
        super.doSave(output, this.withIds(options));
    }

    @Override
    protected void doLoad(InputStream input, Map<?, ?> options) throws IOException {
        super.doLoad(input, this.withIds(options));
    }

    private Map<Object, Object> withIds(Map<?, ?> options) {
        var result = new HashMap<Object, Object>();
        if (options != null) result.putAll(options);
        var delegate = (JsonResource.IEObjectHandler) result.get(JsonResource.OPTION_EOBJECT_HANDLER);
        result.put(JsonResource.OPTION_EOBJECT_HANDLER, new JsonResource.IEObjectHandler() {
            @Override
            public JsonElement processSerializedContent(JsonElement json, EObject object) {
                EcoreJsonResource.this.ids(object, json.getAsJsonObject(), true);
                return delegate == null ? json : delegate.processSerializedContent(json, object);
            }

            @Override
            public EObject processDeserializedContent(EObject object, JsonElement json) {
                EcoreJsonResource.this.ids(object, json.getAsJsonObject(), false);
                return delegate == null ? object : delegate.processDeserializedContent(object, json);
            }
        });
        return result;
    }

    private void ids(EObject object, JsonObject json, boolean saving) {
        if (saving) json.addProperty("id", this.getURIFragment(object));
        else if (json.has("id")) this.setID(object, json.get("id").getAsString());
        JsonObject data = json.has("data") ? json.getAsJsonObject("data") : json;
        for (var feature : object.eClass().getEAllContainments()) {
            var content = data.get(feature.getName());
            if (content == null || content.isJsonNull()) continue;
            if (feature.isMany()) {
                var children = (List<?>) object.eGet(feature);
                var elements = content.getAsJsonArray();
                for (int index = 0; index < children.size(); index++) {
                    this.ids((EObject) children.get(index), elements.get(index).getAsJsonObject(), saving);
                }
            } else if (object.eGet(feature) instanceof EObject child) {
                this.ids(child, content.getAsJsonObject(), saving);
            }
        }
    }
}
