/*******************************************************************************
 * Copyright (c) 2024 Obeo.
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
package org.eclipse.sirius.components.ecore.starter.configuration;

import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreSwitch;

/**
 * Specific {@link EcoreSwitch} returning whether an {@link EObject} is editable.
 *
 * @author adieumegard
 */
public class EcoreEditableSwitch extends EcoreSwitch<Boolean> {

    @Override
    public Boolean caseENamedElement(ENamedElement object) {
        return true;
    }

    @Override
    public Boolean defaultCase(EObject object) {
        return false;
    }
}
