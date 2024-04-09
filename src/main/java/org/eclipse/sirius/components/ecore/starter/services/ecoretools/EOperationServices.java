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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.ETypedElement;
import org.eclipse.emf.ecore.EcoreFactory;

/**
 * Services dealing with EOperations usable from a VSM.
 *
 * Content adapted from org.eclipse.emf.ecoretools.design.service.EOperationServices
 *
 * @author adieumegard - Minor adaptations
 */
public class EOperationServices {
    private static final String TYPE_SEPARATOR = ":";

    private static final String PARAMETERS_SEPARATOR = ",";

    private static final String CLOSING_PARENTHESIS = ")";

    private static final String OPENING_PARENTHESIS = "(";

    private static final String SINGLE_SPACE = " ";

    /**
     * Computes the label of an EOperation.
     */
    public String render(EOperation op) {
        StringBuilder sb = new StringBuilder();
        this.renderName(op, sb);
        this.renderParameters(op, sb, true);
        this.renderType(op, sb);
        return sb.toString();
    }

    /**
     * Computes the tooltip of an EOperation (same as label but excluding the parameters Types) .
     *
     * @param op
     *            the operation to be rendered
     * @return the tooltip of the given EOperation (same as label but excluding the parameters Types)
     */
    public String renderEOperationTooltip(EOperation op) {
        StringBuilder sb = new StringBuilder();
        this.renderName(op, sb);
        this.renderParameters(op, sb, false);
        this.renderType(op, sb);
        return sb.toString();
    }

    /**
     * Performs a "direct edit" operation on an EOperation.
     */
    public EOperation performEdit(EOperation op, String editString) {
        this.editName(op, editString);
        this.editParameters(op, editString);
        this.editType(op, editString);
        return op;
    }

    private void renderName(EOperation op, StringBuilder sb) {
        if (op.getName() != null) {
            sb.append(op.getName());
        }
    }

    /**
     * Renders the parameters of the given EOperation.
     *
     * @param op
     *            the operation to render
     * @param sb
     *            the string builder used to render the eOperation
     * @param includeParameterType
     *            indicates whether parameter's Types should be rendered or not
     */
    private void renderParameters(EOperation op, StringBuilder sb, boolean includeParameterType) {
        sb.append(OPENING_PARENTHESIS);
        boolean first = true;
        for (EParameter param : op.getEParameters()) {
            if (!first) {
                sb.append(PARAMETERS_SEPARATOR).append(SINGLE_SPACE);
            } else {
                first = false;
            }
            sb.append(this.renderParameter(param, includeParameterType));
        }
        sb.append(CLOSING_PARENTHESIS);
    }

    /**
     * Renders the given Parameter.
     *
     * @param parameter
     *            the parameter to render
     * @param includeParameterType
     *            indicates whether parameter's Type should be rendered or not
     */
    private String renderParameter(EParameter parameter, boolean includeParameterType) {
        String name = parameter.getName();
        String typeName = EGenericsServices.getETypeLabel(parameter);
        if (name != null && typeName != null && includeParameterType) {
            name += SINGLE_SPACE + typeName;
        } else if (name != null) {
            name = parameter.getName();
        } else {
            name = "";
        }
        return name;
    }

    private void renderType(EOperation op, StringBuilder sb) {
        String typeLabel = EGenericsServices.getETypeLabel(op);
        if (typeLabel != null) {
            sb.append(SINGLE_SPACE).append(TYPE_SEPARATOR).append(SINGLE_SPACE);
            sb.append(typeLabel);
        }
    }

    private void editName(EOperation op, String editString) {
        int nameEnd = editString.length();
        int typeStart = editString.indexOf(TYPE_SEPARATOR);
        if (typeStart != -1) {
            nameEnd = Math.min(nameEnd, typeStart);
        }
        int paramStart = editString.indexOf(OPENING_PARENTHESIS);
        if (paramStart != -1) {
            nameEnd = Math.min(nameEnd, paramStart);
        }

        String namePart = editString.substring(0, nameEnd);
        if (namePart.trim().length() > 0) {
            op.setName(namePart.trim());
        } else if (typeStart == -1 && paramStart == -1) {
            // Reset the name only if no other parts have been specified.
            op.setName(null);
        }
    }

    private void editParameters(EOperation op, String editString) {
        int paramStart = editString.indexOf(OPENING_PARENTHESIS);
        int paramEnd = editString.lastIndexOf(CLOSING_PARENTHESIS);
        if (paramStart != -1 && paramEnd != -1 && paramStart < paramEnd) {
            List<String[]> spec = this.parseParameters(editString.substring(paramStart + 1, paramEnd));
            int nbParam = spec.size();
            // Remove parameters from the end if we have too many.
            for (int i = op.getEParameters().size() - 1; i >= nbParam; i--) {
                op.getEParameters().remove(i);
            }
            // Update existing parameters with new spec and append additional
            // ones if necessary
            for (int i = 0; i < nbParam; i++) {
                EParameter param = null;
                if (i < op.getEParameters().size()) {
                    param = op.getEParameters().get(i);
                } else {
                    param = EcoreFactory.eINSTANCE.createEParameter();
                    op.getEParameters().add(param);
                }
                this.updateParameter(op, param, spec.get(i));
            }
        }
    }

