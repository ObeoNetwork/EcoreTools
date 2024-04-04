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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature.Setting;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.util.ECrossReferenceAdapter;
import org.eclipse.emf.ecore.util.EcoreSwitch;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.business.api.session.SessionManager;

/**
 * A switch implementation retrieving all the elements which might be related to a single one.
 *
 * Content adapted from org.eclipse.emf.ecoretools.design.service.RelatedElementsSwitch
 *
 * @author adieumegard - Minor adaptations
 */
public class RelatedElementsSwitch extends EcoreSwitch<List<EObject>> {

    private Set<EObject> relateds;

    private Collection<Setting> xRefs;

    private ECrossReferenceAdapter referencer;

    public RelatedElementsSwitch() {

    }

    public RelatedElementsSwitch(ECrossReferenceAdapter xRef) {
        this.referencer = xRef;
    }

    public List<EObject> getRelatedElements(EObject ctx) {
        Session sess = SessionManager.INSTANCE.getSession(ctx);
        this.relateds = Sets.newLinkedHashSet();
        if (sess != null) {
            this.xRefs = sess.getSemanticCrossReferencer().getInverseReferences(ctx);
        } else if (this.referencer != null) {
            this.xRefs = this.referencer.getInverseReferences(ctx);
        }
        this.doSwitch(ctx);
        this.relateds.remove(ctx);
        // hack to prevent some null element in relateds for a unknown reason.
        this.relateds.remove(null);
        return ImmutableList.copyOf(this.relateds);
    }

    @Override
    public List<EObject> caseEClass(EClass object) {
        this.relateds.addAll(object.getESuperTypes());
        for (Setting xRef : this.xRefs) {
            if (xRef.getEObject() instanceof EClass) {
                this.relateds.add(xRef.getEObject());
            } else if (xRef.getEObject() instanceof EReference && xRef.getEStructuralFeature() == EcorePackage.eINSTANCE.getEReference_EReferenceType()) {
                this.relateds.add(((EReference) xRef.getEObject()).getEContainingClass());
            }
        }
        for (EReference eRef : object.getEReferences()) {
            if (eRef.getEType() != null) {
                this.relateds.add(eRef.getEType());
            }
        }
        return super.caseEClass(object);
    }

    @Override
    public List<EObject> caseEOperation(EOperation object) {
        if (object.getEType() != null) {
            this.relateds.add(object.getEType());
        }

        for (EParameter param : object.getEParameters()) {
            if (param.getEType() != null) {
                this.relateds.add(param.getEType());
            }
        }

        return super.caseEOperation(object);
    }

    @Override
    public List<EObject> caseEReference(EReference object) {
        this.relateds.add(object.getEType());
        return super.caseEReference(object);
    }

    @Override
    public List<EObject> caseEPackage(EPackage object) {
        this.relateds.addAll(object.getEClassifiers());
        return super.caseEPackage(object);
    }

}
