/*******************************************************************************
 * Copyright (c) 2014 - 2024 Obeo
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.components.ecore.starter.services.ecoretools;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EGenericType;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.ETypeParameter;
import org.eclipse.emf.ecore.ETypedElement;
import org.eclipse.emf.ecore.EcoreFactory;

/**
 * Content adapted from org.eclipse.emf.ecoretools.design.service.EGenericServices.
 *
 * @author adieumegard - Minor adaptations
 */
public class EGenericsServices {

    public static String getETypeLabel(ETypedElement attr) {
        String typeName = null;
        if (attr.getEType() != null && attr.getEType().getName() != null) {
            typeName = attr.getEType().getName();
        }
        if (attr.getEGenericType() != null && attr.getEGenericType().getETypeParameter() != null) {
            typeName = attr.getEGenericType().getETypeParameter().getName();
        }
        return typeName;
    }

    public static EObject getETypeOrParameter(ETypedElement attr) {
        EObject result = null;
        if (attr.getEGenericType() != null && attr.getEGenericType().getETypeParameter() != null) {
            result = attr.getEGenericType().getETypeParameter();
        }
        if (attr.getEType() != null) {
            result = attr.getEType();
        }
        return result;
    }

    public static Object findGenericType(ENamedElement attr, String typePart) {
        EClass parentClass = null;
        if (attr instanceof EStructuralFeature) {
            parentClass = ((EStructuralFeature) attr).getEContainingClass();
        } else if (attr instanceof EOperation) {
            parentClass = ((EOperation) attr).getEContainingClass();
        } else if (attr instanceof EParameter) {
            parentClass = ((EParameter) attr).getEOperation().getEContainingClass();
        }
        Object value = null;
        if (parentClass != null && typePart != null) {
            for (ETypeParameter typeParam : parentClass.getETypeParameters()) {
                if (typePart.equals(typeParam.getName())) {
                    value = typeParam;
                }
            }
        }
        return value;
    }

    public static void setETypeWithGenerics(ETypedElement typed, Object value) {
        EGenericType eGenericType = null;
        if (value instanceof EClassifier) {
            EClassifier eClassifier = (EClassifier) value;
            eGenericType = EcoreFactory.eINSTANCE.createEGenericType();
            eGenericType.setEClassifier(eClassifier);
            for (int i = 0, size = eClassifier.getETypeParameters().size(); i < size; ++i) {
                eGenericType.getETypeArguments().add(EcoreFactory.eINSTANCE.createEGenericType());
            }
            typed.setEGenericType(eGenericType);
        } else if (value instanceof ETypeParameter) {
            eGenericType = EcoreFactory.eINSTANCE.createEGenericType();
            eGenericType.setETypeParameter((ETypeParameter) value);
            typed.setEGenericType(eGenericType);
        }
    }
}
