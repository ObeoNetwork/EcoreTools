/*******************************************************************************
 * Copyright (c) 2023, 2024 Obeo.
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
package org.eclipse.sirius.components.ecore.starter.services;

import static org.eclipse.sirius.components.ecore.starter.configuration.EcoreStereotypeDescriptionRegistryConfigurer.ECORE_M2_LABEL;
import static org.eclipse.sirius.components.ecore.starter.configuration.EcoreStereotypeDescriptionRegistryConfigurer.EMPTY_ECORE_LABEL;
import static org.eclipse.sirius.components.ecore.starter.configuration.EcoreStereotypeDescriptionRegistryConfigurer.VEHICLE_LABEL;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.sirius.components.collaborative.api.IEditingContextActionProvider;
import org.eclipse.sirius.components.collaborative.dto.EditingContextAction;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.web.services.editingcontext.EditingContext;
import org.springframework.stereotype.Service;

/**
 * Provides the list of possible actions on the editingContext.
 *
 * @author adieumegard
 */
@Service
public class EcoreEditingContextActionProvider implements IEditingContextActionProvider {


    public static final String EMPTY_ECORE_ID = EMPTY_ECORE_LABEL;

    public static final String VEHICLE_ID = VEHICLE_LABEL;

    public static final String ECORE_M2_ID = ECORE_M2_LABEL;


    private static final EditingContextAction EMPTY_ECORE_EDITING_CONTEXT_ACTION = new EditingContextAction(EMPTY_ECORE_ID, EMPTY_ECORE_LABEL);

    private static final EditingContextAction VEHICLE_EDITING_CONTEXT_ACTION = new EditingContextAction(VEHICLE_ID, VEHICLE_LABEL);

    private static final EditingContextAction ECORE_M2_EDITING_CONTEXT_ACTION = new EditingContextAction(ECORE_M2_ID, ECORE_M2_LABEL);


    @Override
    public List<EditingContextAction> getEditingContextAction(IEditingContext editingContext) {
        var actions = new ArrayList<EditingContextAction>();
        if (editingContext instanceof EditingContext) {
            actions.add(EMPTY_ECORE_EDITING_CONTEXT_ACTION);
            actions.add(VEHICLE_EDITING_CONTEXT_ACTION);
            actions.add(ECORE_M2_EDITING_CONTEXT_ACTION);
        }
        return actions;
    }
}
