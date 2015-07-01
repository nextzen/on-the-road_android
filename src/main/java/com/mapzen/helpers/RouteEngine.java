package com.mapzen.helpers;

import com.mapzen.valhalla.Instruction;
import com.mapzen.valhalla.Route;

import android.location.Location;

import java.util.ArrayList;

public class RouteEngine {
    public static final int DESTINATION_RADIUS = 20;

    public enum RouteState {
        START,
        PRE_INSTRUCTION,
        INSTRUCTION,
        COMPLETE,
        LOST
    }

    private Route route;
    private RouteState routeState;
    private RouteListener listener;
    private Location location;
    private Location snapLocation;
    private Instruction currentInstruction;
    private ArrayList<Instruction> instructions;

    public void onLocationChanged(final Location location) {
        if (routeState == RouteState.COMPLETE) {
            return;
        }

        this.location = location;
        snapLocation();

        if (routeState == RouteState.START) {
            listener.onApproachInstruction(0);
            routeState = RouteState.PRE_INSTRUCTION;
        }

        if (routeState == RouteState.COMPLETE) {
            listener.onUpdateDistance(0, 0);
        } else {
            listener.onUpdateDistance(route.getDistanceToNextInstruction(),
                    route.getRemainingDistanceToDestination());
        }

        if (routeState == RouteState.LOST) {
            return;
        }

        if (routeState == RouteState.PRE_INSTRUCTION
                && route.getDistanceToNextInstruction() < ZoomController.DEFAULT_TURN_RADIUS) {
            int nextIndex = instructions.indexOf(route.getNextInstruction());
            listener.onApproachInstruction(nextIndex);
            routeState = RouteState.INSTRUCTION;
        }

        Instruction nextInstruction = route.getNextInstruction();

        if (!currentInstruction.equals(nextInstruction)) {
            routeState = RouteState.PRE_INSTRUCTION;
            int nextIndex = instructions.indexOf(currentInstruction);
            listener.onInstructionComplete(nextIndex);
        }

        currentInstruction = route.getNextInstruction();
    }

    private void snapLocation() {
        snapLocation = route.snapToRoute(location);

        if (snapLocation != null) {
            listener.onSnapLocation(location, snapLocation);
        }

        if (youHaveArrived()) {
            routeState = RouteState.COMPLETE;
            listener.onRouteComplete();
        }

        if (routeState != RouteState.START) {
            if (route.isLost()) {
                routeState = RouteState.LOST;
                listener.onRecalculate(location);
            }
        }
    }

    private boolean youHaveArrived() {
        return snapLocation != null && snapLocation.distanceTo(
                getLocationForDestination()) < DESTINATION_RADIUS;
    }

    private Location getLocationForDestination() {
        final int destinationIndex = route.getRouteInstructions().size() - 1;
        final Instruction destinationInstruction =
                route.getRouteInstructions().get(destinationIndex);
        return destinationInstruction.getLocation();
    }

    public void setRoute(Route route) {
        this.route = route;
        routeState = RouteState.START;
        instructions = route.getRouteInstructions();
        currentInstruction = instructions.get(0);

    }

    public void setListener(RouteListener listener) {
        this.listener = listener;
    }

    public interface RouteListener {
        public void onRecalculate(Location location);
        public void onSnapLocation(Location originalLocation, Location snapLocation);
        public void onApproachInstruction(int index);
        public void onInstructionComplete(int index);
        public void onUpdateDistance(int distanceToNextInstruction, int distanceToDestination);
        public void onRouteComplete();
    }
}