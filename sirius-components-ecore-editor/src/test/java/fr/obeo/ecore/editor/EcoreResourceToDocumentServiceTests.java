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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.view.ViewFactory;
import org.eclipse.sirius.web.application.editingcontext.services.DocumentData;
import org.eclipse.sirius.web.application.editingcontext.services.ResourceToDocumentService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EcoreResourceToDocumentServiceTests {
    @ParameterizedTest
    @CsvSource({"model,true,false,model.ecore", "model.ecore,true,false,model.ecore",
            "model.xmi,true,false,model.xmi.ecore", "model,false,false,model", "model,true,true,model"})
    void onlyWritableEcoreDocumentsReceiveTheExtension(String name, boolean ecore, boolean readOnly, String expected) {
        var resource = new JSONResourceFactory().createResourceFromPath("document");
        resource.getContents().add(ecore ? EcoreFactory.eINSTANCE.createEPackage() : ViewFactory.eINSTANCE.createView());
        var metadata = new ResourceMetadataAdapter(name, readOnly);
        resource.eAdapters().add(metadata);
        var delegate = mock(ResourceToDocumentService.class);
        var document = Optional.of(mock(DocumentData.class));
        when(delegate.toDocument(resource, true)).thenReturn(document);
        var service = new EcoreResourceToDocumentService(delegate);
        assertThat(service.toDocument(resource, true)).isSameAs(document);
        assertThat(metadata.getName()).isEqualTo(expected);
        service.toDocument(resource, true);
        assertThat(metadata.getName()).isEqualTo(expected);
        verify(delegate, times(2)).toDocument(resource, true);
    }
}
