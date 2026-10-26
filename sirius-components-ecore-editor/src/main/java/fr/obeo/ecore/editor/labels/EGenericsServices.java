/*******************************************************************************
 * Copyright (c) 2014 Obeo
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package fr.obeo.ecore.editor.labels;

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

/** Resolves classifier/type-parameter targets and creates their generic type bindings. */
public class EGenericsServices {

	public static String getETypeLabel(ETypedElement typedElement) {
		String typeName = null;
		if (typedElement.getEType() != null && typedElement.getEType().getName() != null) {
			typeName = typedElement.getEType().getName();
		}
		if (typedElement.getEGenericType() != null
				&& typedElement.getEGenericType().getETypeParameter() != null) {
			typeName = typedElement.getEGenericType().getETypeParameter().getName();
		}
		return typeName;
	}

	public static EObject getETypeOrParameter(ETypedElement typedElement) {
		if (typedElement.getEGenericType() != null
				&& typedElement.getEGenericType().getETypeParameter() != null) {
			return typedElement.getEGenericType().getETypeParameter();
		}
		return typedElement.getEType();
	}

	/** Returns the last exact name match among the containing class's own parameters, or null. */
	public static Object findGenericType(ENamedElement namedElement, String typePart) {
		EClass containingClass = null;
		if (namedElement instanceof EStructuralFeature feature) {
			containingClass = feature.getEContainingClass();
		} else if (namedElement instanceof EOperation operation) {
			containingClass = operation.getEContainingClass();
		} else if (namedElement instanceof EParameter parameter) {
			containingClass = parameter.getEOperation()
					.getEContainingClass();
		}
		ETypeParameter matchingParameter = null;
		if (containingClass != null && typePart != null) {
			for (ETypeParameter typeParam : containingClass.getETypeParameters()) {
				if (typePart.equals(typeParam.getName())) {
					matchingParameter = typeParam;
				}
			}
		}
		return matchingParameter;
	}

	/** Replaces the binding with a fresh generic type and unbound arguments; unsupported values leave it unchanged. */
	public static void setETypeWithGenerics(ETypedElement typedElement, Object resolvedType) {
		EGenericType genericType;
		if (resolvedType instanceof EClassifier classifier) {
			genericType = EcoreFactory.eINSTANCE.createEGenericType();
			genericType.setEClassifier(classifier);
			for (int i = 0, size = classifier.getETypeParameters().size(); i < size; ++i) {
				genericType.getETypeArguments().add(
						EcoreFactory.eINSTANCE.createEGenericType());
			}
			typedElement.setEGenericType(genericType);
		} else if (resolvedType instanceof ETypeParameter typeParameter) {
			genericType = EcoreFactory.eINSTANCE.createEGenericType();
			genericType.setETypeParameter(typeParameter);
			typedElement.setEGenericType(genericType);
		}
	}
}
