/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package fr.obeo.ecore.editor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.emf.ecore.EPackage;
import org.eclipse.sirius.components.collaborative.api.IRepresentationEventProcessor;
import org.eclipse.sirius.components.collaborative.api.IRepresentationEventProcessorFactory;
import org.eclipse.sirius.components.collaborative.api.IRepresentationRefreshPolicyRegistry;
import org.eclipse.sirius.components.collaborative.api.IRepresentationSearchService;
import org.eclipse.sirius.components.collaborative.api.ISubscriptionManagerFactory;
import org.eclipse.sirius.components.collaborative.forms.FormEventProcessor;
import org.eclipse.sirius.components.collaborative.forms.api.FormCreationParameters;
import org.eclipse.sirius.components.collaborative.forms.configuration.FormEventProcessorConfiguration;
import org.eclipse.sirius.components.collaborative.forms.configuration.FormEventProcessorFactoryConfiguration;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IRepresentationDescriptionSearchService;
import org.eclipse.sirius.components.core.api.IURLParser;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.forms.renderer.IWidgetDescriptor;
import org.eclipse.sirius.web.domain.boundedcontexts.representationdata.RepresentationMetadata;

/** Creates an ephemeral form for the selected object's documentation. */
public class EcoreDocumentationEventProcessorFactory implements IRepresentationEventProcessorFactory {
    public static final String PREFIX = "documentation://";
    private final IRepresentationDescriptionSearchService representationDescriptionSearchService;
    private final IRepresentationSearchService representationSearchService;
    private final IRepresentationRefreshPolicyRegistry representationRefreshPolicyRegistry;
    private final ISubscriptionManagerFactory subscriptionManagerFactory;
    private final FormEventProcessorFactoryConfiguration formConfiguration;
    private final List<IWidgetDescriptor> widgetDescriptors;
    private final IURLParser urlParser;
    private final EcoreDocumentationPropertiesConfigurer description;

    public EcoreDocumentationEventProcessorFactory(IRepresentationDescriptionSearchService representationDescriptionSearchService,
            IRepresentationSearchService representationSearchService, IRepresentationRefreshPolicyRegistry representationRefreshPolicyRegistry,
            ISubscriptionManagerFactory subscriptionManagerFactory,
            FormEventProcessorFactoryConfiguration formConfiguration, List<IWidgetDescriptor> widgetDescriptors,
            IURLParser urlParser, EcoreDocumentationPropertiesConfigurer description) {
        this.representationDescriptionSearchService = Objects.requireNonNull(representationDescriptionSearchService);
        this.representationSearchService = Objects.requireNonNull(representationSearchService);
        this.representationRefreshPolicyRegistry = Objects.requireNonNull(representationRefreshPolicyRegistry);
        this.subscriptionManagerFactory = Objects.requireNonNull(subscriptionManagerFactory);
        this.formConfiguration = Objects.requireNonNull(formConfiguration);
        this.widgetDescriptors = Objects.requireNonNull(widgetDescriptors);
        this.urlParser = Objects.requireNonNull(urlParser);
        this.description = Objects.requireNonNull(description);
    }

    @Override
    public boolean canHandle(IEditingContext editingContext, String representationId) {
        return representationId.startsWith(PREFIX);
    }

    @Override
    public Optional<IRepresentationEventProcessor> createRepresentationEventProcessor(IEditingContext editingContext, String representationId) {
        var parameters = this.urlParser.getParameterValues(representationId);
        var values = parameters.getOrDefault("objectIds", List.of());
        if (values.isEmpty()) return Optional.empty();
        var objects = this.urlParser.getParameterEntries(values.getFirst()).stream()
                .map(id -> this.formConfiguration.getObjectSearchService().getObject(editingContext, id))
                .flatMap(Optional::stream)
                .map(object -> this.documentationTarget(editingContext, object)).toList();
        if (objects.isEmpty()) return Optional.empty();
        var creation = FormCreationParameters.newFormCreationParameters(representationId)
                .formDescription(this.description.formDescription(representationId)).object(objects.getFirst()).selection(objects).build();
        return Optional.of(new FormEventProcessor(
                new FormEventProcessorConfiguration(editingContext, this.formConfiguration.getObjectSearchService(), creation,
                        this.widgetDescriptors, this.formConfiguration.getFormEventHandlers(), this.formConfiguration.getTableEventHandlers()),
                this.subscriptionManagerFactory.create(), this.representationSearchService,
                this.representationDescriptionSearchService, this.representationRefreshPolicyRegistry,
                this.formConfiguration.getFormPostProcessor(), this.formConfiguration.getFormCapabilitiesService()));
    }

    private Object documentationTarget(IEditingContext editingContext, Object object) {
        if (object instanceof RepresentationMetadata metadata && Diagram.KIND.equals(metadata.getKind())) {
            return this.formConfiguration.getObjectSearchService().getObject(editingContext, metadata.getTargetObjectId())
                    .filter(EPackage.class::isInstance).orElse(object);
        }
        return object;
    }
}
