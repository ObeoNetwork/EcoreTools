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

    public String render(EReference ref) {
        StringBuilder sb = new StringBuilder();
        renderCardinality(ref, sb);
        renderName(ref, sb);
        return sb.toString();
    }

    public String renderAsNode(EReference ref) {
        StringBuilder sb = new StringBuilder();
        renderName(ref, sb);
        renderType(ref, sb);
        return sb.toString();
    }

    private void renderType(EReference ref, StringBuilder sb) {
        String typeName = EGenericsServices.getETypeLabel(ref);
        if (typeName != null) {
            if (ref.getName() != null) {
                sb.append(" ");
            }
            sb.append(TYPE_SEPARATOR).append(" ").append(typeName);
        }
    }

    private void renderCardinality(EReference ref, StringBuilder sb) {
        sb.append("[");
        sb.append(renderBound(ref.getLowerBound()));
        sb.append(CARDINALITY_SEPARATOR);
        sb.append(renderBound(ref.getUpperBound()));
        sb.append("]");
    }

    private String renderBound(int bound) {
        if (bound == -1) {
            return CARDINALITY_UNBOUNDED;
        } else {
            return String.valueOf(bound);
        }
    }

    private void renderName(EReference ref, StringBuilder sb) {
        if (ref.getName() != null) {
            sb.append(" ");
            if (ref.isDerived()) {
                sb.append(DERIVED_PREFIX);
            }
            sb.append(ref.getName());
        }
    }

    public EReference performEdit(EReference ref, String editString) {
        if ("0".equals(editString.trim())) {
            ref.setLowerBound(0);
        } else if ("1".equals(editString.trim())) {
            ref.setLowerBound(1);
        } else if ("11".equals(editString.trim())) {
            ref.setLowerBound(1);
            ref.setUpperBound(1);
        } else if (CARDINALITY_UNBOUNDED.equals(editString.trim())) {
            ref.setUpperBound(-1);
        } else if (CARDINALITY_UNBOUNDED_ALTERNATIVE.equals(editString.trim())) {
            ref.setUpperBound(-1);
        } else {
            editName(ref, editString);
            editCardinality(ref, editString);
        }
        return ref;
    }

    private void editName(EReference ref, String editString) {
        String namePart = extractNamePart(ref, editString);
        if (namePart != null && namePart.trim().length() > 0) {
            boolean derived = namePart.startsWith(DERIVED_PREFIX);
            ref.setDerived(derived);
            ref.setName(namePart.substring(derived ? DERIVED_PREFIX.length() : 0).trim());
        }
    }

    private String extractNamePart(EReference ref, String name) {
        int end = name.indexOf("]");
        if (end != -1 && end < name.length()) {
            return name.substring(end + 1).trim();
        } else {
            return name.trim();
        }
    }

    private void editCardinality(EReference ref, String editString) {
        List<Integer> card = parseCardinality(editString);
        if (card.get(0) != null) {
            ref.setLowerBound(card.get(0).intValue());
        }
        if (card.get(1) != null) {
            ref.setUpperBound(card.get(1).intValue());
        }
    }

    /**
     * Made public only to allow testing. Returns a List instead of an array
     * because one the method is public (for testing), Acceleo will try to
     * interpret as a service, but it does not handle arrays.
     */
    public List<Integer> parseCardinality(String editString) {
        List<Integer> result = new ArrayList<>(java.util.Arrays.asList(null, null));
        String spec = extractCardinalityPart(editString);
        if (spec != null) {
            if (spec.contains(CARDINALITY_SEPARATOR)) {
                String[] parts = spec.split(Pattern.quote(CARDINALITY_SEPARATOR));
                switch (parts.length) {
                case 0:
                    break;
                case 1:
                    if (spec.startsWith(CARDINALITY_SEPARATOR)) {
                        result.set(1, parseBound(parts[0]));
                    } else if (spec.endsWith(CARDINALITY_SEPARATOR)) {
                        result.set(0, parseBound(parts[0]));
                    }
                    break;
                default: // 2 (or more, but only the first 2 are considered)
                    result.set(0, parseBound(parts[0]));
                    result.set(1, parseBound(parts[1]));
                }
            }
        }
        return result;
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

    /**
     * Made public only to allow testing.
     */
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
