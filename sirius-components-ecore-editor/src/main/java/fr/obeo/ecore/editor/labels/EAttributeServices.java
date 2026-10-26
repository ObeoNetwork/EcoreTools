/*******************************************************************************
 * Copyright (c) 2013 THALES GLOBAL SERVICES and Others
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

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;

/**
 * Services on EAttributes usable from a VSM.
 */
public class EAttributeServices {
    private static final String DERIVED_ATTRIBUTE_PREFIX = "/";

    private static final String TYPE_SEPARATOR = ":";

    private static final String DEFAULT_VALUE_SEPARATOR = "=";

    /**
     * Renders the specified attribute into a string to display to the end-user.
     * 
     * @param attribute
     *            the attribute to render.
     * @return a string representation of the attribute suitable to display to
     *         the end-user.
     */
    public String render(EAttribute attribute) {
        StringBuilder label = new StringBuilder();
        renderName(attribute, label);
        renderType(attribute, label);
        renderDefaultValue(attribute, label);
        return label.toString();
    }

    /**
     * Mutates and returns the feature. Multiplicity shortcuts change only their specified bounds;
     * full labels update the supplied name/type/default parts, leaving an empty default unchanged.
     * A derived-name prefix also sets transient, volatile, and non-changeable flags.
     */
    public EStructuralFeature performEdit(EStructuralFeature feature, String editString) {
        String trimmedEditString = editString.trim();
        if ("0".equals(trimmedEditString)) {
            feature.setLowerBound(0);
        } else if ("1".equals(trimmedEditString)) {
            feature.setLowerBound(1);
        } else if ("11".equals(trimmedEditString)) {
            feature.setLowerBound(1);
            feature.setUpperBound(1);
        } else if (EReferenceServices.CARDINALITY_UNBOUNDED.equals(trimmedEditString)
                || EReferenceServices.CARDINALITY_UNBOUNDED_ALTERNATIVE.equals(trimmedEditString)) {
            feature.setUpperBound(-1);
        } else {
            this.applyLabelParts(feature, editString);
        }
        return feature;
    }

    private void applyLabelParts(EStructuralFeature feature, String editString) {
        int typeStart = editString.indexOf(TYPE_SEPARATOR);
        boolean hasTypePart = typeStart != -1;

        int defaultStart = editString.indexOf(DEFAULT_VALUE_SEPARATOR);
        boolean hasDefaultPart = defaultStart != -1;

        int nameEnd = editString.length();
        if (hasTypePart) {
            nameEnd = Math.min(nameEnd, typeStart);
        }
        if (hasDefaultPart) {
            nameEnd = Math.min(nameEnd, defaultStart);
        }
        String namePart = editString.substring(0, nameEnd).trim();
        boolean hasNamePart = namePart.length() > 0;

        String typePart = null;
        if (hasTypePart) {
            int typeEnd = hasDefaultPart ? defaultStart : editString.length();
            typePart = editString.substring(typeStart + TYPE_SEPARATOR.length(), typeEnd).trim();
        }

        String defaultPart = null;
        if (hasDefaultPart) {
            defaultPart = editString.substring(defaultStart + DEFAULT_VALUE_SEPARATOR.length()).trim();
        }

        if (hasNamePart) {
            if (namePart.startsWith(DERIVED_ATTRIBUTE_PREFIX)) {
                feature.setDerived(true);
                feature.setTransient(true);
                feature.setVolatile(true);
                feature.setChangeable(false);
                namePart = namePart.substring(DERIVED_ATTRIBUTE_PREFIX.length());
            } else {
                feature.setDerived(false);
            }
            feature.setName(namePart);
        }
        if (hasTypePart) {
            Object resolvedType = EGenericsServices.findGenericType(feature, typePart);
            if (resolvedType == null) {
                resolvedType = new DesignServices().findTypeByName(feature, typePart);
            }
            EGenericsServices.setETypeWithGenerics(feature, resolvedType);
        }
        if (hasDefaultPart && defaultPart.length() > 0) {
            feature.setDefaultValueLiteral(defaultPart);
        }
    }

    private void renderName(EAttribute attribute, StringBuilder label) {
        if (attribute.getName() != null) {
            if (attribute.isDerived()) {
                label.append(DERIVED_ATTRIBUTE_PREFIX);
            }
            label.append(attribute.getName());
        }
    }

    private void renderType(EAttribute attribute, StringBuilder label) {
        String typeName = EGenericsServices.getETypeLabel(attribute);
        if (typeName != null) {
            if (attribute.getName() != null) {
                label.append(" ");
            }
            label.append(TYPE_SEPARATOR).append(" ").append(typeName);
        }
    }

    private void renderDefaultValue(EAttribute attribute, StringBuilder label) {
        if (attribute.getDefaultValueLiteral() != null) {
            if (attribute.getDefaultValueLiteral().length() > 0) {
                label.append(" ").append(DEFAULT_VALUE_SEPARATOR).append(" ").append(attribute.getDefaultValueLiteral());
            }
        } else if (attribute.getDefaultValue() != null) {
            EDataType dataType = attribute.getEAttributeType();
            if (dataType instanceof EEnum) {
                label.append(" ").append(DEFAULT_VALUE_SEPARATOR).append(" ").append(attribute.getDefaultValue());
            } else {
                EFactory factory = dataType.getEPackage() != null ? dataType.getEPackage().getEFactoryInstance() : EcoreFactory.eINSTANCE;  
                String serializable = factory.convertToString(dataType, attribute.getDefaultValue());
                if (!"0".equals(serializable)) {
                    label.append(" ").append(DEFAULT_VALUE_SEPARATOR).append(" ");
                    label.append(serializable);
                }
            }
        }
    }
}
