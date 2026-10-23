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

import java.util.List;
import org.eclipse.sirius.components.core.api.IFeedbackMessageService;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.representations.Failure;
import org.eclipse.sirius.components.representations.Message;
import org.eclipse.sirius.components.representations.MessageLevel;
import org.eclipse.sirius.components.representations.Success;
import org.eclipse.sirius.components.view.emf.diagram.IDiagramIdProvider;
import org.eclipse.sirius.components.view.emf.diagram.ViewReconnectionToolsExecutor;
import org.junit.jupiter.api.Test;

class EcoreReconnectionToolsExecutorTests {
    @Test
    void preservesConflictFeedbackAndDelegatesOtherResults() {
        var delegate = mock(ViewReconnectionToolsExecutor.class);
        var feedback = mock(IFeedbackMessageService.class);
        var ids = mock(IDiagramIdProvider.class);
        when(ids.getId(any(org.eclipse.sirius.components.view.diagram.DiagramDescription.class))).thenReturn("ecore");
        var executor = new EcoreReconnectionToolsExecutor(delegate, feedback, new EcoreViews().create(), ids);
        var description = mock(DiagramDescription.class);
        when(description.getId()).thenReturn("ecore");
        assertThat(executor.canExecute(description)).isTrue();
        when(description.getId()).thenReturn("other");
        assertThat(executor.canExecute(description)).isFalse();
        var message = new Message("The target already has documentation", MessageLevel.ERROR);
        when(feedback.getFeedbackMessages()).thenReturn(List.of(message));
        when(delegate.execute(null, null, null, null, null, description)).thenReturn(new Failure(""));
        assertThat(((Failure) executor.execute(null, null, null, null, null, description)).getMessages()).containsExactly(message);
        var success = new Success();
        when(delegate.execute(null, null, null, null, null, description)).thenReturn(success);
        assertThat(executor.execute(null, null, null, null, null, description)).isSameAs(success);
        var failure = new Failure("Other failure");
        when(feedback.getFeedbackMessages()).thenReturn(List.of());
        when(delegate.execute(null, null, null, null, null, description)).thenReturn(failure);
        assertThat(executor.execute(null, null, null, null, null, description)).isSameAs(failure);
    }
}
