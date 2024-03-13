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

import java.util.List;
import java.util.UUID;

import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.sirius.components.core.configuration.IStereotypeDescriptionRegistry;
import org.eclipse.sirius.components.core.configuration.IStereotypeDescriptionRegistryConfigurer;
import org.eclipse.sirius.components.core.configuration.StereotypeDescription;
import org.eclipse.sirius.components.ecore.starter.helper.StereotypeBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Configuration used to register new stereotype descriptions.
 *
 * @author adieumegard
 */
@Configuration
public class EcoreStereotypeDescriptionRegistryConfigurer implements IStereotypeDescriptionRegistryConfigurer {


    public static final UUID EMPTY_ECORE_ID = UUID.nameUUIDFromBytes("empty_ecore".getBytes());

    public static final String EMPTY_ECORE_LABEL = "Ecore - Empty";

    public static final UUID VEHICLE_ID = UUID.nameUUIDFromBytes("vehicle".getBytes());

    public static final String VEHICLE_LABEL = "Ecore - Vehicle";

    public static final String VEHICLE_MODEL_FILE = "vehicle.ecore";

    public static final UUID ECORE_M2_ID = UUID.nameUUIDFromBytes("ecore".getBytes());

    public static final String ECORE_M2_LABEL = "Ecore Metamodel";

    public static final String ECORE_M2_MODEL_FILE = "Ecore.ecore";

    private static final String TIMER_NAME = "ecore_siriusweb_stereotype_load";

    private final StereotypeBuilder stereotypeBuilder;

    public EcoreStereotypeDescriptionRegistryConfigurer(MeterRegistry meterRegistry) {
        this.stereotypeBuilder = new StereotypeBuilder(TIMER_NAME, meterRegistry);
    }

    @Override
    public void addStereotypeDescriptions(IStereotypeDescriptionRegistry registry) {
        registry.add(new StereotypeDescription(EMPTY_ECORE_ID, EMPTY_ECORE_LABEL, this::getEmptyEcoreContent));
        registry.add(new StereotypeDescription(VEHICLE_ID, VEHICLE_LABEL, this::getPackageAndClassesContent));
        registry.add(new StereotypeDescription(ECORE_M2_ID, ECORE_M2_LABEL, this::getEcoreM2Content));
    }

    private String getEmptyEcoreContent() {
        return this.stereotypeBuilder.getStereotypeBody(List.of(EcoreFactory.eINSTANCE.createEPackage()));
    }

    private String getPackageAndClassesContent() {
        return this.stereotypeBuilder.getStereotypeBody(new ClassPathResource(VEHICLE_MODEL_FILE));
    }

    private String getEcoreM2Content() {
        return this.stereotypeBuilder.getStereotypeBody(new ClassPathResource(ECORE_M2_MODEL_FILE));
    }
}
