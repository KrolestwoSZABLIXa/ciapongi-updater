package pl.szablix.ciapongiupdater.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Overlay;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.szablix.ciapongiupdater.UpdateManager;
import pl.szablix.ciapongiupdater.I18n;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin extends Overlay {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private ResourceReload reload;

    private static final Identifier CIAPONGI_LOGO = new Identifier("ciapongiupdater", "logo.png");
    private static final Identifier BACKGROUND = new Identifier("ciapongiupdater", "background.png");
    private static final UpdateManager UPDATER = new UpdateManager();
    private static boolean updateChecked = false;
    private long reloadStartTime = -1L;
    private float reloadProgress = 0f;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(MinecraftClient client, ResourceReload reload, Consumer<Optional<Throwable>> exceptionHandler, boolean reboot, CallbackInfo ci) {
        if (!updateChecked) {
            updateChecked = true;
            new Thread(() -> {
                UPDATER.checkForUpdates();
                if (UPDATER.updateAvailable) {
                    UPDATER.performUpdate();
                }
            }, "CiapongiUpdater-Thread").start();
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int width = this.client.getWindow().getScaledWidth();
        int height = this.client.getWindow().getScaledHeight();
        
        if (this.reloadStartTime == -1L) {
            this.reloadStartTime = Util.getMeasuringTimeMs();
        }

        this.reloadProgress = MathHelper.clamp(this.reloadProgress, 0.0f, 1.0f);
        this.reloadProgress = MathHelper.lerp(delta * 0.5f, this.reloadProgress, this.reload.getProgress());

        boolean isUpdating = UPDATER.isWorking || UPDATER.updateAvailable || UPDATER.needsRestart;
        
        float f;
        float g;

        // Force full opacity during update process
        if (isUpdating) {
            f = -1.0f;
            g = 1.0f;
        } else {
            f = this.reload.isComplete() ? (float)(Util.getMeasuringTimeMs() - this.reloadStartTime) / 1000.0f : -1.0f;
            g = f > 1.0f ? 1.0f - MathHelper.clamp(f - 1.0f, 0.0f, 1.0f) : 1.0f;
        }

        int alpha = MathHelper.ceil(g * 255.0f) << 24;
        int textColor = 0xFFFFFF | alpha;
        int subTextColor = 0xAAAAAA | alpha;

        int barWidth = (int)(width * 0.6f);
        int barX = (width - barWidth) / 2;
        int barY = (height / 2) + 40;

        // Render background and logo
        context.setShaderColor(1.0f, 1.0f, 1.0f, g);
        context.drawTexture(BACKGROUND, 0, 0, 0, 0, width, height, width, height);

        int logoSize = 128;
        int logoX = (width - logoSize) / 2;
        int logoY = (height / 2) - logoSize;
        context.drawTexture(CIAPONGI_LOGO, logoX, logoY, 0.0f, 0.0f, logoSize, logoSize, logoSize, logoSize);
        context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        if (isUpdating) {
            drawTracks(context, barX, barY, barWidth, g);
            drawTrain(context, barX, barY, UPDATER.progress, g);

            renderPixelStatus(context, width, barY, alpha, g);

            if (UPDATER.needsRestart) {
                int btnWidth = 120;
                int btnHeight = 24;
                int btnX = (width - btnWidth) / 2;
                int btnY = barY + 60;
                boolean hovered = mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= btnY && mouseY <= btnY + btnHeight;
                
                int btnColor = (hovered ? 0x666666 : 0x444444) | alpha;
                context.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, btnColor);
                context.drawBorder(btnX, btnY, btnWidth, btnHeight, textColor);
                
                String btnText = I18n.get("ui.close").toUpperCase();
                drawPixelString(context, btnText, btnX + (btnWidth - (btnText.length() * 6)) / 2, btnY + (btnHeight - 7) / 2, textColor, 1);

                if (hovered && GLFW.glfwGetMouseButton(this.client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                    this.client.scheduleStop();
                }
            }
            ci.cancel(); 
        } else {
            drawTracks(context, barX, barY, barWidth, g);
            drawTrain(context, barX, barY, this.reloadProgress, g);
            if (this.reload.isComplete()) {
                if (f == -1.0f) this.reloadStartTime = Util.getMeasuringTimeMs();
                if (f >= 2.0f) this.client.setOverlay(null);
            }
            ci.cancel(); 
        }
    }

    private void renderPixelStatus(DrawContext context, int width, int barY, int alpha, float g) {
        String sKey = UPDATER.statusKey;
        String sArg = UPDATER.statusArg;
        String status = I18n.get(sKey != null ? sKey : "status.checking", sArg).toUpperCase();
        
        String currentSpeed = UPDATER.speed;
        String currentSize = UPDATER.sizeInfo;
        float currentProgress = UPDATER.progress;
        int textColor = 0xFFFFFF | alpha;
        int subTextColor = 0xAAAAAA | alpha;

        if (!status.isEmpty()) {
            int scale = 1;
            int textWidth = status.length() * 6 * scale;
            drawPixelString(context, status, (width - textWidth) / 2, barY + 20, textColor, scale);
            
            if (UPDATER.needsRestart) {
                String restartMsg = I18n.get("status.restart").toUpperCase();
                int rWidth = restartMsg.length() * 6;
                drawPixelString(context, restartMsg, (width - rWidth) / 2, barY + 35, subTextColor, 1);
            } else if (!currentSpeed.isEmpty() || !currentSize.isEmpty()) {
                String subStatus = (currentSpeed + " | " + currentSize + " " + (int)(currentProgress * 200) + "%").toUpperCase();
                int subWidth = subStatus.length() * 6;
                drawPixelString(context, subStatus, (width - subWidth) / 2, barY + 35, subTextColor, 1);
            }
        }
    }

    private void drawPixelString(DrawContext context, String text, int x, int y, int color, int scale) {
        if (text == null) return;
        for (int i = 0; i < text.length(); i++) {
            drawPixelChar(context, text.charAt(i), x + (i * 6 * scale), y, color, scale);
        }
    }

    private void drawPixelChar(DrawContext context, char c, int x, int y, int color, int scale) {
        int[] glyph = getGlyph(c);
        if (glyph == null) return; // Safety first
        for (int r = 0; r < 7; r++) {
            for (int col = 0; col < 5; col++) {
                if ((glyph[r] & (1 << (4 - col))) != 0) {
                    context.fill(x + col * scale, y + r * scale, x + (col + 1) * scale, y + (r + 1) * scale, color);
                }
            }
        }
    }

    private int[] getGlyph(char c) {
        return switch (c) {
            case '0' -> new int[]{31, 17, 17, 17, 17, 17, 31};
            case '1' -> new int[]{4, 12, 4, 4, 4, 4, 14};
            case '2' -> new int[]{31, 1, 1, 31, 16, 16, 31};
            case '3' -> new int[]{31, 1, 1, 31, 1, 1, 31};
            case '4' -> new int[]{17, 17, 17, 31, 1, 1, 1};
            case '5' -> new int[]{31, 16, 16, 31, 1, 1, 31};
            case '6' -> new int[]{31, 16, 16, 31, 17, 17, 31};
            case '7' -> new int[]{31, 1, 1, 2, 4, 8, 8};
            case '8' -> new int[]{31, 17, 17, 31, 17, 17, 31};
            case '9' -> new int[]{31, 17, 17, 31, 1, 1, 31};
            case 'A' -> new int[]{14, 17, 17, 31, 17, 17, 17};
            case 'B' -> new int[]{30, 17, 17, 30, 17, 17, 30};
            case 'C' -> new int[]{15, 16, 16, 16, 16, 16, 15};
            case 'D' -> new int[]{30, 17, 17, 17, 17, 17, 30};
            case 'E' -> new int[]{31, 16, 16, 30, 16, 16, 31};
            case 'F' -> new int[]{31, 16, 16, 30, 16, 16, 16};
            case 'G' -> new int[]{15, 16, 16, 23, 17, 17, 15};
            case 'H' -> new int[]{17, 17, 17, 31, 17, 17, 17};
            case 'I' -> new int[]{14, 4, 4, 4, 4, 4, 14};
            case 'J' -> new int[]{7, 2, 2, 2, 2, 18, 12};
            case 'K' -> new int[]{17, 18, 20, 24, 20, 18, 17};
            case 'L' -> new int[]{16, 16, 16, 16, 16, 16, 31};
            case 'M' -> new int[]{17, 27, 21, 17, 17, 17, 17};
            case 'N' -> new int[]{17, 25, 21, 19, 17, 17, 17};
            case 'O' -> new int[]{14, 17, 17, 17, 17, 17, 14};
            case 'P' -> new int[]{30, 17, 17, 30, 16, 16, 16};
            case 'Q' -> new int[]{14, 17, 17, 17, 21, 18, 13};
            case 'R' -> new int[]{30, 17, 17, 30, 20, 18, 17};
            case 'S' -> new int[]{15, 16, 16, 14, 1, 1, 30};
            case 'T' -> new int[]{31, 4, 4, 4, 4, 4, 4};
            case 'U' -> new int[]{17, 17, 17, 17, 17, 17, 14};
            case 'V' -> new int[]{17, 17, 17, 17, 17, 10, 4};
            case 'W' -> new int[]{17, 17, 17, 21, 21, 27, 17};
            case 'X' -> new int[]{17, 17, 10, 4, 10, 17, 17};
            case 'Y' -> new int[]{17, 17, 17, 10, 4, 4, 4};
            case 'Z' -> new int[]{31, 1, 2, 4, 8, 16, 31};
            case '.' -> new int[]{0, 0, 0, 0, 0, 0, 4};
            case ',' -> new int[]{0, 0, 0, 0, 0, 4, 8};
            case ':' -> new int[]{0, 4, 0, 0, 0, 4, 0};
            case '/' -> new int[]{1, 2, 4, 8, 16, 16, 16};
            case '|' -> new int[]{4, 4, 4, 4, 4, 4, 4};
            case '(' -> new int[]{2, 4, 4, 4, 4, 4, 2};
            case ')' -> new int[]{8, 4, 4, 4, 4, 4, 8};
            case '!' -> new int[]{4, 4, 4, 4, 4, 0, 4};
            case '-' -> new int[]{0, 0, 0, 31, 0, 0, 0};
            case '%' -> new int[]{24, 24, 2, 4, 8, 3, 3};
            case ' ' -> new int[]{0, 0, 0, 0, 0, 0, 0};
            default -> new int[]{31, 31, 31, 31, 31, 31, 31};
        };
    }

    private void drawTracks(DrawContext context, int x, int y, int width, float opacity) {
        int alpha = MathHelper.ceil(opacity * 255.0f) << 24;
        int railColor = 0xFF555555 | alpha;
        int sleeperColor = 0xFF443322 | alpha;
        for (int i = 0; i < width; i += 10) {
            context.fill(x + i, y + 1, x + i + 4, y + 4, sleeperColor);
        }
        context.fill(x, y + 1, x + width, y + 2, railColor);
        context.fill(x, y + 3, x + width, y + 4, railColor);
    }

    private void drawTrain(DrawContext context, int x, int y, float progress, float opacity) {
        int alpha = MathHelper.ceil(opacity * 255.0f) << 24;
        int barWidth = (int)(this.client.getWindow().getScaledWidth() * 0.6f);
        int trainX = x + (int)(progress * barWidth) - 10;
        context.fill(trainX, y - 10, trainX + 20, y, 0xFFBBBBBB | alpha);
        context.fill(trainX + 15, y - 15, trainX + 18, y - 10, 0xFF333333 | alpha);
        context.fill(trainX + 13, y - 8, trainX + 18, y - 4, 0xFF77AAFF | alpha);
        context.fill(trainX + 20, y - 3, trainX + 22, y - 1, 0xFF000000 | alpha);
        context.fill(trainX + 2, y, trainX + 6, y + 4, 0xFF000000 | alpha);
        context.fill(trainX + 14, y, trainX + 18, y + 4, 0xFF000000 | alpha);
        for (int i = 1; i <= 4; i++) {
            int wagonX = trainX - (i * 24);
            if (wagonX > x - 20) {
                context.fill(wagonX, y - 8, wagonX + 20, y, 0xFF999999 | alpha);
                context.fill(wagonX + 2, y - 6, wagonX + 18, y - 3, 0xFF444444 | alpha);
                context.fill(wagonX + 20, y - 5, wagonX + 24, y - 3, 0xFF555555 | alpha);
                context.fill(wagonX + 3, y, wagonX + 7, y + 4, 0xFF000000 | alpha);
                context.fill(wagonX + 13, y, wagonX + 17, y + 4, 0xFF000000 | alpha);
            }
        }
    }
}
