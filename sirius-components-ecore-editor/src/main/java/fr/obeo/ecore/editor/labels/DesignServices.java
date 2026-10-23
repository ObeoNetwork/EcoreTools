/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package fr.obeo.ecore.editor.labels;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.util.EcoreUtil;

/** Type lookup from the editing context, followed by the built-in Ecore types. */
public class DesignServices {
    public EClassifier findTypeByName(EObject context, String name) {
        if (name == null) return null;
        List<EObject> roots = new ArrayList<>();
        if (context.eResource() != null && context.eResource().getResourceSet() != null) {
            context.eResource().getResourceSet().getResources().forEach(r -> roots.addAll(r.getContents()));
        } else {
            roots.add(EcoreUtil.getRootContainer(context));
        }
        roots.add(EcorePackage.eINSTANCE);
        for (EObject root : roots) {
            if (root instanceof EClassifier type && matches(type, name)) return type;
            var contents = root.eAllContents();
            while (contents.hasNext()) {
                if (contents.next() instanceof EClassifier type && matches(type, name)) return type;
            }
        }
        return null;
    }

    private boolean matches(EClassifier type, String name) {
        return type.getName() != null && type.getName().trim().equalsIgnoreCase(name.trim());
    }
}
