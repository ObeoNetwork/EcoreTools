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

import java.util.Objects;

import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextProcessor;
import org.eclipse.sirius.web.application.editingcontext.EditingContext;
import org.eclipse.sirius.components.view.View;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Makes the diagram available for Ecore models in every workbench. */
@Service
public class EcoreViewEditingContextInitializer implements IEditingContextProcessor {
    private final View view;

    public EcoreViewEditingContextInitializer(@Qualifier("ecoreView") View view) {
        this.view = Objects.requireNonNull(view);
    }

    @Override
    public void preProcess(IEditingContext editingContext) {
        if (editingContext instanceof EditingContext context && !context.getViews().contains(this.view)) {
            context.getViews().add(this.view);
        }
    }
}
