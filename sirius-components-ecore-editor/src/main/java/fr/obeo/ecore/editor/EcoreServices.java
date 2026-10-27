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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import fr.obeo.ecore.editor.labels.*;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.core.api.IFeedbackMessageService;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.components.emf.services.EObjectIDManager;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.components.representations.Message;
import org.eclipse.sirius.components.representations.MessageLevel;
import org.springframework.beans.factory.annotation.Autowired;

/** AQL service entry points for Ecore diagram queries, labels, and model-editing tools. */
public class EcoreServices {
    private final IFeedbackMessageService feedbackMessageService;

    public EcoreServices() {
        this(new IFeedbackMessageService.NoOp());
    }

    @Autowired
    public EcoreServices(IFeedbackMessageService feedbackMessageService) {
        this.feedbackMessageService = Objects.requireNonNull(feedbackMessageService);
    }

    /** Creates the annotation/detail entry when missing and returns the editable detail entry. */
    public EObject ecoreNote(EModelElement self, boolean constraint) {
        String source = constraint ? EcorePackage.eNS_URI : "http://www.eclipse.org/emf/2002/GenModel";
        String key = constraint ? "constraints" : "documentation";
        EAnnotation annotation = self.getEAnnotation(source);
        if (annotation == null) {
            annotation = EcoreFactory.eINSTANCE.createEAnnotation();
            annotation.setSource(source);
            self.getEAnnotations().add(annotation);
        }
        if (!annotation.getDetails().containsKey(key)) annotation.getDetails().put(key, constraint ? "Constraint1 Constraint2" : "New documentation note");
        return (EObject) annotation.getDetails().get(annotation.getDetails().indexOfKey(key));
    }

    public EObject ecoreExistingNote(EModelElement self, boolean constraint) {
        String source = constraint ? EcorePackage.eNS_URI : "http://www.eclipse.org/emf/2002/GenModel";
        String key = constraint ? "constraints" : "documentation";
        var annotation = self.getEAnnotation(source);
        int index = annotation == null ? -1 : annotation.getDetails().indexOfKey(key);
        return index < 0 ? null : (EObject) annotation.getDetails().get(index);
    }

    public List<EObject> ecoreNotes(EObject self, DiagramContext context) {
        var visibleObjectIds = visibleSemanticObjectIds(context);
        var visibleReferenceIds = new HashSet<String>();
        if (context != null) {
            var deletedViewIds = context.viewDeletionRequests().stream().map(request -> request.getElementId()).collect(java.util.stream.Collectors.toSet());
            context.diagram().getEdges().stream().filter(edge -> !deletedViewIds.contains(edge.getId()))
                    .forEach(edge -> visibleReferenceIds.add(edge.getTargetObjectId()));
        }
        var objectIdManager = new EObjectIDManager();
        var result = new ArrayList<EObject>();
        if (self instanceof EModelElement model) appendAnnotationNotes(model, result);
        for (var element : ecoreImportCandidates(self)) {
            if (element instanceof EModelElement model && visibleObjectIds.contains(objectIdManager.findId(model).orElse(""))) appendAnnotationNotes(model, result);
            element.eAllContents().forEachRemaining(child -> {
                if (child instanceof EModelElement model) {
                    String id = objectIdManager.findId(model).orElse("");
                    if (visibleObjectIds.contains(id) || child instanceof EReference && visibleReferenceIds.contains(id)) appendAnnotationNotes(model, result);
                }
            });
        }
        return result.stream().distinct().toList();
    }

    /** Moves the existing detail entry to the target, rejecting conflicts before either note is changed. */
    public EObject ecoreAssignNote(EObject self, EModelElement target) {
        if (!(self.eContainer() instanceof EAnnotation source) || source.getDetails().indexOf(self) < 0) {
            throw reportToolError("Select a documentation or constraint note");
        }
        if (isReadOnly(self) || isReadOnly(target)) throw reportToolError("The annotation and its target must be editable");
        if (source.getEModelElement() == target) return self;
        var entry = source.getDetails().get(source.getDetails().indexOf(self));
        var destination = target.getEAnnotation(source.getSource());
        if (destination != null && destination.getDetails().containsKey(entry.getKey())) {
            throw reportToolError("The target already has " + entry.getKey() + "; both notes have been preserved");
        }
        if (destination == null) {
            destination = EcoreFactory.eINSTANCE.createEAnnotation();
            destination.setSource(source.getSource());
            target.getEAnnotations().add(destination);
        }
        destination.getDetails().add(entry);
        if (source.getDetails().isEmpty() && source.getContents().isEmpty()
                && source.getReferences().isEmpty() && source.getEAnnotations().isEmpty()) {
            EcoreUtil.remove(source);
        }
        return self;
    }

