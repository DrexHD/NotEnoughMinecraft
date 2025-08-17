package de.tomalbrc.cameraobscura.render.renderer;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageRenderer extends AbstractRenderer<int[]> {
    public ImageRenderer(LivingEntity entity, int width, int height, int renderDistance) {
        super(entity, width, height, renderDistance);
    }

    public int[] render() {
        Vec3 eyes = this.entity.getEyePosition();

        int[] pixels = new int[width * height];

        this.castRays(this.entity).parallelStream().forEach(ray -> {
            int color = raytracer.trace(eyes, ray.direction());
            int x = width - ray.xOrigin() - 1;
            int y = ray.yOrigin();
            pixels[x + width * y] = color;
        });

        return pixels;
    }
}
