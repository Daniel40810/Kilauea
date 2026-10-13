package com.dan.kilauea.render;

import com.dan.kilauea.camera.OrbitCamera;
import com.dan.kilauea.terrain.Ground;

/** Kleiner Helfer: Drehpunkt versetzen, ohne die Blickrichtung zu ändern. */
final class OrbitCameraHelper {
    private OrbitCameraHelper() { }

    static void go(OrbitCamera c, double x, double z) {
        c.autoOrbit = false;
        double d = Math.min(c.dist, 18_000);
        c.lookAt(x, Math.max(0, Ground.height(x, z)), z, Math.toDegrees(c.yaw), Math.max(20, Math.toDegrees(c.pitch)), d);
    }
}
