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
package org.eclipse.sirius.components.ecore.starter.view.descriptions;

import java.util.Objects;

import org.eclipse.sirius.components.ecore.starter.view.EcoreViewBuilder;
import org.eclipse.sirius.components.view.builder.generated.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.builder.providers.IEdgeDescriptionProvider;
import org.eclipse.sirius.components.view.diagram.ArrowStyle;
import org.eclipse.sirius.components.view.diagram.ConditionalEdgeStyle;
import org.eclipse.sirius.components.view.diagram.LineStyle;
import org.eclipse.sirius.components.view.diagram.SynchronizationPolicy;

/**
 * An abstraction for an {@linkplain EdgeDecriptionProvider} with utility methods.
 *
 * @author adieumegard
 */
public abstract class AbstractEReferenceDescriptionProvider implements IEdgeDescriptionProvider {

    protected final DiagramBuilders diagramBuilderHelper = new DiagramBuilders();

    protected final EcoreViewBuilder ecoreViewBuilder = new EcoreViewBuilder();

    protected final SynchronizationPolicy synchronizationPolicy;

    protected final IColorProvider colorProvider;

    public AbstractEReferenceDescriptionProvider(IColorProvider colorProvider, SynchronizationPolicy synchronizationPolicy) {
        this.colorProvider = Objects.requireNonNull(colorProvider);
        this.synchronizationPolicy = Objects.requireNonNull(synchronizationPolicy);
    }

    /**
     * Create a condition edge style.
     *
     * @return
     */
    protected ConditionalEdgeStyle conditionalEdgeStyle(String condition, ArrowStyle sourceStyle, ArrowStyle targetStyle, boolean bold) {
        return this.diagramBuilderHelper.newConditionalEdgeStyle()
                .condition(condition)
                .lineStyle(LineStyle.SOLID)
                .color(this.colorProvider.getColor("Ecore_Black"))
                .sourceArrowStyle(sourceStyle)
                .targetArrowStyle(targetStyle)
                .bold(bold)
                .build();
    }
}
