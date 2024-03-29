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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
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
        Set<EClass> displayed = this.getDisplayedOrElseAvailableEClasses(self.getEPackage(), editingContext, diagram);
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

    public Set<EReference> getEReferencesToDisplay(EPackage root, IEditingContext editingContext, Diagram diagram) {
        // [diagram.getDisplayedEClasses().oclAsType(ecore::EClass).eAllReferences->flatten()/]
        Collection<EClass> eClasses = this.getDisplayedOrElseAvailableEClasses(root, editingContext, diagram);
        Set<EReference> eRefs = Sets.newLinkedHashSet();
        for (EClass clazz : eClasses) {
            eRefs.addAll(clazz.getEAllReferences());
        }
        return eRefs;
    }

    public List<EReference> getEOppositeEReferences(EPackage self, IEditingContext editingContext, Diagram diagram) {
        Set<EClass> eClasses = this.getDisplayedOrElseAvailableEClasses(self, editingContext, diagram);
        Set<EReference> references = Sets.newLinkedHashSet();
        for (EClass clazz : eClasses) {
            references.addAll(clazz.getEReferences());
        }
        Map<String, EReference> map = new HashMap<>();
        for (EReference ref : references) {
            if (ref.getEOpposite() != null) {
                String key1 = ref.getEOpposite().hashCode() + "" + ref.hashCode();
                String key2 = ref.hashCode() + "" + ref.getEOpposite().hashCode();
                if (map.get(key1) == null && map.get(key2) == null) {
                    map.put(key1, ref);
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    /**
     * Retrieve a set of EClasses displayed in the diagram. if the {@code diagram} is null then retrieve all EClasses directly available from {@code self}.
     * @param context
     * @param editingContext
     * @param diagram
     * @return
     */
    private Set<EClass> getDisplayedOrElseAvailableEClasses(EPackage context, IEditingContext editingContext, Diagram diagram) {
        Set<EClass> eClasses;
        if (diagram != null) {
            eClasses = this.getDisplayedEClasses(editingContext, diagram);
        } else {
            eClasses = new HashSet<>(context.getEClassifiers().stream().filter(EClass.class::isInstance).map(EClass.class::cast).toList());
        }
        return eClasses;
    }

}