    private IllegalArgumentException reportToolError(String message) {
        feedbackMessageService.addFeedbackMessage(new Message(message, MessageLevel.ERROR));
        return new IllegalArgumentException(message);
    }

    private boolean isReadOnly(EObject object) {
        return object.eResource() != null && object.eResource().eAdapters().stream()
                .filter(ResourceMetadataAdapter.class::isInstance).map(ResourceMetadataAdapter.class::cast)
                .anyMatch(ResourceMetadataAdapter::isReadOnly);
    }

    private void appendAnnotationNotes(EModelElement element, List<EObject> notes) {
        for (var annotation : element.getEAnnotations()) {
            String key = EcorePackage.eNS_URI.equals(annotation.getSource()) ? "constraints"
                    : "http://www.eclipse.org/emf/2002/GenModel".equals(annotation.getSource()) ? "documentation" : null;
            if (key != null && annotation.getDetails().containsKey(key)) notes.add((EObject) annotation.getDetails().get(annotation.getDetails().indexOfKey(key)));
        }
    }

    public String ecoreReferenceRowLabel(EReference reference) {
        return new EReferenceServices().renderAsNode(reference);
    }

    public EObject ecoreEditReferenceRow(EReference reference, String label) {
        return new EAttributeServices().performEdit(reference, label);
    }

    public List<EReference> ecoreReferenceRows(EClass self, DiagramContext context) {
        var visibleObjectIds = visibleSemanticObjectIds(context);
        var objectIdManager = new EObjectIDManager();
        return self.getEAllReferences().stream()
                .filter(ref -> ref.getEContainingClass() == self
                        ? ecoreReferenceTarget(ref) == null || !visibleObjectIds.contains(objectIdManager.findId(ecoreReferenceTarget(ref)).orElse(""))
                        : !visibleObjectIds.contains(objectIdManager.findId(ref.getEContainingClass()).orElse(""))).toList();
    }

    /** Returns a depth-first, encounter-ordered copy, stopping each inheritance path at its first visible type. */
    public List<EClass> ecoreVisibleSuperTypes(EClass self, DiagramContext context) {
        var visibleObjectIds = visibleSemanticObjectIds(context);
        var result = new java.util.LinkedHashSet<EClass>();
        collectNearestVisibleSuperTypes(self, visibleObjectIds, new HashSet<>(), result);
        return List.copyOf(result);
    }

    public String ecoreSuperTypeLabel(EClass self, EClass superType) {
        return self.getEGenericSuperTypes().stream().filter(type -> type.getEClassifier() == superType)
                .filter(type -> !type.getETypeArguments().isEmpty())
                .map(type -> "<<bind>> " + type.getETypeArguments().stream().map(argument -> {
                    if (argument.getEClassifier() != null) return argument.getEClassifier().getName();
                    if (argument.getETypeParameter() != null) return argument.getETypeParameter().getName();
                    return "?";
                }).collect(java.util.stream.Collectors.joining(",")))
                .findFirst().orElse("");
    }

    private void collectNearestVisibleSuperTypes(EClass type, Set<String> visibleObjectIds, Set<EClass> visited, Set<EClass> result) {
        if (!visited.add(type)) {
            return;
        }
        var objectIdManager = new EObjectIDManager();
        for (var parent : type.getESuperTypes()) {
            if (visibleObjectIds.contains(objectIdManager.findId(parent).orElse(""))) {
                result.add(parent);
            } else {
                collectNearestVisibleSuperTypes(parent, visibleObjectIds, visited, result);
            }
        }
    }

    /** Includes pending creations and excludes deleted views with their descendants. */
    private Set<String> visibleSemanticObjectIds(DiagramContext context) {
        Set<String> result = new HashSet<>();
        if (context == null) return result;
        Set<String> deletedViewIds = new HashSet<>();
        context.viewDeletionRequests().forEach(request -> deletedViewIds.add(request.getElementId()));
        collectVisibleObjectIds(context.diagram().getNodes(), deletedViewIds, result);
        context.viewCreationRequests().forEach(request -> result.add(request.getTargetObjectId()));
        return result;
    }

