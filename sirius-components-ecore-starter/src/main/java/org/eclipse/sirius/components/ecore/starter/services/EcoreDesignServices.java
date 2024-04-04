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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.sirius.components.core.api.IObjectService;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.ViewModifier;
import org.eclipse.sirius.components.diagrams.elements.NodeElementProps;
import org.eclipse.sirius.components.diagrams.renderer.DiagramRenderingCache;
import org.eclipse.sirius.components.ecore.starter.services.ecoretools.DesignServices;
import org.eclipse.sirius.components.representations.Element;

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

    /**
     * Queries the diagram to get all the EClass elements whose State is not {@link ViewModifier.Hidden}.
     * @param context
     *
     * @param diagram
     *            The current diagram, provided by the {@code previousDiagram} variable.
     * @param cache
     *            The diagram cache, provided by the {@code cache} variable.
     * @return
     */
    public Set<EClass> getDisplayedEClasses(EPackage context, Diagram diagram, DiagramRenderingCache cache) {
        Set<EClass> result = Sets.newLinkedHashSet();
        TreeIterator<EObject> packageContentsIterator = context.eAllContents();
        while (packageContentsIterator.hasNext()) {
            EObject next = packageContentsIterator.next();
            if (next instanceof EClass clazz) {
                List<Element> classNodes = cache.getObjectToNodes().get(clazz);
                for (Element classNode : classNodes) {
                    if (classNode.getProps() instanceof NodeElementProps props) {
                        if (!props.getState().equals(ViewModifier.Hidden)) {
                            result.add(clazz);
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Gets the eSuperTypes of an EClass. If the representation of one of the eSuperTypes is
     * {@link ViewModifier.Hidden}, computes the "closest" visible eSuperTypes by searching it in the closure of the
     * eSuperTypes relation.
     *
     * @param self
     *            The EClass used as the context of the search.
     *
     * @param diagram
     *            The current diagram, provided by the {@code previousDiagram} variable.
     * @param cache
     *            The diagram cache, provided by the {@code cache} variable.
     * @return
     */
    public Set<EClass> getDirectSuperTypesOrMostSpecificVisibleOnes(EClass self, Diagram diagram, DiagramRenderingCache cache) {
        Set<EClass> result = Sets.newLinkedHashSet();
        Set<EClass> displayed = this.getDisplayedOrElseAvailableEClasses(self.getEPackage(), diagram, cache);
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

    /**
     * Gets the list of {@link EReference} to be displayed on the {@code diagram} based on the visibility of the
     * {@code EClass} elements.
     *
     * @param root
     *            The {@link EPackage} used as context of the computation.
     *
     * @param diagram
     *            The current diagram, provided by the {@code previousDiagram} variable.
     * @param cache
     *            The diagram cache, provided by the {@code cache} variable.
     * @return
     */
    public Set<EReference> getEReferencesToDisplay(EPackage root, Diagram diagram, DiagramRenderingCache cache) {
        // [diagram.getDisplayedEClasses().oclAsType(ecore::EClass).eAllReferences->flatten()/]
        Collection<EClass> eClasses = this.getDisplayedOrElseAvailableEClasses(root, diagram, cache);
        Set<EReference> eRefs = Sets.newLinkedHashSet();
        for (EClass clazz : eClasses) {
            eRefs.addAll(clazz.getEAllReferences());
        }
        return eRefs;
    }

    /**
     * Gets the list of eOpposite {@link EReference} to be displayed on the {@code diagram} based on the visibility of
     * the {@code EClass} elements.
     *
     * @param root
     *            The {@link EPackage} used as context of the computation.
     *
     * @param diagram
     *            The current diagram, provided by the {@code previousDiagram} variable.
     * @param cache
     *            The diagram cache, provided by the {@code cache} variable.
     * @return
     */
    public List<EReference> getEOppositeEReferences(EPackage self, Diagram diagram, DiagramRenderingCache cache) {
        Set<EClass> eClasses = this.getDisplayedOrElseAvailableEClasses(self, diagram, cache);
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
     * Retrieve a set of EClasses displayed in the diagram. If the {@code diagram} is null then retrieve all
     * {@link EClass} directly available from {@code context}.
     *
     * @param context
     *            The {@link EPackage} used as context of the computation.
     *
     * @param diagram
     *            The current diagram, provided by the {@code previousDiagram} variable.
     * @param cache
     *            The diagram cache, provided by the {@code cache} variable.
     * @return
     */
    private Set<EClass> getDisplayedOrElseAvailableEClasses(EPackage context, Diagram diagram, DiagramRenderingCache cache) {
        Set<EClass> eClasses;
        if (cache != null) {
            eClasses = this.getDisplayedEClasses(context, diagram, cache);
        } else {
            eClasses = new HashSet<>(context.getEClassifiers().stream().filter(EClass.class::isInstance).map(EClass.class::cast).toList());
        }
        return eClasses;
    }

}
