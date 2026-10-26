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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.EReference;


/**
 * Services dealing with EReferences usable from a VSM.
 */
public class EReferenceServices {
    private static final String CARDINALITY_SEPARATOR = "..";

    public static final String DERIVED_PREFIX = "/";

    public static final String CARDINALITY_UNBOUNDED = "*";

    public static final String CARDINALITY_UNBOUNDED_ALTERNATIVE = "-1";

    private static final String TYPE_SEPARATOR = ":";

    public String render(EReference reference) {
        StringBuilder label = new StringBuilder();
        renderCardinality(reference, label);
        renderName(reference, label);
        return label.toString();
    }

    public String renderAsNode(EReference reference) {
        StringBuilder label = new StringBuilder();
        renderName(reference, label);
        renderType(reference, label);
        return label.toString();
    }

    private void renderType(EReference reference, StringBuilder label) {
        String typeName = EGenericsServices.getETypeLabel(reference);
        if (typeName != null) {
            if (reference.getName() != null) {
                label.append(" ");
            }
            label.append(TYPE_SEPARATOR).append(" ").append(typeName);
        }
    }

    private void renderCardinality(EReference reference, StringBuilder label) {
        label.append("[");
        label.append(renderBound(reference.getLowerBound()));
        label.append(CARDINALITY_SEPARATOR);
        label.append(renderBound(reference.getUpperBound()));
        label.append("]");
    }

    private String renderBound(int bound) {
        if (bound == -1) {
            return CARDINALITY_UNBOUNDED;
        } else {
            return String.valueOf(bound);
        }
    }

    private void renderName(EReference reference, StringBuilder label) {
        if (reference.getName() != null) {
            label.append(" ");
            if (reference.isDerived()) {
                label.append(DERIVED_PREFIX);
            }
            label.append(reference.getName());
        }
    }

    public EReference performEdit(EReference reference, String editString) {
        String trimmedEditString = editString.trim();
        if ("0".equals(trimmedEditString)) {
            reference.setLowerBound(0);
        } else if ("1".equals(trimmedEditString)) {
            reference.setLowerBound(1);
        } else if ("11".equals(trimmedEditString)) {
            reference.setLowerBound(1);
            reference.setUpperBound(1);
        } else if (CARDINALITY_UNBOUNDED.equals(trimmedEditString)
                || CARDINALITY_UNBOUNDED_ALTERNATIVE.equals(trimmedEditString)) {
            reference.setUpperBound(-1);
        } else {
            editName(reference, editString);
            editCardinality(reference, editString);
        }
        return reference;
    }

    private void editName(EReference reference, String editString) {
        String namePart = extractNamePart(editString);
        if (namePart != null && namePart.trim().length() > 0) {
            boolean derived = namePart.startsWith(DERIVED_PREFIX);
            reference.setDerived(derived);
            reference.setName(namePart.substring(derived ? DERIVED_PREFIX.length() : 0).trim());
        }
    }

    private String extractNamePart(String editString) {
        int end = editString.indexOf("]");
        if (end != -1 && end < editString.length()) {
            return editString.substring(end + 1).trim();
        } else {
            return editString.trim();
        }
    }

    private void editCardinality(EReference reference, String editString) {
        List<Integer> bounds = parseCardinality(editString);
        if (bounds.get(0) != null) {
            reference.setLowerBound(bounds.get(0).intValue());
        }
        if (bounds.get(1) != null) {
            reference.setUpperBound(bounds.get(1).intValue());
        }
    }

    /**
     * Returns a mutable [lower, upper] list; null entries mean absent or invalid bounds.
     * Only bracketed ranges with ".." are parsed, and extra range parts are ignored.
     */
    public List<Integer> parseCardinality(String editString) {
        List<Integer> bounds = new ArrayList<>(java.util.Arrays.asList(null, null));
        String cardinalityText = extractCardinalityPart(editString);
        if (cardinalityText == null || !cardinalityText.contains(CARDINALITY_SEPARATOR)) {
            return bounds;
        }
        String[] boundParts = cardinalityText.split(Pattern.quote(CARDINALITY_SEPARATOR));
        switch (boundParts.length) {
        case 0:
            break;
        case 1:
            if (cardinalityText.startsWith(CARDINALITY_SEPARATOR)) {
                bounds.set(1, parseBound(boundParts[0]));
            } else if (cardinalityText.endsWith(CARDINALITY_SEPARATOR)) {
                bounds.set(0, parseBound(boundParts[0]));
            }
            break;
        default:
            bounds.set(0, parseBound(boundParts[0]));
            bounds.set(1, parseBound(boundParts[1]));
        }
        return bounds;
    }

    private Integer parseBound(String bound) {
        if (CARDINALITY_UNBOUNDED.equals(bound.trim())) {
            return Integer.valueOf(-1);
        } else {
            try {
                return Integer.parseInt(bound.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /** Returns the trimmed text between the first bracket pair, or null if that pair is absent or reversed. */
    public String extractCardinalityPart(String editString) {
        int start = editString.indexOf("[");
        int end = editString.indexOf("]");
        if (start != -1 && end != -1 && start < end && end < editString.length()) {
            return editString.substring(start + 1, end).trim();
        } else {
            return null;
        }
    }
}
