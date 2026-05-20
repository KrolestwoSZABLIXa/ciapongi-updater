package pl.szablix.ciapongiupdater;

import javazoom.jl.player.Player;
import java.io.BufferedInputStream;
import java.io.InputStream;

public class MusicManager {
    private static Player player;
    private static boolean playing = false;
    private static Thread musicThread;

    public static void playUpdateMusic() {
        if (playing) return;
        playing = true;
        musicThread = new Thread(() -> {
            try {
                while (playing) {
                    InputStream is = MusicManager.class.getResourceAsStream("/assets/ciapongiupdater/update_music.mp3");
                    if (is == null) break;
                    BufferedInputStream bis = new BufferedInputStream(is);
                    player = new Player(bis);
                    player.play();
                }
            } catch (Exception e) {
                // Ignore errors to prevent crash
            }
        }, "CiapongiUpdater-Music");
        musicThread.setDaemon(true);
        musicThread.start();
    }

    public static void stop() {
        playing = false;
        if (player != null) {
            try {
                player.close();
            } catch (Exception e) {}
        }
        if (musicThread != null) {
            musicThread.interrupt();
        }
    }
}