    private void collectVisibleObjectIds(List<Node> nodes, Set<String> deletedViewIds, Set<String> result) {
        for (Node node : nodes) {
            if (!deletedViewIds.contains(node.getId())) {
                result.add(node.getTargetObjectId());
                collectVisibleObjectIds(node.getChildNodes(), deletedViewIds, result);
                collectVisibleObjectIds(node.getBorderNodes(), deletedViewIds, result);
            }
        }
    }

    public EReference ecoreCreateOpposite(EReference reference) {
        if (reference.getEOpposite() == null && ecoreReferenceTarget(reference) instanceof EClass target) {
            var opposite = ecoreReference(target, reference.getEContainingClass(), false);
            opposite.setName("opposite");
            reference.setEOpposite(opposite);
            opposite.setEOpposite(reference);
        }
        return reference;
    }

    public EObject ecoreReferenceTarget(EReference reference) {
        return EGenericsServices.getETypeOrParameter(reference);
    }
    public String ecoreIcon(EObject self) {
        String name = self.eClass().getName();
        if (self instanceof EClass type) {
            name += type.isInterface() ? "_interface" : type.isAbstract() ? "_abstract" : "";
        }
        if (self instanceof java.util.Map.Entry<?, ?>) name = "EAnnotation";
        return "/icons/ecore/" + name + ".svg";
    }

    public String ecoreLabel(EObject self) {
        if (self instanceof EAttribute attribute) return new EAttributeServices().render(attribute);
        if (self instanceof EReference reference) return new EReferenceServices().render(reference);
        if (self instanceof EOperation operation) return new EOperationServices().render(operation);
        if (self instanceof ENamedElement named) return Objects.toString(named.getName(), "");
        return "";
    }

    public EObject ecoreEdit(EObject self, String label) {
        if (self instanceof EReference reference) return new EReferenceServices().performEdit(reference, label);
        if (self instanceof EAttribute attribute) return new EAttributeServices().performEdit(attribute, label);
        if (self instanceof EOperation operation) return new EOperationServices().performEdit(operation, label);
        if (self instanceof ENamedElement named) {
            named.setName(self instanceof EClassifier || self instanceof ETypeParameter ? this.classifierNameFromLabel(label) : label);
        }
        return self;
    }

    private String classifierNameFromLabel(String label) {
        StringBuilder classifierName = new StringBuilder();
        for (String word : label.split("\\s+")) {
            if (!word.isEmpty()) {
                // Preserve the tool's ASCII-only capitalization, independent of the server locale.
                char first = word.charAt(0);
                classifierName.append(first >= 'a' && first <= 'z' ? (char) (first - 32) : first).append(word.substring(1));
            }
        }
        return classifierName.toString();
    }

    public boolean ecoreHasError(EObject self) {
        return Diagnostician.INSTANCE.validate(self).getSeverity() >= Diagnostic.ERROR;
    }

    /** Collects packages and classifiers recursively in loaded-resource order, or from the detached root. */
    public List<EObject> ecoreImportCandidates(EObject self) {
        List<EObject> result = new ArrayList<>();
        if (self.eResource() != null && self.eResource().getResourceSet() != null) {
            self.eResource().getResourceSet().getResources().forEach(r -> r.getContents().forEach(root -> collectImportableElements(root, result)));
        } else collectImportableElements(EcoreUtil.getRootContainer(self), result);
        return result;
    }

    public List<EClassifier> ecoreRelatedElements(EObject self, DiagramContext context) {
        var candidates = ecoreImportCandidates(self);
        var related = referencedClassifiers(self);
        for (var candidate : candidates) {
            if (candidate instanceof EClassifier classifier && referencedClassifiers(classifier).contains(self)) related.add(classifier);
        }
        related.remove(self);
        var visibleObjectIds = visibleSemanticObjectIds(context);
        var objectIdManager = new EObjectIDManager();
        // shortcut: only loaded model classifiers are importable, add read-only library views before exposing registry types.
        return related.stream().filter(candidates::contains)
                .filter(type -> !visibleObjectIds.contains(objectIdManager.findId(type).orElseGet(() -> EcoreUtil.getURI(type).toString()))).toList();
    }

