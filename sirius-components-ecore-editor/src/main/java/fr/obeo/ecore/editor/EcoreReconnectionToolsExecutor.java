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

import java.util.stream.Stream;
import org.eclipse.sirius.components.collaborative.diagrams.api.IReconnectionToolsExecutor;
import org.eclipse.sirius.components.collaborative.diagrams.api.ReconnectionToolInterpreterData;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IFeedbackMessageService;
import org.eclipse.sirius.components.diagrams.Edge;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.diagrams.description.EdgeDescription;
import org.eclipse.sirius.components.diagrams.events.ReconnectEdgeKind;
import org.eclipse.sirius.components.representations.Failure;
import org.eclipse.sirius.components.representations.IStatus;
import org.eclipse.sirius.components.view.View;
import org.eclipse.sirius.components.view.emf.diagram.IDiagramIdProvider;
import org.eclipse.sirius.components.view.emf.diagram.ViewReconnectionToolsExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/** Sirius 2026.7's reconnection executor drops feedback messages on failure. */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EcoreReconnectionToolsExecutor implements IReconnectionToolsExecutor {
    private final ViewReconnectionToolsExecutor delegate;
    private final IFeedbackMessageService feedback;
    private final String descriptionId;

    public EcoreReconnectionToolsExecutor(ViewReconnectionToolsExecutor delegate, IFeedbackMessageService feedback,
            @Qualifier("ecoreView") View view, IDiagramIdProvider ids) {
        this.delegate = delegate;
        this.feedback = feedback;
        this.descriptionId = ids.getId((org.eclipse.sirius.components.view.diagram.DiagramDescription) view.getDescriptions().getFirst());
    }

    @Override
    public boolean canExecute(DiagramDescription description) {
        return descriptionId.equals(description.getId());
    }

    @Override
    public IStatus execute(IEditingContext editingContext, ReconnectionToolInterpreterData data, Edge edge,
            EdgeDescription edgeDescription, ReconnectEdgeKind kind, DiagramDescription description) {
        var status = delegate.execute(editingContext, data, edge, edgeDescription, kind, description);
        if (status instanceof Failure failure && !feedback.getFeedbackMessages().isEmpty()) {
            return new Failure(Stream.concat(failure.getMessages().stream(), feedback.getFeedbackMessages().stream())
                    .filter(message -> !message.body().isBlank()).distinct().toList());
        }
        return status;
    }
}
