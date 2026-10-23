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

import org.eclipse.sirius.web.infrastructure.configuration.mvc.IIndexProcessor;
import org.springframework.web.servlet.function.ServerRequest;

/** Installs styles scoped by the Ecore canvas marker, without replacing the shared renderer. */
public class EcoreDiagramStyles implements IIndexProcessor {
    @Override
    public String process(ServerRequest request, String indexHtml) {
        return indexHtml.replace("</head>", "<link rel=\"stylesheet\" href=\"" + request.servletRequest().getContextPath()
                + "/ecore-editor/diagram.css\">\n</head>");
    }
}
