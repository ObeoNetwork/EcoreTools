/*******************************************************************************
 * Copyright (c) 2010 - 2024 THALES GLOBAL SERVICES.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.components.ecore.starter.services.ecoretools;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;

import java.util.Collections;
import java.util.Iterator;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

/**
 * Adapter class to treat an EObject as an Iterable on all its contents.
 * <p>
 * Usage:
 *
 * <pre>
 * for (EObject obj : AllContents.of(root)) {
 *     // code
 * }
 * </pre>
 *
 * Content adapted from org.eclipse.sirius.ext.emf.AllContents
 *
 * @author pcdavid
 * @author adieumegard - Minor adaptations
 */
public final class AllContents implements Iterable<EObject> {
    private final EObject root;

    private final EClass klass;

    private final boolean includeRoot;

    /**
     * Constructor.
     *
     * @param root
     *            the root element.
     * @param klass
     *            the optional type of elements to find.
     * @param includeRoot
     *            whether or not the root element itself should be included in
     *            the iterator.
     *
     */
    private AllContents(EObject root, EClass klass, boolean includeRoot) {
        this.root = root;
        this.klass = klass;
        this.includeRoot = includeRoot;
    }

    /**
     * Factory method.
     *
     * @param obj
     *            the root element.
     * @return an iterable on all the sub-elements of the root (recursively).
     */
    public static Iterable<EObject> of(EObject obj) {
        return AllContents.of(obj, false);
    }

    /**
     * Factory method.
     *
     * @param obj
     *            the root element.
     * @param includeRoot
     *            whether or not the root element itself should be included in
     *            the iterator.
     * @return an iterable on all the sub-elements of the root (recursively).
     */
    public static Iterable<EObject> of(EObject obj, boolean includeRoot) {
        return new AllContents(obj, null, includeRoot);
    }

    /**
     * Factory method.
     *
     * @param obj
     *            the root element.
     * @param klass
     *            the type of elements to consider.
     * @return an iterable on all the sub-elements of the root (recursively)
     *         which are of the specified type.
     */
    public static Iterable<EObject> of(EObject obj, EClass klass) {
        return AllContents.of(obj, klass, false);
    }

    /**
     * Factory method.
     *
     * @param obj
     *            the root element.
     * @param klass
     *            the type of elements to consider.
     * @param includeRoot
     *            whether or not the root element itself should be included in
     *            the iterator.
     * @return an iterable on all the sub-elements of
     *            the root (recursively) which are of the specified type.
     */
    public static Iterable<EObject> of(EObject obj, EClass klass, boolean includeRoot) {
        return new AllContents(obj, klass, includeRoot);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<EObject> iterator() {
        final Iterator<EObject> contentsIterator;
        if (this.root == null) {
            contentsIterator = Collections.emptyIterator();
        } else if (this.klass == null) {
            contentsIterator = this.root.eAllContents();
        } else {
            contentsIterator = Iterators.filter(this.root.eAllContents(), new Predicate<EObject>() {
                @Override
                public boolean apply(EObject input) {
                    return AllContents.this.klass.isInstance(input);
                }
            });
        }
        if (this.includeRoot) {
            return Iterators.concat(Iterators.singletonIterator(this.root), contentsIterator);
        } else {
            return contentsIterator;
        }
    }
}
