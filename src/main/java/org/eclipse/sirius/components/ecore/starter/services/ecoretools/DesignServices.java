/*******************************************************************************
 * Copyright (c) 2013, 2024 THALES GLOBAL SERVICES and Others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.components.ecore.starter.services.ecoretools;

import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.codegen.ecore.genmodel.GenClass;
import org.eclipse.emf.common.EMFPlugin;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.ETypeParameter;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.impl.EStringToStringMapEntryImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.business.api.session.SessionManager;
import org.eclipse.sirius.diagram.DDiagram;
import org.eclipse.sirius.diagram.DEdge;
import org.eclipse.sirius.diagram.DNodeList;
import org.eclipse.sirius.diagram.DSemanticDiagram;
import org.eclipse.sirius.diagram.EdgeArrows;
import org.eclipse.sirius.diagram.EdgeTarget;
import org.eclipse.sirius.diagram.business.api.query.DDiagramQuery;
import org.eclipse.sirius.diagram.model.business.internal.query.DDiagramInternalQuery;
import org.eclipse.sirius.viewpoint.DSemanticDecorator;
import org.eclipse.sirius.viewpoint.FontFormat;

/**
 * Generic Ecore services usable from a VSM.
 *
 * Content adapted from org.eclipse.emf.ecoretools.design.service.DesignServices
 *
 * @author adieumegard - Minor adaptations
 */
public class DesignServices extends EReferenceServices {

    protected static final String GEN_MODEL_PACKAGE_NS_URI = "http://www.eclipse.org/emf/2002/GenModel";

    protected static final String ECORE_PACKAGE_NS_URI = "http://www.eclipse.org/emf/2002/Ecore";

    private static final String CLASS_DIAGRAM_CLASS_MAPPINGID = "EC EClass";

    /**
     * Returns all the root objects of all the resources in the same resource-set as the specified object.
     *
     * @param any
     *            an EObject.
     * @return all the root objects in the same resource-set as <code>any</code> or an empty collection if
     *         <code>any</code> is not inside a resource-set.
     */
    public List<EObject> allRoots(EObject any) {
        Resource res = any.eResource();
        if (res != null && res.getResourceSet() != null) {
            List<EObject> roots = new ArrayList<>();
            for (Resource childRes : res.getResourceSet().getResources()) {
                roots.addAll(childRes.getContents());
            }
            return roots;
        } else {
            return Collections.emptyList();
        }
    }

    public Set<EPackage> rootEPackages(EObject any) {
        return Sets.newLinkedHashSet(Iterables.filter(this.allRoots(any), EPackage.class));
    }

    public Boolean isEOperation(EObject any) {
        return any instanceof EOperation;
    }

    public Boolean isEStructuralFeature(EObject any) {
        return any instanceof EStructuralFeature;
    }

    public Boolean isEPackage(EObject any) {
        return any instanceof EPackage;
    }

    public Boolean isEClass(EObject any) {
        return any instanceof EClass;
    }

    public Boolean isEEnum(EObject any) {
        return any instanceof EEnum;
    }

    public EObject eContainerEContainer(EObject any) {
        if (any.eContainer() != null) {
            return any.eContainer().eContainer();
        }
        return null;
    }

    public Set<EStringToStringMapEntryImpl> getVisibleDocAnnotations(EObject self, DSemanticDiagram diag) {
        // [diagram.getDisplayedEModelElements().oclAsType(ecore::EModelElement).eAnnotations.details->select(key
        // = 'documentation')/]
        Set<EStringToStringMapEntryImpl> result = Sets.newLinkedHashSet();
        for (EModelElement displayed : this.getDisplayedEModelElements(diag)) {
            if (!(displayed instanceof EAttribute) && !(displayed instanceof EEnumLiteral) && !(displayed instanceof EOperation)) {
                EAnnotation eAnnot = displayed.getEAnnotation(GEN_MODEL_PACKAGE_NS_URI);
                if (eAnnot != null) {
                    for (EStringToStringMapEntryImpl mapEntry : Iterables.filter(eAnnot.getDetails(), EStringToStringMapEntryImpl.class)) {
                        if ("documentation".equals(mapEntry.getKey())) {
                            result.add(mapEntry);
                        }
                    }
                }
            }

        }
        return result;
    }

