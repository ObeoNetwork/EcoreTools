/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package fr.obeo.ecore.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.Edge;
import org.eclipse.sirius.components.diagrams.Label;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.components.diagrams.layoutdata.*;

/** Initial positions only: subsequent user moves and resizes are persisted normally. */
final class EcoreExampleLayout {
    private record Bounds(double x, double y, double width, double height) { }

    private static final Map<String, Bounds> POSITIONS = Map.of(
            "Set", new Bounds(60, 60, 190, 108),
            "Source", new Bounds(330, 250, 240, 280),
            "E", new Bounds(790, 250, 240, 280),
            "Interface", new Bounds(790, 60, 230, 56),
            "SetOfThing", new Bounds(60, 395, 190, 64),
            "subPackage", new Bounds(30, 650, 210, 64),
            "SomeDataType", new Bounds(330, 685, 240, 80),
            "constraints", new Bounds(660, 685, 195, 70),
            "Enumeration", new Bounds(880, 675, 170, 100));

    private final IIdentityService identity;

    EcoreExampleLayout(IIdentityService identity) { this.identity = identity; }

    Diagram apply(EPackage model, Diagram diagram) {
        var names = new HashMap<String, String>();
        model.getEClassifiers().forEach(type -> {
            names.put(this.identity.getId(type), type.getName());
            if (type instanceof EClass clazz) clazz.getEReferences().forEach(reference ->
                    names.put(this.identity.getId(reference), clazz.getName() + "/" + reference.getName()));
        });
        model.getESubpackages().forEach(pack -> names.put(this.identity.getId(pack), pack.getName()));
        model.getEClassifier("SomeDataType").getEAnnotations().getFirst().getDetails().forEach(entry ->
                names.put(this.identity.getId(entry), entry.getKey()));
        var nodes = new LinkedHashMap<String, NodeLayoutData>();
        for (var node : diagram.getNodes()) {
            var bounds = POSITIONS.get(names.get(node.getTargetObjectId()));
            this.place(node, bounds, nodes);
        }
        var edges = new LinkedHashMap<String, EdgeLayoutData>();
        var labels = new LinkedHashMap<String, LabelLayoutData>();
        var handles = new HashMap<String, List<HandleLayoutData>>();
        for (var edge : diagram.getEdges()) {
            String name = names.get(edge.getTargetObjectId());
            switch (name) {
                case "Source/optionalToE" -> this.route(edge, edges, handles, "right", 240, 86, "left", 0, 86);
                case "Source/requiredToE" -> this.route(edge, edges, handles, "right", 240, 126, "left", 0, 126);
                case "Source/derivedToE" -> this.route(edge, edges, handles, "right", 240, 166, "left", 0, 166);
                case "Source/containedToE" -> this.route(edge, edges, handles, "right", 240, 206, "left", 0, 206);
                case "Source/containedDerived" -> this.route(edge, edges, handles, "right", 240, 246, "left", 0, 246);
                case "Source/source" -> this.route(edge, edges, handles, "bottom", 32, 280, "bottom", 210, 280,
                        362, 565, 1000, 565);
                case "Source/mandatoryE" -> this.route(edge, edges, handles, "bottom", 12, 280, "bottom", 230, 280,
                        342, 605, 1020, 605);
                case "Set/elements" -> this.route(edge, edges, handles, "top", 100, 0, "top", 16, 0,
                        160, 20, 250, 20);
                case "Source" -> this.route(edge, edges, handles, "top", 140, 0, "bottom", 130, 108,
                        470, 235, 190, 235);
                case "SetOfThing" -> this.route(edge, edges, handles, "top", 30, 0, "bottom", 30, 108);
                case "E" -> this.route(edge, edges, handles, "top", 120, 0, "bottom", 120, 56);
                case "constraints" -> this.route(edge, edges, handles, "left", 0, 45, "right", 240, 45);
                default -> throw new IllegalStateException("Unexpected example edge: " + name);
            }
            switch (name) {
                case "Source/source" -> {
                    this.label(edge.getBeginLabel(), labels, 20, 43);
                    this.label(edge.getEndLabel(), labels, -150, 43);
                }
                case "Source/mandatoryE" -> {
                    this.label(edge.getBeginLabel(), labels, 20, 83);
                    this.label(edge.getEndLabel(), labels, -170, 83);
                }
                case "Source" -> this.label(edge.getCenterLabel(), labels, -30, -25);
                case "SetOfThing" -> this.label(edge.getCenterLabel(), labels, 20, -15);
                case "Set/elements" -> this.label(edge.getCenterLabel(), labels, 20, 0);
                default -> this.label(edge.getCenterLabel(), labels, -100, -25);
            }
        }
        handles.forEach((id, anchors) -> {
            var node = nodes.get(id);
            nodes.put(id, new NodeLayoutData(id, node.position(), node.size(), true, true, anchors, node.minComputedSize()));
        });
        return Diagram.newDiagram(diagram).layoutData(new DiagramLayoutData(nodes, edges, labels, false)).build();
    }

    private void place(Node node, Bounds bounds, Map<String, NodeLayoutData> nodes) {
        nodes.put(node.getId(), new NodeLayoutData(node.getId(), new Position(bounds.x(), bounds.y()),
                new Size(bounds.width(), bounds.height()), true, true, List.of(), new Size(0, 0)));
        for (int index = 0; index < node.getChildNodes().size(); index++) {
            this.place(node.getChildNodes().get(index), new Bounds(0, 34 + index * 28, bounds.width(), 28), nodes);
        }
        for (int index = 0; index < node.getBorderNodes().size(); index++) {
            boolean left = "SetOfThing".equals(node.getInsideLabel().getText());
            this.place(node.getBorderNodes().get(index), new Bounds(left ? -35 : bounds.width() - 16,
                    left ? 10 : 28 + index * 36, left ? 40 : 32, left ? 40 : 32), nodes);
        }
    }

    private void label(Label label, Map<String, LabelLayoutData> labels, double x, double y) {
        if (label != null) labels.put(label.id(), new LabelLayoutData(label.id(), new Position(x, y), new Size(0, 0), false, true));
    }

    private void route(Edge edge, Map<String, EdgeLayoutData> edges, Map<String, List<HandleLayoutData>> handles,
            String sourceSide, double sourceX, double sourceY, String targetSide, double targetX, double targetY,
            double... bendingPoints) {
        handles.computeIfAbsent(edge.getSourceId(), ignored -> new ArrayList<>()).add(
                new HandleLayoutData(edge.getId(), new Position(sourceX, sourceY), sourceSide, HandleType.source));
        handles.computeIfAbsent(edge.getTargetId(), ignored -> new ArrayList<>()).add(
                new HandleLayoutData(edge.getId(), new Position(targetX, targetY), targetSide, HandleType.target));
        var points = new ArrayList<Position>();
        for (int index = 0; index < bendingPoints.length; index += 2) {
            points.add(new Position(bendingPoints[index], bendingPoints[index + 1]));
        }
        edges.put(edge.getId(), new EdgeLayoutData(edge.getId(), points, List.of(), List.of()));
    }
}
