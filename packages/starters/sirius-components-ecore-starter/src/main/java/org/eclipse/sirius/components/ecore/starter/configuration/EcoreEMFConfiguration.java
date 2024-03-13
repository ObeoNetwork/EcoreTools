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
package org.eclipse.sirius.components.ecore.starter.configuration;

import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.provider.EcoreItemProviderAdapterFactory;
import org.eclipse.sirius.components.emf.services.ILabelFeatureProvider;
import org.eclipse.sirius.components.emf.services.LabelFeatureProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration of the EMF support for Ecore MM.
 *
 * @author adieumegard
 */
@Configuration
public class EcoreEMFConfiguration {

    @Bean
    public AdapterFactory ecoreAdapterFactory() {
        return new EcoreItemProviderAdapterFactory();
    }

    @Bean
    public EPackage ecoreEPackage() {
        return EcorePackage.eINSTANCE;
    }

    @Bean
    public ILabelFeatureProvider ecoreLabelFeatureProvider() {
        return new LabelFeatureProvider(EcorePackage.eINSTANCE.getNsURI(), new EcoreLabelFeatureSwitch(), new EcoreEditableSwitch());
    }

}
