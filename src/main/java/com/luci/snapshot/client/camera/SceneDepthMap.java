package com.luci.snapshot.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class SceneDepthMap {
    private static final double MAX_DISTANCE = 256.0;
    private final int width;
    private final int height;
    private final double[] distances;

    private SceneDepthMap(int width, int height, double[] distances) {
        this.width = width;
        this.height = height;
        this.distances = distances;
    }

    static SceneDepthMap capture(Minecraft client, CameraSettings settings) {
        int width = settings.preset() == OpticsPreset.SCREENSHOT_ULTRA ? 64 : 48;
        int height = Math.max(18, (int) Math.round(width * client.getWindow().getHeight() / (double) client.getWindow().getWidth()));
        double[] depths = new double[width * height];
        Vec3 origin = client.player.getEyePosition();
        Vec3 forward = client.player.getLookAngle().normalize();
        Vec3 right = Vec3.Y_AXIS.cross(forward);
        if (right.lengthSqr() < 1.0E-6) {
            right = Vec3.directionFromRotation(0.0F, client.player.getYRot() + 90.0F);
        }
        right = right.normalize();
        Vec3 up = forward.cross(right).normalize();

        double roll = Math.toRadians(settings.rollDegrees());
        double cosine = Math.cos(roll);
        double sine = Math.sin(roll);
        Vec3 rolledRight = right.scale(cosine).add(up.scale(sine));
        Vec3 rolledUp = up.scale(cosine).subtract(right.scale(sine));

        double fullAspect = client.getWindow().getWidth() / (double) client.getWindow().getHeight();
        double outputAspect = settings.aspectRatio().nativeFrame() ? fullAspect : settings.aspectRatio().ratio();
        double horizontalCrop = Math.min(1.0, outputAspect / fullAspect);
        double verticalCrop = Math.min(1.0, fullAspect / outputAspect);
        double horizontalScale = Math.tan(Math.toRadians(settings.targetFov()) * 0.5) * horizontalCrop;
        double verticalScale = horizontalScale / outputAspect * verticalCrop / Math.max(0.001, horizontalCrop);

        for (int y = 0; y < height; y++) {
            double screenY = 1.0 - (y + 0.5) / height * 2.0;
            for (int x = 0; x < width; x++) {
                double screenX = (x + 0.5) / width * 2.0 - 1.0;
                Vec3 direction = forward
                    .add(rolledRight.scale(screenX * horizontalScale))
                    .add(rolledUp.scale(screenY * verticalScale))
                    .normalize();
                Vec3 end = origin.add(direction.scale(MAX_DISTANCE));
                HitResult hit = client.level.clip(new ClipContext(origin, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
                depths[y * width + x] = hit.getType() == HitResult.Type.MISS ? MAX_DISTANCE : origin.distanceTo(hit.getLocation());
            }
        }
        return new SceneDepthMap(width, height, depths);
    }

    double sample(double normalizedX, double normalizedY) {
        double x = clamp(normalizedX, 0.0, 1.0) * (width - 1);
        double y = clamp(normalizedY, 0.0, 1.0) * (height - 1);
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = Math.min(width - 1, x0 + 1);
        int y1 = Math.min(height - 1, y0 + 1);
        double tx = x - x0;
        double ty = y - y0;
        double top = mix(distances[y0 * width + x0], distances[y0 * width + x1], tx);
        double bottom = mix(distances[y1 * width + x0], distances[y1 * width + x1], tx);
        return mix(top, bottom, ty);
    }

    private static double mix(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