    private void updateParameter(EOperation op, EParameter param, String[] paramSpec) {
        if (paramSpec[0] != null) {
            param.setName(paramSpec[0]);
        }
        if (paramSpec[1] != null) {
            EClassifier type = new DesignServices().findTypeByName(op, paramSpec[1]);
            if (type != null) {
                param.setEType(type);
            }
        }
    }

    private List<String[]> parseParameters(String spec) {
        List<String[]> result = new ArrayList<>();
        if (spec.contains(PARAMETERS_SEPARATOR)) {
            String[] params = spec.split(PARAMETERS_SEPARATOR);
            for (String paramSpec : params) {
                result.add(this.parseParameter(paramSpec));
            }
        } else if (spec.trim().length() > 0) {
            result.add(this.parseParameter(spec));
        }
        return result;
    }

    /**
     * Parses a single parameter specification. Supports the following syntaxes:
     *
     * <pre>
     * "name"
     * "name Type"
     * "name : Type"
     * " : Type"
     * </pre>
     *
     * Returns an array of two strings, representing the parameter name and the type name. Both can be <code>null</code>
     * if the corresponding part is not specified.
     *
     * Note: this method is public only so that it can be visible from tests.
     */
    public String[] parseParameter(String paramSpec) {
        String[] type = null;
        // Try ":" as type separator
        int typeStart = paramSpec.indexOf(TYPE_SEPARATOR);
        if (typeStart != -1) {
            type = this.splitParameterSpec(paramSpec, typeStart);
        } else {
            // If there is no ":", assume whitespace separates the name from the
            // type
            String newParamSpec = paramSpec.trim();
            typeStart = newParamSpec.lastIndexOf(SINGLE_SPACE);
            if (typeStart != -1) {
                type = this.splitParameterSpec(newParamSpec, typeStart);
            } else {
                typeStart = newParamSpec.lastIndexOf("\t");
                if (typeStart != -1) {
                    type = this.splitParameterSpec(newParamSpec, typeStart);
                }
            }
        }
        // If there was neither ":" nor space, the spec can specify only the
        // name, or maybe nothing if it is blank.
        if (type == null) {
            String name = paramSpec.trim();
            if (name.length() == 0) {
                name = null;
            }
            type = new String[] { name, null };
        }
        return type;
    }

    public List<ENamedElement> getAllAssociatedElements(EOperation op) {
        // [eParameters->including(self)->asSequence()->sortedBy(name)/]
        List<ENamedElement> result = Lists.newArrayListWithExpectedSize(1 + op.getEParameters().size());
        result.add(op);
        result.addAll(op.getEParameters());
        Collections.sort(result, Ordering.natural().onResultOf(new Function<ENamedElement, String>() {
            @Override
            public String apply(ENamedElement input) {
                return input.getName();
            }
        }));
        return result;
    }

    private String[] splitParameterSpec(String paramSpec, int typeStart) {
        String[] type;
        String name;
        String typeName;
        name = paramSpec.substring(0, typeStart).trim();
        if (typeStart != paramSpec.length() - 1) {
            typeName = paramSpec.substring(typeStart + 1).trim();
        } else {
            typeName = null;
        }
        if (name.length() == 0) {
            name = null;
        }
        if (typeName != null && typeName.length() == 0) {
            typeName = null;
        }
        type = new String[] { name, typeName };
        return type;
    }

    private void editType(EOperation op, String editString) {
        Object value = this.getSpecifiedType(op, editString);
        if (value != null) {
            EGenericsServices.setETypeWithGenerics(op, value);
        } else {
            // Only reset the type to null if the ":" is explicitly present with
            // no type specified
            int typeStart = editString.lastIndexOf(TYPE_SEPARATOR);
            if (typeStart != -1 && !editString.substring(typeStart).contains(CLOSING_PARENTHESIS)) {
                op.setEType(null);
            }
        }
    }

    private Object getSpecifiedType(ETypedElement receiver, String editString) {
        int typeStart = editString.lastIndexOf(TYPE_SEPARATOR);
        if (typeStart != -1 && editString.length() > typeStart + 1) {
            String typeName = editString.substring(typeStart + 1).trim();
            if (!typeName.contains(CLOSING_PARENTHESIS)) {
                Object value = EGenericsServices.findGenericType(receiver, typeName);
                if (value == null) {
                    value = new DesignServices().findTypeByName(receiver, typeName);
                }
                return value;
            }
        }
        return null;
    }
}
