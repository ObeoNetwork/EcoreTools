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

import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.ETypedElement;
import org.eclipse.emf.ecore.EcoreFactory;


/**
 * Services dealing with EOperations usable from a VSM.
 */
public class EOperationServices {
	private static final String TYPE_SEPARATOR = ":";

	private static final String PARAMETERS_SEPARATOR = ",";

	/**
	 * Computes the label of an EOperation.
	 */
	public String render(EOperation operation) {
		StringBuilder label = new StringBuilder();
		renderName(operation, label);
		renderParameters(operation, label, true);
		renderReturnType(operation, label);
		return label.toString();
	}

	/**
	 * Computes the tooltip of an EOperation (same as label but excluding the
	 * parameters Types) .
	 * 
	 * @param operation
	 *            the operation to be rendered
	 * @return the tooltip of the given EOperation (same as label but excluding
	 *         the parameters Types)
	 */
	public String renderEOperationTooltip(EOperation operation) {
		StringBuilder label = new StringBuilder();
		renderName(operation, label);
		renderParameters(operation, label, false);
		renderReturnType(operation, label);
		return label.toString();
	}

	/**
	 * Mutates and returns the operation, reusing parameters by position and removing excess ones.
	 * Omitted parameter/return clauses keep existing values; an explicit unresolved return type clears it.
	 */
	public EOperation performEdit(EOperation operation, String editString) {
		editName(operation, editString);
		editParameters(operation, editString);
		editReturnType(operation, editString);
		return operation;
	}

	private void renderName(EOperation operation, StringBuilder label) {
		if (operation.getName() != null) {
			label.append(operation.getName());
		}
	}

	/**
	 * Renders the parameters of the given EOperation.
	 * 
	 * @param operation
	 *            the operation to render
	 * @param label
	 *            the string builder used to render the eOperation
	 * @param includeParameterType
	 *            indicates whether parameter's Types should be rendered or not
	 */
	private void renderParameters(EOperation operation, StringBuilder label,
			boolean includeParameterType) {
		label.append("(");
		boolean first = true;
		for (EParameter parameter : operation.getEParameters()) {
			if (!first) {
				label.append(PARAMETERS_SEPARATOR).append(" ");
			} else {
				first = false;
			}
			label.append(renderParameter(parameter, includeParameterType));
		}
		label.append(")");
	}

	/**
	 * Renders the given Parameter.
	 * 
	 * @param parameter
	 *            the parameter to render
	 * @param includeParameterType
	 *            indicates whether parameter's Type should be rendered or not
	 */
	private String renderParameter(EParameter parameter,
			boolean includeParameterType) {
		String name = parameter.getName();
		String typeName = EGenericsServices.getETypeLabel(parameter);
		if (name != null && typeName != null && includeParameterType) {
			return parameter.getName() + " " + typeName;
		} else if (name != null) {
			return parameter.getName();
		} else {
			return "";
		}
	}

	private void renderReturnType(EOperation operation, StringBuilder label) {
		String typeLabel = EGenericsServices.getETypeLabel(operation);
		if (typeLabel != null) {
			label.append(" ").append(TYPE_SEPARATOR).append(" ");
			label.append(typeLabel);
		}
	}

	private void editName(EOperation operation, String editString) {
		int nameEnd = editString.length();
		int typeStart = editString.indexOf(TYPE_SEPARATOR);
		if (typeStart != -1) {
			nameEnd = Math.min(nameEnd, typeStart);
		}
		int parametersStart = editString.indexOf("(");
		if (parametersStart != -1) {
			nameEnd = Math.min(nameEnd, parametersStart);
		}

		String namePart = editString.substring(0, nameEnd);
		if (namePart.trim().length() > 0) {
			operation.setName(namePart.trim());
		} else if (typeStart == -1 && parametersStart == -1) {
			// Reset the name only if no other parts have been specified.
			operation.setName(null);
		}
	}

