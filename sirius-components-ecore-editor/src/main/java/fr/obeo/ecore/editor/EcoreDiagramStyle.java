/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package fr.obeo.ecore.editor;

/** Ecore-specific visual tokens; the Sirius renderer supplies fonts and interaction states. */
public final class EcoreDiagramStyle {
    public static final String CANVAS = "#f8f9fc";
    public static final String SURFACE = "#ffffff";
    public static final String HEADER_BACKGROUND = "var(--ecore-header-background, #e9e9f3)";
    public static final String TEXT = "#1e2644";
    public static final String SECONDARY = "#566179";
    public static final String BORDER = "#788398";
    public static final String RELATIONSHIP = "#63708a";
    public static final String CLASS = "#6561a8";
    public static final String ENUM = "#16786f";
    public static final String DATATYPE = "#2d6f9f";
    public static final String PACKAGE = "#7b5ba9";
    public static final String NOTE = "#f5f6fa";
    public static final String CONSTRAINT = "#eef4f9";
    public static final String ERROR = "#c62828";
    public static final int TITLE_SIZE = 14;
    public static final int MEMBER_SIZE = 13;
    public static final int EDGE_SIZE = 12;
    public static final int RADIUS = 6;

    private EcoreDiagramStyle() { }
}
