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
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.core.api.IReadOnlyObjectPredicate;
import org.eclipse.sirius.components.forms.description.FormDescription;
import org.eclipse.sirius.components.forms.description.GroupDescription;
import org.eclipse.sirius.components.forms.description.IfDescription;
import org.eclipse.sirius.components.forms.description.PageDescription;
import org.eclipse.sirius.components.forms.description.RichTextDescription;
import org.eclipse.sirius.components.forms.description.TextareaDescription;
import org.eclipse.sirius.components.representations.Failure;
import org.eclipse.sirius.components.representations.IStatus;
import org.eclipse.sirius.components.representations.Success;
import org.eclipse.sirius.components.representations.VariableManager;

/** Describes the form used by the separate documentation view. */
public class EcoreDocumentationPropertiesConfigurer {
    public static final String DESCRIPTION_ID = "ecore-documentation-form";
    private static final String SOURCE = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String KEY = "documentation";
    private static final Pattern HTML = Pattern.compile("<(/?[a-zA-Z][^>]*|![^>]*|\\?[^>]*)>|&(#\\d+|#x[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]+);");
    private final IIdentityService identityService;
    private final IReadOnlyObjectPredicate readOnlyObjectPredicate;
    public EcoreDocumentationPropertiesConfigurer(IIdentityService identityService, IReadOnlyObjectPredicate readOnlyObjectPredicate) {
        this.identityService = Objects.requireNonNull(identityService);
        this.readOnlyObjectPredicate = Objects.requireNonNull(readOnlyObjectPredicate);
    }

    public FormDescription formDescription() {
        var page = PageDescription.newPageDescription("ecore-documentation-page")
                .idProvider(vm -> "ecore-documentation-page")
                .labelProvider(vm -> "Documentation")
                .semanticElementsProvider(vm -> vm.get(VariableManager.SELF, Object.class).stream().toList())
                .groupDescriptions(List.of(this.documentationGroup()))
                .canCreatePredicate(this::isDocumentable).build();
        return FormDescription.newFormDescription(DESCRIPTION_ID).label("Documentation")
                .idProvider(vm -> "ecore-documentation")
                .labelProvider(vm -> "Documentation")
                .targetObjectIdProvider(vm -> vm.get(VariableManager.SELF, Object.class).map(this.identityService::getId).orElse(null))
                .canCreatePredicate(vm -> false).pageDescriptions(List.of(page)).iconURLsProvider(vm -> List.of()).build();
    }

    FormDescription formDescription(String representationId) {
        return FormDescription.newFormDescription(this.formDescription()).idProvider(vm -> representationId).build();
    }

    private GroupDescription documentationGroup() {
        Function<VariableManager, String> targetId = vm -> vm.get(VariableManager.SELF, Object.class).map(this.identityService::getId).orElse(null);
        Function<VariableManager, String> value = vm -> vm.get(VariableManager.SELF, EObject.class).map(this::documentation).orElse("");
        Function<VariableManager, Boolean> readOnly = vm -> vm.get(VariableManager.SELF, EObject.class).map(this.readOnlyObjectPredicate::test).orElse(true);
        BiFunction<VariableManager, String, IStatus> update = (vm, newValue) -> {
            var object = vm.get(VariableManager.SELF, EObject.class);
            if (object.isEmpty() || !this.isDocumentable(vm) || this.readOnlyObjectPredicate.test(object.get()) || newValue == null) return new Failure("Cannot edit documentation");
            if (this.isDocumentationEntry(object.get())) {
                object.get().eSet(EcorePackage.Literals.ESTRING_TO_STRING_MAP_ENTRY__VALUE, newValue);
                return new Success();
            }
            var element = (EModelElement) object.get();
            var annotation = element.getEAnnotation(SOURCE);
            if (annotation == null && !newValue.isEmpty()) {
                annotation = EcoreFactory.eINSTANCE.createEAnnotation();
                annotation.setSource(SOURCE);
                element.getEAnnotations().add(annotation);
            }
            if (annotation != null) annotation.getDetails().put(KEY, newValue);
            return new Success();
        };
        var richText = RichTextDescription.newRichTextDescription("ecore-documentation-rich-text")
                .idProvider(vm -> "ecore-documentation-rich-text-" + targetId.apply(vm)).targetObjectIdProvider(targetId)
                .labelProvider(vm -> "Documentation").valueProvider(value).newValueHandler(update)
                .isReadOnlyProvider(readOnly).diagnosticsProvider(vm -> List.of()).kindProvider(d -> "").messageProvider(d -> "").build();
        var htmlText = TextareaDescription.newTextareaDescription("ecore-documentation-html")
                .idProvider(vm -> "ecore-documentation-html-" + targetId.apply(vm)).targetObjectIdProvider(targetId)
                .labelProvider(vm -> "Documentation (HTML)").valueProvider(value).newValueHandler(update)
                .isReadOnlyProvider(readOnly).diagnosticsProvider(vm -> List.of()).kindProvider(d -> "").messageProvider(d -> "").build();
        var markdown = IfDescription.newIfDescription("ecore-documentation-markdown-if").targetObjectIdProvider(targetId)
                .predicate(vm -> !HTML.matcher(value.apply(vm)).find()).controlDescriptions(List.of(richText)).build();
        var html = IfDescription.newIfDescription("ecore-documentation-html-if").targetObjectIdProvider(targetId)
                .predicate(vm -> HTML.matcher(value.apply(vm)).find()).controlDescriptions(List.of(htmlText)).build();
        return GroupDescription.newGroupDescription("ecore-documentation-group")
                .idProvider(vm -> "ecore-documentation-group-" + targetId.apply(vm)).labelProvider(vm -> "Documentation")
                .semanticElementsProvider(vm -> vm.get(VariableManager.SELF, Object.class).stream().toList())
                .controlDescriptions(List.of(markdown, html)).build();
    }

    private boolean isDocumentable(VariableManager vm) {
        return vm.get(VariableManager.SELF, EObject.class).filter(object ->
                object instanceof EModelElement && object.eClass().getEPackage() == EcorePackage.eINSTANCE || this.isDocumentationEntry(object)).isPresent();
    }

    private boolean isDocumentationEntry(EObject object) {
        return object.eClass() == EcorePackage.Literals.ESTRING_TO_STRING_MAP_ENTRY
                && object instanceof Map.Entry<?, ?> entry && KEY.equals(entry.getKey())
                && object.eContainer() instanceof EAnnotation annotation && SOURCE.equals(annotation.getSource());
    }

    private String documentation(EObject object) {
        if (this.isDocumentationEntry(object)) return Objects.toString(((Map.Entry<?, ?>) object).getValue(), "");
        if (object instanceof EModelElement element) {
            var annotation = element.getEAnnotation(SOURCE);
            return annotation == null ? "" : Objects.toString(annotation.getDetails().get(KEY), "");
        }
        return "";
    }
}
