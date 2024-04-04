/*******************************************************************************
 * Copyright (c) 2013 - 2024 THALES GLOBAL SERVICES and Others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.components.ecore.starter.services.ecoretools;

import com.google.common.base.Optional;
import com.google.common.collect.Iterators;

import java.util.Iterator;

import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;

/**
 * Content adapted from org.eclipse.emf.ecoretools.design.service.DiagnosticAttachment.
 *
 * @author adieumegard - Minor adaptations
 */
public class DiagnosticAttachment extends AdapterImpl {

    private Diagnostic diagnostic;

    public Diagnostic getDiagnostic() {
        return this.diagnostic;
    }

    public void setDiagnostic(Diagnostic diagnostic) {
        this.diagnostic = diagnostic;
    }

    public static DiagnosticAttachment getAttachment(EObject cur) {
        Iterator<DiagnosticAttachment> it = Iterators.filter(cur.eAdapters().iterator(), DiagnosticAttachment.class);
        DiagnosticAttachment found = null;
        if (it.hasNext()) {
            found = it.next();
        }
        return found;
    }

    public static DiagnosticAttachment getOrCreate(EObject cur, Diagnostic diag) {
        DiagnosticAttachment found = getAttachment(cur);
        if (found == null) {
            found = new DiagnosticAttachment();
            cur.eAdapters().add(found);
            found.setDiagnostic(diag);
        }
        return found;
    }

    public static Optional<Diagnostic> get(EObject cur) {
        DiagnosticAttachment found = getAttachment(cur);
        if (found != null) {
            return Optional.fromNullable(found.getDiagnostic());
        }
        return Optional.absent();
    }

}