    public Set<EStringToStringMapEntryImpl> getVisibleConstraintsAnnotations(EObject self, DSemanticDiagram diag) {
        Set<EStringToStringMapEntryImpl> result = Sets.newLinkedHashSet();
        for (EModelElement displayed : this.getDisplayedEModelElements(diag)) {
            if (!(displayed instanceof EAttribute) && !(displayed instanceof EEnumLiteral) && !(displayed instanceof EOperation)) {
                EAnnotation eAnnot = displayed.getEAnnotation(ECORE_PACKAGE_NS_URI);
                if (eAnnot != null) {
                    for (EStringToStringMapEntryImpl mapEntry : Iterables.filter(eAnnot.getDetails(), EStringToStringMapEntryImpl.class)) {
                        if ("constraints".equals(mapEntry.getKey())) {
                            result.add(mapEntry);
                        }
                    }
                }
            }

        }
        return result;
    }

    public boolean hasNoClassifier(DSemanticDiagram diagram) {
        Iterator<DSemanticDecorator> it = Iterators.filter(diagram.getOwnedDiagramElements().iterator(), DSemanticDecorator.class);
        while (it.hasNext()) {
            DSemanticDecorator dec = it.next();
            if (dec.getTarget() instanceof EClassifier) {
                return true;
            }
        }
        return false;
    }

    public Set<EReference> getNonDisplayedEReferences(EClass self, DSemanticDiagram diag) {
        Set<EReference> result = Sets.newLinkedHashSet();
        Set<EClass> displayedEClasses = null;
        for (EReference eReference : self.getEAllReferences()) {
            if (eReference.getEType() != null) {
                if (displayedEClasses == null) {
                    displayedEClasses = this.getDisplayedEClasses(diag);
                }
                /*
                 * if the target of the EReference is not visible, we *have* to display it as a node except if it iis
                 * from a supertype which is visible itself, then the reference will be already displayed. but if the
                 * reference is owned by a super type and this supertype is not visible, even if the target is visible,
                 * then we have to display it as a node to!
                 */
                boolean targetTypeIsVisible = displayedEClasses.contains(eReference.getEType());
                boolean referenceIsInherited = eReference.getEContainingClass() != self;
                boolean referenceHostIsVisible = displayedEClasses.contains(eReference.getEContainingClass());
                if (!referenceIsInherited && !targetTypeIsVisible) {
                    result.add(eReference);
                }
                if (referenceIsInherited && (!referenceHostIsVisible)) {
                    result.add(eReference);
                }
            }
        }
        return result;
    }

    public Set<EClass> getDisplayedEClasses(DSemanticDiagram diagram) {
        Set<EClass> result = Sets.newLinkedHashSet();
        Iterator<DNodeList> it = Iterators.filter(new DDiagramInternalQuery(diagram).getContainers().iterator(), DNodeList.class);
        while (it.hasNext()) {
            DNodeList dec = it.next();
            if (dec.getTarget() instanceof EClass && dec.isVisible()) {
                result.add((EClass) dec.getTarget());
            }
        }
        return result;
    }

    public Set<EClassifier> getDisplayedEClassifiers(DSemanticDiagram diagram) {
        Set<EClassifier> result = Sets.newLinkedHashSet();
        Iterator<DNodeList> it = Iterators.filter(new DDiagramInternalQuery(diagram).getContainers().iterator(), DNodeList.class);
        while (it.hasNext()) {
            DNodeList dec = it.next();
            if (dec.getTarget() instanceof EClassifier && dec.isVisible()) {
                result.add((EClassifier) dec.getTarget());
            }
        }
        return result;
    }

    private Set<EClass> getInternalEClasses(DSemanticDiagram diagram) {
        Set<EClass> result = Sets.newLinkedHashSet();
        Iterator<DNodeList> it = Iterators.filter(new DDiagramInternalQuery(diagram).getContainers().iterator(), DNodeList.class);
        while (it.hasNext()) {
            DNodeList dec = it.next();
            if (dec.getTarget() instanceof EClass) {
                if (dec.getActualMapping() != null && CLASS_DIAGRAM_CLASS_MAPPINGID.equals(dec.getActualMapping().getName())) {
                    result.add((EClass) dec.getTarget());
                }
            }
        }
        return result;
    }

