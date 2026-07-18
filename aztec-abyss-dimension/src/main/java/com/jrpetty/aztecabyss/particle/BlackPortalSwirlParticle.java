package com.jrpetty.aztecabyss.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

import javax.annotation.Nullable;

/** The signature "black flame" swirl that distinguishes an Abyss portal from a nether one. */
public class BlackPortalSwirlParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected BlackPortalSwirlParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.friction = 0.96F;
        this.gravity = 0.0F;
        this.lifetime = 40 + this.random.nextInt(20);
        this.quadSize = 0.1F + this.random.nextFloat() * 0.1F;
        this.setSpriteFromAge(sprites);
        this.rCol = 0.05F;
        this.gCol = 0.0F;
        this.bCol = 0.08F;
        this.alpha = 0.9F;
        this.xd = xd * 0.2;
        this.yd = yd * 0.2;
        this.zd = zd * 0.2;
        this.hasPhysics = false;
        this.sprites = sprites;
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
    }

    @Override
    public net.minecraft.client.particle.ParticleRenderType getRenderType() {
        return net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new BlackPortalSwirlParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