	private void editParameters(EOperation operation, String editString) {
		int parametersStart = editString.indexOf("(");
		int parametersEnd = editString.lastIndexOf(")");
		if (parametersStart != -1 && parametersEnd != -1 && parametersStart < parametersEnd) {
			List<String[]> parameterSpecifications = parseParameters(editString.substring(
					parametersStart + 1, parametersEnd));
			int parameterCount = parameterSpecifications.size();
			for (int i = operation.getEParameters().size() - 1; i >= parameterCount; i--) {
				operation.getEParameters().remove(i);
			}
			// Reusing parameter objects preserves their IDs and references across direct edits.
			for (int i = 0; i < parameterCount; i++) {
				EParameter parameter = null;
				if (i < operation.getEParameters().size()) {
					parameter = operation.getEParameters().get(i);
				} else {
					parameter = EcoreFactory.eINSTANCE.createEParameter();
					operation.getEParameters().add(parameter);
				}
				updateParameter(operation, parameter, parameterSpecifications.get(i));
			}
		}
	}

	private void updateParameter(EOperation operation, EParameter parameter,
			String[] parameterSpec) {
		if (parameterSpec[0] != null) {
			parameter.setName(parameterSpec[0]);
		}
		if (parameterSpec[1] != null) {
			EClassifier type = new DesignServices().findTypeByName(operation,
					parameterSpec[1]);
			if (type != null) {
				parameter.setEType(type);
			}
		}
	}

	private List<String[]> parseParameters(String parameterListText) {
		List<String[]> result = new ArrayList<>();
		if (parameterListText.contains(PARAMETERS_SEPARATOR)) {
			String[] params = parameterListText.split(PARAMETERS_SEPARATOR);
			for (String parameterSpec : params) {
				result.add(parseParameter(parameterSpec));
			}
		} else if (parameterListText.trim().length() > 0) {
			result.add(parseParameter(parameterListText));
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
	 * Returns an array of two strings, representing the parameter name and the
	 * type name. Both can be <code>null</code> if the corresponding part is not
	 * specified.
	 * 
	 */
	public String[] parseParameter(String parameterSpec) {
		int typeStart = parameterSpec.indexOf(TYPE_SEPARATOR);
		if (typeStart != -1) {
			return splitParameterSpec(parameterSpec, typeStart);
		}

		parameterSpec = parameterSpec.trim();
		// Preserve the legacy precedence of a space separator over a tab separator.
		typeStart = parameterSpec.lastIndexOf(" ");
		if (typeStart == -1) {
			typeStart = parameterSpec.lastIndexOf("\t");
		}
		if (typeStart != -1) {
			return splitParameterSpec(parameterSpec, typeStart);
		}
		return new String[] { parameterSpec.isEmpty() ? null : parameterSpec, null };
	}


	private String[] splitParameterSpec(String parameterSpec, int typeStart) {
		String name = parameterSpec.substring(0, typeStart).trim();
		String typeName;
		if (typeStart != parameterSpec.length() - 1) {
			typeName = parameterSpec.substring(typeStart + 1).trim();
		} else {
			typeName = null;
		}
		if (name.length() == 0) {
			name = null;
		}
		if (typeName != null && typeName.length() == 0) {
			typeName = null;
		}
		return new String[] { name, typeName };
	}

	private void editReturnType(EOperation operation, String editString) {
		Object resolvedType = resolveReturnType(operation, editString);
		if (resolvedType != null) {
			EGenericsServices.setETypeWithGenerics(operation, resolvedType);
		} else {
			// A parameter type separator must not clear the operation's return type.
			int typeStart = editString.lastIndexOf(TYPE_SEPARATOR);
			if (typeStart != -1
					&& !editString.substring(typeStart).contains(")")) {
				operation.setEType(null);
			}
		}
	}

	private Object resolveReturnType(ETypedElement receiver, String editString) {
		int typeStart = editString.lastIndexOf(TYPE_SEPARATOR);
		if (typeStart != -1 && editString.length() > typeStart + 1) {
			String typeName = editString.substring(typeStart + 1).trim();
			if (!typeName.contains(")")) {
				Object resolvedType = EGenericsServices.findGenericType(receiver,
						typeName);
				if (resolvedType == null) {
					resolvedType = new DesignServices().findTypeByName(receiver,
							typeName);
				}
				return resolvedType;
			}
		}
		return null;
	}
}
