/*******************************************************************************
 * Copyright (c) 2013 THALES GLOBAL SERVICES and Others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.components.ecore.starter.services.ecoretools;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;

/**
 * Services on EAttributes usable from a VSM.
 *
 * Content adapted from org.eclipse.emf.ecoretools.design.service.EAttributeServices
 *
 * @author adieumegard - Minor adaptations
 */
public class EAttributeServices {
    /**
     *
     */
    private static final String SINGLE_SPACE = " ";

    private static final String DERIVED_ATTRIBUTE_PREFIX = "/";

    private static final String TYPE_SEPARATOR = ":";

    private static final String DEFAULT_VALUE_SEPARATOR = "=";

    /**
     * Renders the specified attribute into a string to display to the end-user.
     *
     * @param attr
     *            the attribute to render.
     * @return a string representation of the attribute suitable to display to
     *         the end-user.
     */
    public String render(EAttribute attr) {
        StringBuilder sb = new StringBuilder();
        this.renderName(attr, sb);
        this.renderType(attr, sb);
        this.renderDefaultValue(attr, sb);
        return sb.toString();
    }

    /**
     * Update the specified attribute according the the edit string entered by
     * the user.
     *
     * @param editString
     *            the edit string entered by the user.
     */
    public EStructuralFeature performEdit(EStructuralFeature attr, String editString) {
        if ("0".equals(editString.trim())) {
            attr.setLowerBound(0);
        } else if ("1".equals(editString.trim())) {
            attr.setLowerBound(1);
        } else if ("11".equals(editString.trim())) {
            attr.setLowerBound(1);
            attr.setUpperBound(1);
        } else if (EReferenceServices.CARDINALITY_UNBOUNDED.equals(editString.trim())) {
            attr.setUpperBound(-1);
        } else if (EReferenceServices.CARDINALITY_UNBOUNDED_ALTERNATIVE.equals(editString.trim())) {
            attr.setUpperBound(-1);
        } else {
            int typeStart = editString.indexOf(TYPE_SEPARATOR);
            boolean setType = typeStart != -1;

            int defaultStart = editString.indexOf(DEFAULT_VALUE_SEPARATOR);
            boolean setDefault = defaultStart != -1;

            int nameEnd = editString.length();
            if (setType) {
                nameEnd = Math.min(nameEnd, typeStart);
            }
            if (setDefault) {
                nameEnd = Math.min(nameEnd, defaultStart);
            }
            String namePart = editString.substring(0, nameEnd).trim();
            boolean setName = namePart.length() > 0;

            String typePart = null;
            if (setType) {
                int typeEnd = editString.length();
                if (setDefault) {
                    typeEnd = defaultStart;
                }
                typePart = editString.substring(typeStart + TYPE_SEPARATOR.length(), typeEnd).trim();
            }

            String defaultPart = null;
            if (setDefault) {
                defaultPart = editString.substring(defaultStart + DEFAULT_VALUE_SEPARATOR.length()).trim();
            }

            if (setName) {
                if (namePart.startsWith(DERIVED_ATTRIBUTE_PREFIX)) {
                    attr.setDerived(true);
                    attr.setTransient(true);
                    attr.setVolatile(true);
                    attr.setChangeable(false);
                    namePart = namePart.substring(DERIVED_ATTRIBUTE_PREFIX.length());
                } else {
                    attr.setDerived(false);
                }
                attr.setName(namePart);
            }
            if (setType) {
                Object value = EGenericsServices.findGenericType(attr, typePart);
                if (value == null) {
                    value = new DesignServices().findTypeByName(attr, typePart);
                }
                EGenericsServices.setETypeWithGenerics(attr, value);
            }
            if (setDefault && defaultPart != null && defaultPart.length() > 0) {
                attr.setDefaultValueLiteral(defaultPart);
            }
        }
        return attr;
    }

    private void renderName(EAttribute attr, StringBuilder sb) {
        if (attr.getName() != null) {
            if (attr.isDerived()) {
                sb.append(DERIVED_ATTRIBUTE_PREFIX);
            }
            sb.append(attr.getName());
        }
    }

    private void renderType(EAttribute attr, StringBuilder sb) {
        String typeName = EGenericsServices.getETypeLabel(attr);
        if (typeName != null) {
            if (attr.getName() != null) {
                sb.append(SINGLE_SPACE);
            }
            sb.append(TYPE_SEPARATOR).append(SINGLE_SPACE).append(typeName);
        }
    }

    private void renderDefaultValue(EAttribute attr, StringBuilder sb) {
        if (attr.getDefaultValueLiteral() != null) {
            if (attr.getDefaultValueLiteral().length() > 0) {
                sb.append(SINGLE_SPACE).append(DEFAULT_VALUE_SEPARATOR).append(SINGLE_SPACE).append(attr.getDefaultValueLiteral());
            }
        } else if (attr.getDefaultValue() != null) {
            EDataType dataType = attr.getEAttributeType();
            if (dataType instanceof EEnum) {
                sb.append(SINGLE_SPACE).append(DEFAULT_VALUE_SEPARATOR).append(SINGLE_SPACE).append(attr.getDefaultValue());
            } else {
                EFactory factory =  EcoreFactory.eINSTANCE;
                if (dataType.getEPackage() != null) {
                    factory = dataType.getEPackage().getEFactoryInstance();
                }
                String serializable = factory.convertToString(dataType, attr.getDefaultValue());
                if (!"0".equals(serializable)) {
                    // Ignore this default value and consider it as blank
                    // default
                    // value. This is the result of the '\u0000' (default value
                    // for
                    // EChar data type) to string.
                    sb.append(SINGLE_SPACE).append(DEFAULT_VALUE_SEPARATOR).append(SINGLE_SPACE);
                    sb.append(serializable);
                }
            }
        }
    }
}