    public Set<EClass> getDirectSuperTypesOrMostSpecificVisibleOnes(EClass self, DSemanticDiagram diagram) {
        Set<EClass> result = Sets.newLinkedHashSet();
        Set<EClass> displayed = this.getDisplayedEClasses(diagram);
        for (EClass directSuperType : self.getESuperTypes()) {
            if (displayed.contains(directSuperType)) {
                result.add(directSuperType);
            } else {
                Set<EClass> mostSpecificDisplayed = this.findMostSpecificAndVisible(directSuperType.getESuperTypes(), displayed);
                if (mostSpecificDisplayed != null) {
                    result.addAll(mostSpecificDisplayed);
                }
            }
        }
        return result;
    }

    public Set<EClass> findMostSpecificAndVisible(Collection<EClass> superTypes, Set<EClass> displayed) {
        Set<EClass> result = Sets.newLinkedHashSet();
        for (EClass eClass : superTypes) {
            if (displayed.contains(eClass)) {
                result.add(eClass);
            } else {
                result.addAll(this.findMostSpecificAndVisible(eClass.getESuperTypes(), displayed));
            }
        }
        return result;
    }

    public Set<EClass> getExternalEClasses(EPackage root, DSemanticDiagram diagram) {

        Set<EClass> related = Sets.newLinkedHashSet();
        Set<EClass> eClasses = this.getInternalEClasses(diagram);
        RelatedElementsSwitch relations = new RelatedElementsSwitch();
        for (EClass eClass : eClasses) {
            for (EClass other : Iterables.filter(relations.getRelatedElements(eClass), EClass.class)) {
                related.add(other);
            }
        }

        return Sets.difference(related, eClasses);
    }

    public Set<EReference> getEReferencesToDisplay(EPackage root, DSemanticDiagram diagram) {
        // [diagram.getDisplayedEClasses().oclAsType(ecore::EClass).eAllReferences->flatten()/]
        Collection<EClass> eClasses = this.getDisplayedEClasses(diagram);
        Set<EReference> eRefs = Sets.newLinkedHashSet();
        for (EClass clazz : eClasses) {
            eRefs.addAll(clazz.getEAllReferences());
        }
        return eRefs;
    }

    public Set<EReference> getEReferences(EPackage root) {
        Collection<EClass> eClasses = root.getEClassifiers().stream().filter(EClass.class::isInstance).map(EClass.class::cast).toList();
        Set<EReference> eRefs = Sets.newLinkedHashSet();
        for (EClass clazz : eClasses) {
            eRefs.addAll(clazz.getEAllReferences());
        }
        return eRefs;
    }

    public Boolean targetIsInterface(EClass clazz, EObject view) {
        if (view instanceof DEdge) {
            EdgeTarget target = ((DEdge) view).getTargetNode();
            if (target instanceof DSemanticDecorator && ((DSemanticDecorator) target).getTarget() instanceof EClass) {
                return ((EClass) ((DSemanticDecorator) target).getTarget()).isInterface();
            }
        }
        return false;
    }

    public List<EReference> getEOppositeSemanticElements(EReference ref) {
        Set<EReference> allRefs = Sets.newLinkedHashSet();
        allRefs.add(ref);
        if (ref.getEOpposite() != null) {
            allRefs.add(ref.getEOpposite());
        }
        return Ordering.natural().onResultOf(new Function<EReference, String>() {

            @Override
            public String apply(EReference input) {
                return input.getName();
            }
        }).sortedCopy(allRefs);
    }

    public Set<EModelElement> getDisplayedEModelElements(DSemanticDiagram diagram) {
        Set<EModelElement> modelelements = Sets.newLinkedHashSet();
        Iterator<DSemanticDecorator> it = Iterators.filter(Iterators.concat(Iterators.singletonIterator(diagram), new DDiagramQuery(diagram).getAllDiagramElements().iterator()),
                DSemanticDecorator.class);
        while (it.hasNext()) {
            DSemanticDecorator dec = it.next();
            if (dec.getTarget() instanceof EModelElement) {
                modelelements.add((EModelElement) dec.getTarget());
            }
        }
        return modelelements;
    }

    public List<EObject> getValidsForDiagram(final EObject element, final DSemanticDecorator containerView) {
        Predicate<EObject> validForClassDiagram = new Predicate<>() {

            @Override
            public boolean apply(EObject input) {
                return input instanceof EPackage || input instanceof EClassifier;
            }
        };
        return this.allValidSessionElements(element, validForClassDiagram);
    }

