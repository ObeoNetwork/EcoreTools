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
public class EcorePackageQueryUtils {

    private static String featurePrefix = "feature:";

    public static String getEPackageDataTypeQualifiedName() {
        return getQualifiedName(EcorePackage.Literals.EPACKAGE);
    }

    public static String getEClassDataTypeQualifiedName() {
        return getQualifiedName(EcorePackage.Literals.ECLASS);
    }

    public static String getEAttributeDataTypeQualifiedName() {
        return getQualifiedName(EcorePackage.Literals.EATTRIBUTE);
    }

    public static String getEOperationDataTypeQualifiedName() {
        return getQualifiedName(EcorePackage.Literals.EOPERATION);
    }

    public static String getEReferenceDataTypeQualifiedName() {
        return getQualifiedName(EcorePackage.Literals.EREFERENCE);
    }

    public static String getEEnumDataTypeQualifiedName() {
        return getQualifiedName(EcorePackage.Literals.EENUM);
    }

    public static String getEEnumLiteralDataTypeQualifiedName() {
        return getQualifiedName(EcorePackage.Literals.EENUM_LITERAL);
    }

    public static String getEPackageContainmentFeatureName(boolean featurePrefixed) {
        String result = "";
        if (featurePrefixed) {
            result = EcorePackageQueryUtils.featurePrefix;
        }
        return result + EcorePackage.Literals.EPACKAGE__ESUBPACKAGES.getName();
    }

    public static String getEClassContainmentFeatureName(boolean featurePrefixed) {
        String result = "";
        if (featurePrefixed) {
            result = EcorePackageQueryUtils.featurePrefix;
        }
        return result + EcorePackage.Literals.EPACKAGE__ECLASSIFIERS.getName();
    }

    public static String getEAttributeContainmentFeatureName(boolean featurePrefixed) {
        String result = "";
        if (featurePrefixed) {
            result = EcorePackageQueryUtils.featurePrefix;
        }
        return result + EcorePackage.Literals.ECLASS__EATTRIBUTES.getName();
    }

    public static String getEOperationContainmentFeatureName(boolean featurePrefixed) {
        String result = "";
        if (featurePrefixed) {
            result = EcorePackageQueryUtils.featurePrefix;
        }
        return result + EcorePackage.Literals.ECLASS__EOPERATIONS.getName();
    }

    public static String getEReferenceContainmentFeatureName(boolean featurePrefixed) {
        String result = "";
        if (featurePrefixed) {
            result = EcorePackageQueryUtils.featurePrefix;
        }
        return result + EcorePackage.Literals.ECLASS__EREFERENCES.getName();
    }

    public static String getEEnumContainmentFeatureName(boolean featurePrefixed) {
        String result = "";
        if (featurePrefixed) {
            result = EcorePackageQueryUtils.featurePrefix;
        }
        return result + EcorePackage.Literals.EPACKAGE__ECLASSIFIERS.getName();
    }

    public static String getEEnumLiteralContainmentFeatureName(boolean featurePrefixed) {
        String result = "";
        if (featurePrefixed) {
            result = EcorePackageQueryUtils.featurePrefix;
        }
        return result + EcorePackage.Literals.EENUM__ELITERALS.getName();
    }

    public static String getEClassInterfaceFeatureName(boolean featurePrefixed) {
        String result = "";
        if (featurePrefixed) {
            result = EcorePackageQueryUtils.featurePrefix;
        }
        return result + EcorePackage.Literals.ECLASS__INTERFACE.getName();
    }

    public static String getEClassAbstractFeatureName(boolean featurePrefixed) {
        String result = "";
        if (featurePrefixed) {
            result = EcorePackageQueryUtils.featurePrefix;
        }
        return result + EcorePackage.Literals.ECLASS__ABSTRACT.getName();
    }

    public static String getEReferenceETypeFeatureName(boolean featurePrefixed) {
        String result = "";
        if (featurePrefixed) {
            result = EcorePackageQueryUtils.featurePrefix;
        }
        return result + EcorePackage.Literals.EREFERENCE__EREFERENCE_TYPE.getName();
    }

    private static String getQualifiedName(EClass clazz) {
        return EcorePackage.eNAME + "::" + clazz.getName();
    }

}
