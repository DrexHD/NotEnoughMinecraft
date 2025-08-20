package de.tomalbrc.cameraobscura.render.renderer;

import me.drex.nem.config.ModConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.ForkJoinPool;

public class ImageRenderer extends AbstractRenderer<int[]> {

    private static final ForkJoinPool pool = new ForkJoinPool(ModConfig.getInstance().renderThreadCount);

    public ImageRenderer(LivingEntity entity, int width, int height, int renderDistance) {
        super(entity, width, height, renderDistance);
    }

    public int[] render() {
        Vec3 eyes = this.entity.getEyePosition();

        int[] pixels = new int[width * height];

        List<Ray> rays = this.castRays(this.entity);
        pool.submit(() -> {
            // https://stackoverflow.com/questions/21163108/custom-thread-pool-in-java-8-parallel-stream
            rays.parallelStream().forEach(ray -> {
                try {
                    int color = raytracer.trace(eyes, ray.direction());
                    int x = width - ray.xOrigin() - 1;
                    int y = ray.yOrigin();
                    pixels[x + width * y] = color;
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
        }).join();


        return pixels;
    }
}
