/*******************************************************************************
 * Copyright (c) 2024 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.components.ecore.starter.services;

import java.util.List;

import org.eclipse.sirius.components.ecore.starter.configuration.EcorePackageQueryUtils;
import org.eclipse.sirius.components.view.View;
import org.eclipse.sirius.components.view.diagram.DiagramDescription;
import org.eclipse.sirius.components.view.emf.IJavaServiceProvider;
import org.springframework.stereotype.Service;

/**
 * Used to provide services for the Ecore view.
 *
 * @author adieumegard
 */
@Service
public class EcoreJavaServiceProvider implements IJavaServiceProvider {

    @Override
    public List<Class<?>> getServiceClasses(View view) {
        boolean isEcoreView = view.getDescriptions().stream().filter(DiagramDescription.class::isInstance).map(DiagramDescription.class::cast)
                .anyMatch(diagramDescription -> diagramDescription.getDomainType().equals(EcorePackageQueryUtils.getEPackageDataTypeQualifiedName()));
        if (isEcoreView) {
            return List.of(EcoreService.class);
        }
        return List.of();
    }

}
