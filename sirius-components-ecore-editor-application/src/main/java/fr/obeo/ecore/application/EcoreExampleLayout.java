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

    private final IIdentityService identityService;

    EcoreExampleLayout(IIdentityService identityService) { this.identityService = identityService; }

    /** Returns a diagram copy with initial layout data; the model and input diagram are not mutated. */
    Diagram apply(EPackage model, Diagram diagram) {
        var semanticNamesById = new HashMap<String, String>();
        model.getEClassifiers().forEach(type -> {
            semanticNamesById.put(this.identityService.getId(type), type.getName());
            if (type instanceof EClass eClass) eClass.getEReferences().forEach(reference ->
                    semanticNamesById.put(this.identityService.getId(reference), eClass.getName() + "/" + reference.getName()));
        });
        model.getESubpackages().forEach(pack -> semanticNamesById.put(this.identityService.getId(pack), pack.getName()));
        model.getEClassifier("SomeDataType").getEAnnotations().getFirst().getDetails().forEach(entry ->
                semanticNamesById.put(this.identityService.getId(entry), entry.getKey()));

        var nodeLayouts = new LinkedHashMap<String, NodeLayoutData>();
        for (var node : diagram.getNodes()) {
            var bounds = POSITIONS.get(semanticNamesById.get(node.getTargetObjectId()));
            this.placeNodeTree(node, bounds, nodeLayouts);
        }

        var edgeLayouts = new LinkedHashMap<String, EdgeLayoutData>();
        var labelLayouts = new LinkedHashMap<String, LabelLayoutData>();
        var handlesByNodeId = new HashMap<String, List<HandleLayoutData>>();
        for (var edge : diagram.getEdges()) {
            String name = semanticNamesById.get(edge.getTargetObjectId());
            switch (name) {
                case "Source/optionalToE" -> this.routeEdge(edge, edgeLayouts, handlesByNodeId, "right", 240, 86, "left", 0, 86);
                case "Source/requiredToE" -> this.routeEdge(edge, edgeLayouts, handlesByNodeId, "right", 240, 126, "left", 0, 126);
                case "Source/derivedToE" -> this.routeEdge(edge, edgeLayouts, handlesByNodeId, "right", 240, 166, "left", 0, 166);
                case "Source/containedToE" -> this.routeEdge(edge, edgeLayouts, handlesByNodeId, "right", 240, 206, "left", 0, 206);
                case "Source/containedDerived" -> this.routeEdge(edge, edgeLayouts, handlesByNodeId, "right", 240, 246, "left", 0, 246);
                case "Source/source" -> this.routeEdge(edge, edgeLayouts, handlesByNodeId, "bottom", 32, 280, "bottom", 210, 280,
                        362, 565, 1000, 565);
                case "Source/mandatoryE" -> this.routeEdge(edge, edgeLayouts, handlesByNodeId, "bottom", 12, 280, "bottom", 230, 280,
                        342, 605, 1020, 605);
                case "Set/elements" -> this.routeEdge(edge, edgeLayouts, handlesByNodeId, "top", 100, 0, "top", 16, 0,
                        160, 20, 250, 20);
                case "Source" -> this.routeEdge(edge, edgeLayouts, handlesByNodeId, "top", 140, 0, "bottom", 130, 108,
                        470, 235, 190, 235);
                case "SetOfThing" -> this.routeEdge(edge, edgeLayouts, handlesByNodeId, "top", 30, 0, "bottom", 30, 108);
                case "E" -> this.routeEdge(edge, edgeLayouts, handlesByNodeId, "top", 120, 0, "bottom", 120, 56);
                case "constraints" -> this.routeEdge(edge, edgeLayouts, handlesByNodeId, "left", 0, 45, "right", 240, 45);
                default -> throw new IllegalStateException("Unexpected example edge: " + name);
            }
            switch (name) {
                case "Source/source" -> {
                    this.positionLabel(edge.getBeginLabel(), labelLayouts, 20, 43);
                    this.positionLabel(edge.getEndLabel(), labelLayouts, -150, 43);
                }
                case "Source/mandatoryE" -> {
                    this.positionLabel(edge.getBeginLabel(), labelLayouts, 20, 83);
                    this.positionLabel(edge.getEndLabel(), labelLayouts, -170, 83);
                }
                case "Source" -> this.positionLabel(edge.getCenterLabel(), labelLayouts, -30, -25);
                case "SetOfThing" -> this.positionLabel(edge.getCenterLabel(), labelLayouts, 20, -15);
                case "Set/elements" -> this.positionLabel(edge.getCenterLabel(), labelLayouts, 20, 0);
                default -> this.positionLabel(edge.getCenterLabel(), labelLayouts, -100, -25);
            }
        }

        handlesByNodeId.forEach((id, connectionHandles) -> {
            var nodeLayout = nodeLayouts.get(id);
            nodeLayouts.put(id, new NodeLayoutData(id, nodeLayout.position(), nodeLayout.size(), true, true, connectionHandles, nodeLayout.minComputedSize()));
        });
        return Diagram.newDiagram(diagram).layoutData(new DiagramLayoutData(nodeLayouts, edgeLayouts, labelLayouts, false)).build();
    }

    private void placeNodeTree(Node node, Bounds bounds, Map<String, NodeLayoutData> nodeLayouts) {
        nodeLayouts.put(node.getId(), new NodeLayoutData(node.getId(), new Position(bounds.x(), bounds.y()),
                new Size(bounds.width(), bounds.height()), true, true, List.of(), new Size(0, 0)));
        for (int index = 0; index < node.getChildNodes().size(); index++) {
            this.placeNodeTree(node.getChildNodes().get(index), new Bounds(0, 34 + index * 28, bounds.width(), 28), nodeLayouts);
        }
        for (int index = 0; index < node.getBorderNodes().size(); index++) {
            boolean portOnLeft = "SetOfThing".equals(node.getInsideLabel().getText());
            this.placeNodeTree(node.getBorderNodes().get(index), new Bounds(portOnLeft ? -35 : bounds.width() - 16,
                    portOnLeft ? 10 : 28 + index * 36, portOnLeft ? 40 : 32, portOnLeft ? 40 : 32), nodeLayouts);
        }
    }

    private void positionLabel(Label label, Map<String, LabelLayoutData> labelLayouts, double x, double y) {
        if (label != null) labelLayouts.put(label.id(), new LabelLayoutData(label.id(), new Position(x, y), new Size(0, 0), false, true));
    }

    /** Registers endpoint handles and diagram-coordinate bend points supplied as consecutive x/y pairs. */
    private void routeEdge(Edge edge, Map<String, EdgeLayoutData> edgeLayouts, Map<String, List<HandleLayoutData>> handlesByNodeId,
            String sourceSide, double sourceX, double sourceY, String targetSide, double targetX, double targetY,
            double... bendPointCoordinates) {
        handlesByNodeId.computeIfAbsent(edge.getSourceId(), ignored -> new ArrayList<>()).add(
                new HandleLayoutData(edge.getId(), new Position(sourceX, sourceY), sourceSide, HandleType.source));
        handlesByNodeId.computeIfAbsent(edge.getTargetId(), ignored -> new ArrayList<>()).add(
                new HandleLayoutData(edge.getId(), new Position(targetX, targetY), targetSide, HandleType.target));
        var bendPoints = new ArrayList<Position>();
        for (int index = 0; index < bendPointCoordinates.length; index += 2) {
            bendPoints.add(new Position(bendPointCoordinates[index], bendPointCoordinates[index + 1]));
        }
        edgeLayouts.put(edge.getId(), new EdgeLayoutData(edge.getId(), bendPoints, List.of(), List.of()));
    }
}
