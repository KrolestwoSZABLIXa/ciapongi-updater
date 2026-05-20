package pl.szablix.ciapongiupdater;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CiapongiUpdater implements ModInitializer {
    public static final String MOD_ID = "ciapongiupdater";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Ciapongi Updater initialized!");
    }
}