    public Set<EObject> getRelated(EObject firstView, List<EObject> allSelectedViews, DDiagram diag) {
        Set<EObject> relateds = Sets.newLinkedHashSet();
        for (DSemanticDecorator decorator : Iterables.filter(allSelectedViews, DSemanticDecorator.class)) {
            relateds.addAll(new RelatedElementsSwitch().getRelatedElements(decorator.getTarget()));
        }
        return relateds;
    }

    public Set<EObject> getRelated(EObject firstView, EObject aView, DDiagram diag) {
        return this.getRelated(firstView, Lists.newArrayList(aView), diag);
    }

    private List<EObject> allValidSessionElements(EObject cur, Predicate<EObject> validForClassDiagram) {
        Session found = SessionManager.INSTANCE.getSession(cur);
        List<EObject> result = Lists.newArrayList();
        if (found != null) {
            for (Resource res : found.getSemanticResources()) {
                if (res.getURI().isPlatformResource() || res.getURI().isPlatformPlugin()) {
                    Iterators.addAll(result, Iterators.filter(res.getAllContents(), validForClassDiagram));
                }
            }
        }
        return result;
    }

    /**
     * Gets the containing resource name, or null.
     *
     * @param current
     *            is the object
     * @return the resource
     */
    public String eResourceName(final EObject current) {
        if (current != null && current.eResource() != null) {
            return current.eResource().getURI().lastSegment();
        } else {
            return null;
        }
    }

    /**
     * Replace spaces by camel case value.
     *
     * @param any
     * @param from
     * @return
     */
    public String toCamelCase(EObject any, String from) {
        if (from != null) {
            StringBuilder buffer = new StringBuilder(from.length());
            for (String word : Splitter.on(CharMatcher.whitespace()).trimResults().split(from)) {
                buffer.append(this.toU1Case(word));
            }
            return buffer.toString();
        }
        return from;
    }

    private String toU1Case(String word) {
        if (word != null && word.length() > 0) {
            return new StringBuilder(word.length()).append(Ascii.toUpperCase(word.charAt(0))).append(word.substring(1)).toString();
        }
        return word;
    }

    /**
     * Computes the label of an EAttribute.
     */
    public String render(EAttribute attr) {
        return new EAttributeServices().render(attr);
    }

    /**
     * Performs a "direct edit" operation on an EAttribute.
     */
    public EStructuralFeature performEdit(EAttribute attr, String editString) {
        return new EAttributeServices().performEdit(attr, editString);
    }

    /**
     * Performs a "direct edit" operation on an EAttribute.
     */
    public EStructuralFeature performEditAsAttribute(EStructuralFeature attr, String editString) {
        return new EAttributeServices().performEdit(attr, editString);
    }

    /**
     * Computes the label of an EOperation.
     */
    public String render(EOperation op) {
        return new EOperationServices().render(op);
    }

    public String renderTooltip(EObject current) {
        String result = "";
        Optional<Diagnostic> diag = DiagnosticAttachment.get(current);
        if (diag.isPresent()) {
            result += this.prettyMessage(diag.get());
        }
        return result;
    }

    private String prettyMessage(Diagnostic diag) {
        String result = "";
        for (Diagnostic child : diag.getChildren()) {
            String message = child.getMessage();
            /*
             * we remove any substring which could be the toString of some data and replace it with something which will
             * not change on subsequent executions.
             */
            for (EObject data : Iterables.filter(child.getData(), EObject.class)) {
                String instanceVariableString = data.getClass().getName() + "@" + Integer.toHexString(data.hashCode());
                message = message.replace(instanceVariableString, data.eClass().getName());
            }

            result += "\n" + this.severityLabel(child.getSeverity()) + " : " + message;
            result += this.prettyMessage(child);
        }
        return result;
    }

    private String severityLabel(int severity) {
        String result;
        switch (severity) {
            case Diagnostic.ERROR:
                result = "ERROR";
                break;
            case Diagnostic.CANCEL:
                result = "CANCEL";
                break;
            case Diagnostic.INFO:
                result = "INFO";
                break;
            case Diagnostic.WARNING:
                result = "WARNING";
                break;
            case Diagnostic.OK:
                result = "OK";
                break;
            default:
                result = "UNKNOWN";
        }
        return result;
    }

