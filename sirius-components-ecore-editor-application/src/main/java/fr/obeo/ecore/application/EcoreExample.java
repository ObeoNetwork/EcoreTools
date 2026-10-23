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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;

/** The editable Ecore model corresponding to the EcoreTools reference image. */
final class EcoreExample {
    private EcoreExample() { }

    static EPackage load() {
        var resources = new ResourceSetImpl();
        var resource = new EcoreResourceFactoryImpl().createResource(URI.createURI("classpath:/templates/example.ecore"));
        resources.getResources().add(resource);
        try (var input = Objects.requireNonNull(EcoreExample.class.getResourceAsStream("/templates/example.ecore"))) {
            resource.load(input, Map.of());
            EcoreUtil.resolveAll(resource);
            return (EPackage) resource.getContents().getFirst();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot load the Ecore example", exception);
        }
    }
}
