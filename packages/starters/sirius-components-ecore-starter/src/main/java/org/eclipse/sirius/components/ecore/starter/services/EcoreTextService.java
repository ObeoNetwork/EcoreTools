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
package org.eclipse.sirius.components.ecore.starter.services;

import org.eclipse.emf.ecore.EAttribute;

/**
 * Utility class for Text computation (Labels, Tooltips).
 *
 * @author adieumegard
 */
public class EcoreTextService {


    public static String getEAttributeLabel(EAttribute eAttribute) {
        String result = eAttribute.getName();
        result += ": ";
        result += eAttribute.getEAttributeType().getName();
        return result;
    }

}
