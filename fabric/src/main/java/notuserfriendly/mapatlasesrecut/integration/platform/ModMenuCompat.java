package notuserfriendly.mapatlasesrecut.integration.platform;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.mehvahdjukaar.moonlight.api.platform.ClientHelper;
import notuserfriendly.mapatlasesrecut.MapAtlasesMod;

public class ModMenuCompat implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> ClientHelper.getMoonlightConfigScreen(MapAtlasesMod.MOD_ID, parent, null);
    }

}
