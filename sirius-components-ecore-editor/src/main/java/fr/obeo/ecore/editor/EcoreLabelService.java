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

import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.sirius.components.core.api.labels.StyledString;
import org.eclipse.sirius.components.emf.services.api.IDefaultEMFLabelService;
import org.eclipse.sirius.components.emf.services.api.IEMFLabelServiceDelegate;

/** Keep the standard EMF Edit labels and property providers, replace only their images. */
public class EcoreLabelService implements IEMFLabelServiceDelegate {
    private final IDefaultEMFLabelService delegate;
    public EcoreLabelService(IDefaultEMFLabelService delegate) { this.delegate = delegate; }
    @Override
    public boolean canHandle(EObject self) { return self.eClass().getEPackage() == EcorePackage.eINSTANCE; }
    @Override
    public StyledString getStyledLabel(EObject self) { return this.delegate.getStyledLabel(self); }
    @Override
    public List<String> getImagePaths(EObject self) { return List.of(new EcoreServices().ecoreIcon(self)); }
}