    private Set<EClassifier> referencedClassifiers(EObject self) {
        Set<EClassifier> related = new java.util.LinkedHashSet<>();
        if (self instanceof EPackage pack) {
            related.addAll(pack.getEClassifiers());
            return related;
        }
        if (self instanceof EClass type) related.addAll(type.getESuperTypes());
        var contents = new ArrayList<EObject>();
        contents.add(self);
        self.eAllContents().forEachRemaining(contents::add);
        for (var element : contents) {
            if (element instanceof ETypedElement typed && typed.getEType() != null) related.add(typed.getEType());
            if (element instanceof EGenericType generic && generic.getEClassifier() != null) related.add(generic.getEClassifier());
        }
        return related;
    }

    private void collectImportableElements(EObject root, List<EObject> result) {
        if (root instanceof EPackage || root instanceof EClassifier) result.add(root);
        root.eAllContents().forEachRemaining(o -> {
            if (o instanceof EPackage || o instanceof EClassifier) result.add(o);
        });
    }

    /** Returns one edge representative per opposite pair, preferring the containment reference. */
    public List<EReference> ecoreReferences(EObject self) {
        var references = ecoreImportCandidates(self).stream().filter(EClass.class::isInstance).map(EClass.class::cast)
                .flatMap(c -> c.getEReferences().stream()).toList();
        var result = new ArrayList<EReference>();
        for (var reference : references) {
            if (reference.getEOpposite() != null && reference.getEOpposite().isContainment()) continue;
            if (!result.contains(reference.getEOpposite())) result.add(reference);
        }
        return result;
    }

    public EObject ecoreCreate(EObject self, String kind) {
        EObject created = EcoreFactory.eINSTANCE.create((EClass) EcorePackage.eINSTANCE.getEClassifier(kind));
        if (created instanceof ENamedElement named) {
            named.setName(created instanceof ETypeParameter ? "T" : "New" + kind.substring(1));
        }
        if (self instanceof EPackage pack && created instanceof EClassifier classifier) {
            pack.getEClassifiers().add(classifier);
        } else if (self instanceof EPackage pack && created instanceof EPackage child) {
            child.setNsPrefix("newPackage");
            child.setNsURI(pack.getNsURI() + "/newPackage");
            pack.getESubpackages().add(child);
        } else if (self instanceof EClass type && created instanceof EStructuralFeature feature) {
            type.getEStructuralFeatures().add(feature);
            if (feature instanceof EAttribute) feature.setEType(EcorePackage.Literals.ESTRING);
            else feature.setEType(EcorePackage.Literals.EOBJECT);
        } else if (self instanceof EClass type && created instanceof EOperation operation) {
            type.getEOperations().add(operation);
        } else if (self instanceof EClassifier type && created instanceof ETypeParameter parameter) {
            type.getETypeParameters().add(parameter);
        } else if (self instanceof EEnum type && created instanceof EEnumLiteral literal) {
            literal.setValue(type.getELiterals().stream().mapToInt(EEnumLiteral::getValue).max().orElse(-1) + 1);
            type.getELiterals().add(literal);
        } else {
            throw new IllegalArgumentException("Cannot create " + kind + " in " + self.eClass().getName());
        }
        return created;
    }

    public EReference ecoreReference(EClass source, ENamedElement target, boolean containment) {
        if (!(target instanceof EClass) && !(target instanceof ETypeParameter)) {
            throw new IllegalArgumentException("A reference must target an EClass or an ETypeParameter");
        }
        EReference reference = EcoreFactory.eINSTANCE.createEReference();
        reference.setName(target.getName() == null ? "newReference" : target.getName().toLowerCase(Locale.ROOT));
        EGenericsServices.setETypeWithGenerics(reference, target);
        reference.setContainment(containment);
        if (containment) reference.setUpperBound(-1);
        source.getEStructuralFeatures().add(reference);
        return reference;
    }

    public EReference ecoreBidirectionalReference(EClass source, EObject target) {
        if (!(target instanceof EClass type)) throw reportToolError("A bidirectional reference must connect two EClasses");
        if (isReadOnly(source) || isReadOnly(type)) throw reportToolError("Both reference owners must be editable");
        return ecoreCreateOpposite(ecoreReference(source, type, false));
    }

    public List<EReference> ecoreReferencesToDelete(EReference reference) {
        var opposite = reference.getEOpposite();
        var references = opposite == null || opposite == reference ? List.of(reference) : List.of(opposite, reference);
        if (references.stream().anyMatch(this::isReadOnly)) throw reportToolError("Both references must be editable before deleting the pair");
        return references;
    }

