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

import com.google.common.collect.Sets;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IObjectService;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.components.diagrams.ViewModifier;
import org.eclipse.sirius.components.ecore.starter.services.ecoretools.DesignServices;

/**
 * Design and Diagram related services.
 *
 * @author adieumegard
 */
public class EcoreDesignServices {

    private final IObjectService objectService;

    public EcoreDesignServices(IObjectService objectService) {
        this.objectService = Objects.requireNonNull(objectService);
    }

    public Set<EClass> getDisplayedEClasses(IEditingContext editingContext, Diagram diagram) {
        Set<EClass> result = Sets.newLinkedHashSet();
        Iterator<Node> it = diagram.getNodes().iterator();
        while (it.hasNext()) {
            Node dec = it.next();
            String targetObjectId = dec.getTargetObjectId();
            Optional<Object> object = this.objectService.getObject(editingContext, targetObjectId);
            if (object.isPresent()) {
                if (object.get() instanceof EClass && !dec.getState().equals(ViewModifier.Hidden)) {
                    result.add((EClass) object.get());
                }
            }
        }
        return result;
    }

    public Set<EClass> getDirectSuperTypesOrMostSpecificVisibleOnes(EClass self, IEditingContext editingContext, Diagram diagram) {
        Set<EClass> result = Sets.newLinkedHashSet();
        Set<EClass> displayed;
        if (diagram != null) {
            displayed = this.getDisplayedEClasses(editingContext, diagram);
        } else {
            displayed = new HashSet<>(self.getEPackage().getEClassifiers().stream().filter(EClass.class::isInstance).map(EClass.class::cast).toList());
        }
        for (EClass directSuperType : self.getESuperTypes()) {
            if (displayed.contains(directSuperType)) {
                result.add(directSuperType);
            } else {
                Set<EClass> mostSpecificDisplayed = new DesignServices().findMostSpecificAndVisible(directSuperType.getESuperTypes(), displayed);
                if (mostSpecificDisplayed != null) {
                    result.addAll(mostSpecificDisplayed);
                }
            }
        }
        return result;
    }

}
