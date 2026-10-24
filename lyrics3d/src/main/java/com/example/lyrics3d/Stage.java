package com.example.lyrics3d;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Where the lyrics float. Follows the player until pinned in place. */
public class Stage {
    public volatile boolean pinned = false;
    public volatile Vec3 pos = Vec3.ZERO;
    private boolean initialised = false;

    public void tick(Minecraft mc) {
        if (mc.player == null) { initialised = false; return; }
        if (pinned) return;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 target = eye.add(flat.scale(5.0)).add(0, 0.6, 0);
        pos = initialised ? pos.lerp(target, 0.08) : target;
        initialised = true;
    }

    public void togglePin() { pinned = !pinned; }
}
