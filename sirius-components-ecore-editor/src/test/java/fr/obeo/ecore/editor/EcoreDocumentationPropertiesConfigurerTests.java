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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.emf.forms.api.IEMFFormDescriptionProvider;
import org.eclipse.sirius.components.forms.description.FormDescription;
import org.eclipse.sirius.components.forms.description.GroupDescription;
import org.eclipse.sirius.components.forms.description.IfDescription;
import org.eclipse.sirius.components.forms.description.PageDescription;
import org.eclipse.sirius.components.forms.description.RichTextDescription;
import org.eclipse.sirius.components.forms.description.TextareaDescription;
import org.eclipse.sirius.components.representations.Failure;
import org.eclipse.sirius.components.representations.Success;
import org.eclipse.sirius.components.representations.VariableManager;
import org.junit.jupiter.api.Test;

class EcoreDocumentationPropertiesConfigurerTests {
    @Test
    void documentationNoteEditsItsOwnEntryAndKeepsStandardProperties() {
        var defaultGroup = GroupDescription.newGroupDescription("default-group")
                .idProvider(vm -> "default-group").labelProvider(vm -> "Default")
                .semanticElementsProvider(vm -> List.of()).controlDescriptions(List.of()).build();
        var defaultPage = PageDescription.newPageDescription("default")
                .idProvider(vm -> "default").labelProvider(vm -> "Default")
                .semanticElementsProvider(vm -> List.of()).groupDescriptions(List.of(defaultGroup)).canCreatePredicate(vm -> true).build();
        var form = mock(FormDescription.class);
        when(form.getPageDescriptions()).thenReturn(List.of(defaultPage));
        var readOnly = new AtomicBoolean(false);
        var configurer = new EcoreDocumentationPropertiesConfigurer(mock(IIdentityService.class), object -> readOnly.get(), () -> form);
        List<PageDescription> pages = new ArrayList<>();
        configurer.addPropertiesDescriptions(pages::add);

        var element = EcoreFactory.eINSTANCE.createEClass();
        var annotation = EcoreFactory.eINSTANCE.createEAnnotation();
        annotation.setSource("http://www.eclipse.org/emf/2002/GenModel");
        element.getEAnnotations().add(annotation);
        annotation.getDetails().put("documentation", "**original**");
        annotation.getDetails().put("other", "preserved");
        var entry = annotation.getDetails().get(annotation.getDetails().indexOfKey("documentation"));
        var vm = new VariableManager();
        vm.put(VariableManager.SELF, entry);
        assertThat(pages).hasSize(1);
        var page = pages.getFirst();
        assertThat(page.getCanCreatePredicate().test(vm)).isTrue();
        assertThat(page.getGroupDescriptions()).hasSize(2);
        assertThat(page.getGroupDescriptions().get(1)).isSameAs(defaultGroup);
        var controls = page.getGroupDescriptions().getFirst().getControlDescriptions();
        var markdownIf = (IfDescription) controls.getFirst();
        var htmlIf = (IfDescription) controls.get(1);
        var markdown = (RichTextDescription) markdownIf.getControlDescriptions().getFirst();
        var html = (TextareaDescription) htmlIf.getControlDescriptions().getFirst();
        assertThat(markdown.getValueProvider().apply(vm)).isEqualTo("**original**");
        assertThat(markdown.getNewValueHandler().apply(vm, "<p>HTML</p>")).isInstanceOf(Success.class);
        assertThat(annotation.getDetails().get("documentation")).isEqualTo("<p>HTML</p>");
        assertThat(annotation.getDetails().get("other")).isEqualTo("preserved");
        assertThat(htmlIf.getPredicate().apply(vm)).isTrue();
        assertThat(html.getValueProvider().apply(vm)).isEqualTo("<p>HTML</p>");
        readOnly.set(true);
        assertThat(html.getIsReadOnlyProvider().apply(vm)).isTrue();
        assertThat(html.getNewValueHandler().apply(vm, "lost")).isInstanceOf(Failure.class);
        assertThat(annotation.getDetails().get("documentation")).isEqualTo("<p>HTML</p>");

        var constraint = EcoreFactory.eINSTANCE.createEAnnotation();
        constraint.setSource(EcorePackage.eNS_URI);
        element.getEAnnotations().add(constraint);
        constraint.getDetails().put("constraints", "C1");
        vm.put(VariableManager.SELF, constraint.getDetails().get(constraint.getDetails().indexOfKey("constraints")));
        assertThat(page.getCanCreatePredicate().test(vm)).isFalse();
        vm.put(VariableManager.SELF, annotation.getDetails().get(annotation.getDetails().indexOfKey("other")));
        assertThat(page.getCanCreatePredicate().test(vm)).isFalse();
    }

    @Test
    void documentationPreservesAnnotationsAndRejectsReadOnlyEdits() {
        var form = mock(FormDescription.class);
        var defaultPage = PageDescription.newPageDescription("default")
                .idProvider(vm -> "default").labelProvider(vm -> "Default")
                .semanticElementsProvider(vm -> List.of()).groupDescriptions(List.of()).canCreatePredicate(vm -> true).build();
        when(form.getPageDescriptions()).thenReturn(List.of(defaultPage));
        IEMFFormDescriptionProvider defaultForm = () -> form;
        var readOnly = new AtomicBoolean(false);
        var configurer = new EcoreDocumentationPropertiesConfigurer(mock(IIdentityService.class), object -> readOnly.get(), defaultForm);
        List<PageDescription> pages = new ArrayList<>();
        configurer.addPropertiesDescriptions(pages::add);
        var page = pages.getFirst();
        var element = EcoreFactory.eINSTANCE.createEClass();
        var vm = new VariableManager();
        vm.put(VariableManager.SELF, element);
        assertThat(page.getCanCreatePredicate().test(vm)).isTrue();
        var controls = page.getGroupDescriptions().getFirst().getControlDescriptions();
        var markdownIf = (IfDescription) controls.getFirst();
        var htmlIf = (IfDescription) controls.get(1);
        var markdown = (RichTextDescription) markdownIf.getControlDescriptions().getFirst();
        var html = (TextareaDescription) htmlIf.getControlDescriptions().getFirst();

        assertThat(markdown.getValueProvider().apply(vm)).isEmpty();
        assertThat(element.getEAnnotations()).isEmpty();
        assertThat(markdownIf.getPredicate().apply(vm)).isTrue();
        assertThat(markdown.getNewValueHandler().apply(vm, "**Example**")).isInstanceOf(Success.class);
        var annotation = element.getEAnnotation("http://www.eclipse.org/emf/2002/GenModel");
        annotation.getDetails().put("other", "keep");
        assertThat(html.getNewValueHandler().apply(vm, "<p>Original &amp; text</p>")).isInstanceOf(Success.class);
        assertThat(htmlIf.getPredicate().apply(vm)).isTrue();
        assertThat(markdownIf.getPredicate().apply(vm)).isFalse();
        assertThat(html.getValueProvider().apply(vm)).isEqualTo("<p>Original &amp; text</p>");
        assertThat(annotation.getDetails().get("other")).isEqualTo("keep");
        readOnly.set(true);
        assertThat(html.getIsReadOnlyProvider().apply(vm)).isTrue();
        assertThat(html.getNewValueHandler().apply(vm, "lost")).isInstanceOf(Failure.class);
        assertThat(annotation.getDetails().get("documentation")).isEqualTo("<p>Original &amp; text</p>");
    }
}
