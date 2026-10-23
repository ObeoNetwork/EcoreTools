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

import java.util.List;

import org.eclipse.sirius.components.view.View;
import org.eclipse.sirius.components.view.emf.IJavaServiceProvider;
import org.springframework.context.annotation.Configuration;

/** Exposes the Ecore semantic operations to the view's AQL expressions. */
@Configuration
public class EcoreJavaServiceProvider implements IJavaServiceProvider {
    @Override
    public List<Class<?>> getServiceClasses(View view) {
        return view.getDescriptions().stream().anyMatch(description -> EcoreViews.DIAGRAM_NAME.equals(description.getName()))
                ? List.of(EcoreServices.class) : List.of();
    }
}
