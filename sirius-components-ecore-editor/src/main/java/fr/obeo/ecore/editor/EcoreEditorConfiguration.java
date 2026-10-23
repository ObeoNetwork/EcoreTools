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
import org.eclipse.sirius.components.core.api.IImagePathService;
import org.eclipse.sirius.components.view.View;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Import this configuration to install the editor in any Sirius Web application. */
@Configuration(proxyBeanMethods = false)
@Import({EcoreJavaServiceProvider.class, EcoreViewEditingContextInitializer.class, EcoreLabelService.class,
        EcoreOppositeLabelPostProcessor.class, EcoreResourceLoader.class, EcoreResourceToDocumentService.class,
        EcoreConnectorPaletteProvider.class, EcoreReconnectionToolsExecutor.class})
public class EcoreEditorConfiguration {
    @Bean
    org.eclipse.emf.ecore.EPackage ecoreMetamodel() { return org.eclipse.emf.ecore.EcorePackage.eINSTANCE; }

    @Bean
    org.eclipse.emf.edit.provider.ComposedAdapterFactory.Descriptor ecoreEditDescriptor() {
        return org.eclipse.emf.ecore.provider.EcoreItemProviderAdapterFactory::new;
    }

    @Bean
    View ecoreView() { return new EcoreViews().create(); }

    @Bean
    IImagePathService ecoreImagePaths() { return () -> List.of("/icons/ecore/"); }
}
