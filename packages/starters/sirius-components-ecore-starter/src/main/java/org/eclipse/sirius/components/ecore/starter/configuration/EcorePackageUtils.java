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

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.util.EcoreSwitch;

/**
 * Specific {@link EcoreSwitch} returning the whether an {@link EObject} is editable.
 *
 * @author adieumegard
 */
public class EcorePackageUtils {

    private static String featurePrefix = "feature:";

    public static String getEPackageDataTypeQualifiedName() {
        return getQualifiedName(EcorePackage.Literals.EPACKAGE);
    }

    public static String getEClassDataTypeQualifiedName() {
        return getQualifiedName(EcorePackage.Literals.ECLASS);
    }

    public static String getEPackageContainmentFeatureName(boolean featurePrefixed) {
        String result = "";
        if (featurePrefixed) {
            result = EcorePackageUtils.featurePrefix;
        }
        return result + EcorePackage.Literals.EPACKAGE__ESUBPACKAGES.getName();
    }

    public static String getEClassContainmentFeatureName(boolean featurePrefixed) {
        String result = "";
        if (featurePrefixed) {
            result = EcorePackageUtils.featurePrefix;
        }
        return result + EcorePackage.Literals.EPACKAGE__ECLASSIFIERS.getName();
    }

    private static String getQualifiedName(EClass clazz) {
        return EcorePackage.eNAME + "::" + clazz.getName();
    }

}
