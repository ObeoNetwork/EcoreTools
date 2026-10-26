/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package fr.obeo.ecore.application;
import fr.obeo.ecore.editor.EcoreEditorConfiguration;
import org.eclipse.sirius.web.application.studio.OnStudioEnabled;
import org.eclipse.sirius.web.application.studio.services.api.IStudioCapableEditingContextPredicate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@SpringBootApplication
@Import(EcoreEditorConfiguration.class)
public class EcoreEditorApplication {
    @Bean
    @Primary
    @Conditional(OnStudioEnabled.class)
    IStudioCapableEditingContextPredicate ecoreStudioCapability() {
        // Every project can define Ecore metamodels for Studio when Studio is enabled.
        return editingContextId -> true;
    }

    public static void main(String[] args) { SpringApplication.run(EcoreEditorApplication.class, args); }
}