    /**
     * Computes the tooltip of an EOperation.
     *
     * @param op
     *            the operation to get the tooltip from
     * @return the tooltip of the given EOperation.
     */
    public String renderEOperationTooltip(EOperation op) {
        String validationTooltip = this.renderTooltip(op);
        String operationSignature = new EOperationServices().renderEOperationTooltip(op);
        if (validationTooltip != null && validationTooltip.length() > 0) {
            return validationTooltip + "\n" + operationSignature;
        } else {
            return operationSignature;
        }
    }

    /**
     * Performs a "direct edit" operation on an EOperation.
     */
    public EOperation performEdit(EOperation op, String editString) {
        return new EOperationServices().performEdit(op, editString);
    }

    public List<ENamedElement> getAllAssociatedElements(EOperation op) {
        return new EOperationServices().getAllAssociatedElements(op);
    }

    /**
     * Computes the label of an EReference.
     */
    @Override
    public String render(EReference ref) {
        return new EReferenceServices().render(ref);
    }

    /**
     * Computes the label of an EReference.
     */
    @Override
    public String renderAsNode(EReference ref) {
        return new EReferenceServices().renderAsNode(ref);
    }

    public String renderEOpposite(EReference ref) {
        if (ref.getEOpposite() != null) {
            return new EReferenceServices().render(ref.getEOpposite());
        }
        return "";
    }

    /**
     * Performs a "direct edit" operation on an EReference.
     */
    @Override
    public EReference performEdit(EReference ref, String editString) {
        return new EReferenceServices().performEdit(ref, editString);
    }

    /**
     * Finds a type matching the specified name (case-insensitive) in the same resource-set as obj, or inside Ecore
     * itself if none could be found.
     *
     * @param obj
     *            the object defining the context in which to look.
     * @param name
     *            the name of the type to look for (case-insensitive). Only basic type names are supported (no qualified
     *            names). Whitespace before or after the name is ignored.
     * @return the first type found in the resource set or Ecore itself which matches the specified name.
     */
    public EClassifier findTypeByName(EObject obj, String name) {
        EClassifier result = this.findTypeByName(this.allRoots(obj), name);
        if (result == null) {
            result = this.findTypeByNameFrom(EcorePackage.eINSTANCE, name);
        }
        return result;
    }

    /**
     * Returns the root container; it may be this object itself.
     *
     * @param eObject
     *            the object to get the root container for.
     * @return the root container.
     */
    public EObject getRootContainer(EObject eObject) {
        return EcoreUtil.getRootContainer(eObject);
    }

