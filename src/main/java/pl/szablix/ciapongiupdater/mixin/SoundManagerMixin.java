package pl.szablix.ciapongiupdater.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.option.GameOptions;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameOptions.class)
public class SoundManagerMixin {
    @Inject(method = "getSoundVolume(Lnet/minecraft/sound/SoundCategory;)F", at = @At("RETURN"), cancellable = true)
    private void onGetSoundVolume(SoundCategory category, CallbackInfoReturnable<Float> cir) {
        if (category == SoundCategory.MUSIC) {
            if (MinecraftClient.getInstance().getOverlay() instanceof SplashOverlay) {
                cir.setReturnValue(0.0f);
            }
        }
    }
}