    public EClass ecoreSuperType(EClass source, EObject target) {
        if (target instanceof EClass type && source != type && !source.isSuperTypeOf(type)
                && !source.getESuperTypes().contains(type)) {
            var generic = EcoreFactory.eINSTANCE.createEGenericType();
            retargetGenericType(generic, type);
            source.getEGenericSuperTypes().add(generic);
        }
        return source;
    }

    public EReference ecoreReconnectReference(EReference reference, EObject newEnd, boolean reconnectSource) {
        if (reconnectSource ? !(newEnd instanceof EClass) : !(newEnd instanceof EClass || newEnd instanceof ETypeParameter)) {
            throw reportToolError(reconnectSource ? "A reference source must be an EClass" : "A reference must target an EClass or an ETypeParameter");
        }
        var opposite = reference.getEOpposite();
        if (opposite != null && !(newEnd instanceof EClass)) {
            throw reportToolError("A reference with an opposite must target an EClass");
        }
        if (isReadOnly(reference) || opposite != null && isReadOnly(opposite)
                || (reconnectSource || opposite != null) && isReadOnly(newEnd)) {
            throw reportToolError("The references and their modified owners must be editable");
        }
        if (reconnectSource) {
            // Adding to the new owner's containment list moves the existing reference without changing its identity.
            ((EClass) newEnd).getEStructuralFeatures().add(reference);
            if (opposite != null) retargetReference(opposite, newEnd);
        } else {
            retargetReference(reference, newEnd);
            if (opposite != null) ((EClass) newEnd).getEStructuralFeatures().add(opposite);
        }
        return reference;
    }

    private void retargetReference(EReference reference, EObject target) {
        if (reference.getEGenericType() != null && target instanceof EClass type) retargetGenericType(reference.getEGenericType(), type);
        else EGenericsServices.setETypeWithGenerics(reference, target);
    }

    public EClass ecoreReconnectSuperType(EClass source, EObject oldTarget, EObject newEnd, boolean reconnectSource) {
        if (!(newEnd instanceof EClass type) || !(oldTarget instanceof EClass parent)) {
            throw reportToolError("An inheritance link must connect two EClasses");
        }
        var binding = source.getEGenericSuperTypes().stream().filter(generic -> generic.getEClassifier() == parent).findFirst()
                .orElseThrow(() -> reportToolError("Import the direct supertype before reconnecting an indirect inheritance link"));
        var newSource = reconnectSource ? type : source;
        var newTarget = reconnectSource ? parent : type;
        if (newSource == source && newTarget == parent) return source;
        if (isReadOnly(source) || reconnectSource && isReadOnly(newSource)) {
            throw reportToolError("The classes whose supertypes change must be editable");
        }
        if (newSource == newTarget || newSource.isSuperTypeOf(newTarget)) {
            throw reportToolError("Reconnecting this inheritance link would create a cycle");
        }
        if (newSource.getESuperTypes().contains(newTarget)) {
            throw reportToolError("The class already has this direct supertype");
        }
        if (reconnectSource) newSource.getEGenericSuperTypes().add(binding);
        else retargetGenericType(binding, newTarget);
        return newSource;
    }

    private void retargetGenericType(EGenericType genericType, EClass targetClass) {
        genericType.setETypeParameter(null);
        genericType.setEClassifier(targetClass);
        // Preserve arguments by position; only discard excess bindings or add unbound placeholders.
        int typeParameterCount = targetClass.getETypeParameters().size();
        while (genericType.getETypeArguments().size() > typeParameterCount) {
            genericType.getETypeArguments().removeLast();
        }
        while (genericType.getETypeArguments().size() < typeParameterCount) {
            genericType.getETypeArguments().add(EcoreFactory.eINSTANCE.createEGenericType());
        }
    }

    public EClass ecoreRemoveSuperType(EClass source, EClass target) {
        source.getESuperTypes().remove(target); return source;
    }

    public EPackage ecoreExample() {
        var factory = EcoreFactory.eINSTANCE;
        var pack = factory.createEPackage(); pack.setName("library"); pack.setNsPrefix("library"); pack.setNsURI("https://example.org/library");
        var book = (EClass) ecoreCreate(pack, "EClass"); book.setName("Book");
        var title = (EAttribute) ecoreCreate(book, "EAttribute"); title.setName("title");
        var library = (EClass) ecoreCreate(pack, "EClass"); library.setName("Library");
        var books = ecoreReference(library, book, true); books.setName("books"); books.setUpperBound(-1);
        return pack;
    }
}