    private EClassifier findTypeByName(Iterable<EObject> roots, String name) {
        for (EObject root : roots) {
            EClassifier result = this.findTypeByNameFrom(root, name);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private EClassifier findTypeByNameFrom(EObject root, String name) {
        EClassifier result = null;
        if (root instanceof EClassifier && this.nameMatches((EClassifier) root, name)) {
            result = (EClassifier) root;
        }
        if (result == null) {
            for (EObject obj : AllContents.of(root)) {
                if (obj instanceof EClassifier && this.nameMatches((EClassifier) obj, name)) {
                    result = (EClassifier) obj;
                }
            }
        }
        return result;
    }

    private boolean nameMatches(EClassifier type, String name) {
        if (type != null && type.getName() != null && name != null) {
            return type.getName().trim().equalsIgnoreCase(name.trim());
        } else {
            return false;
        }
    }

    public Boolean hasError(EObject eObj) {
        if (eObj instanceof EClass || eObj instanceof EStructuralFeature) {
            DiagnosticAttachment attachment = DiagnosticAttachment.getAttachment(eObj);
            if (attachment == null) {
                Diagnostic diagnostic = Diagnostician.INSTANCE.validate(eObj);
                attachment = DiagnosticAttachment.getOrCreate(eObj, diagnostic);
            }
            Diagnostic diag = attachment.getDiagnostic();
            if (diag != null) {
                return diag.getSeverity() == Diagnostic.ERROR;
            }
        }
        return false;
    }

    public Boolean isEDataType(EObject eObj) {
        return eObj.eClass() == EcorePackage.eINSTANCE.getEDataType();
    }

    public Boolean isDDiagram(EObject any, EObject view) {
        return view instanceof DDiagram;
    }

    public List<EObject> eOperationSemanticElements(EOperation eOp) {
        List result = Lists.newArrayList(Ordering.natural().onResultOf(new Function<EParameter, String>() {

            @Override
            public String apply(EParameter arg0) {
                return arg0.getName();
            }
        }).sortedCopy(eOp.getEParameters()));
        result.add(0, eOp);
        return result;
    }

    public Boolean viewContainerNotSemanticContainer(EObject self, DSemanticDiagram diag, DSemanticDecorator containerView) {
        return containerView.getTarget() != self.eContainer();
    }

    public Boolean noEOpposite(EReference ref) {
        return ref.getEOpposite() == null;
    }

    public boolean hasNoDocAnnotation(EObject eObj) {
        // Error[eAnnotations.details->select(key = 'documentation')->size() =
        // 0/]
        if (eObj instanceof EModelElement) {
            return EcoreUtil.getDocumentation((EModelElement) eObj) == null;
        }
        return true;
    }

    public EdgeArrows arrowsFillDiamond(EObject any) {
        return EdgeArrows.FILL_DIAMOND_LITERAL;
    }

    public FontFormat fontFormatBold(EObject any) {
        return FontFormat.BOLD_LITERAL;
    }

    public void reconnectESuperTypeSource(EObject element, EObject target, EObject source, EObject otherEnd, EObject edgeView, EObject sourceView) {
        if (edgeView instanceof DEdge && ((DEdge) edgeView).getSourceNode() instanceof DSemanticDecorator && ((DEdge) edgeView).getTargetNode() instanceof DSemanticDecorator) {
            EObject newSource = ((DSemanticDecorator) ((DEdge) edgeView).getSourceNode()).getTarget();
            EObject newTarget = ((DSemanticDecorator) ((DEdge) edgeView).getTargetNode()).getTarget();

            /*
             * reconnect source:
             */
            if (newSource instanceof EClass && newTarget instanceof EClass && source instanceof EClass) {
                ((EClass) source).getESuperTypes().remove(newTarget);
                ((EClass) newSource).getESuperTypes().add((EClass) newTarget);

            }
        }

    }

    public void reconnectESuperTypeTarget(EObject element, EObject target, EObject source, EObject otherEnd, EObject edgeView, EObject sourceView) {
        if (edgeView instanceof DEdge && ((DEdge) edgeView).getSourceNode() instanceof DSemanticDecorator && ((DEdge) edgeView).getTargetNode() instanceof DSemanticDecorator) {
            EObject newTarget = ((DSemanticDecorator) ((DEdge) edgeView).getTargetNode()).getTarget();

            /*
             * reconnect target:
             */
            if (newTarget instanceof EClass && target instanceof EClass && element instanceof EClass) {
                ((EClass) element).getESuperTypes().remove(newTarget);
                ((EClass) element).getESuperTypes().add((EClass) target);

            }
        }

    }

    public void reconnectEReferenceSource(EObject element, EObject newValue) {
        if (newValue instanceof EClass && element instanceof EReference) {

            EReference eRef = (EReference) element;
            EClass srcClass = (EClass) newValue;

            if (eRef.eContainer() != srcClass) {
                srcClass.getEStructuralFeatures().add(eRef);
            }

        }
    }

    public void reconnectEReferenceTarget(EObject element, EObject newValue) {
        if (newValue != null && element instanceof EReference) {

            EReference eRef = (EReference) element;

            if (newValue instanceof EClass) {
                EClass targetClass = (EClass) newValue;
                if (eRef.getEType() != newValue) {
                    eRef.setEType(targetClass);
                }
            } else if (newValue instanceof ETypeParameter) {
                if (eRef.getEType() != newValue) {
                    EGenericsServices.setETypeWithGenerics(eRef, newValue);
                }
            }

        }
    }

    public String getEClassItemIconPath(GenClass cur) throws IOException {
        String r = "/org.eclipse.emf.ecoretools.design/icons/full/obj16/empty.gif";
        if (cur != null && EMFPlugin.IS_RESOURCES_BUNDLE_AVAILABLE) {
            String icon = cur.getItemIconFileName();
            IFile f = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(icon));
            if (f.exists() && f.isAccessible()) {
                r = icon;
            }
        }
        return r;
    }

}
