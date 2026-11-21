package org.example.ronin;

import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screen.Screen;

import java.util.function.Function;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public Function<Screen, Screen> getModConfigScreenFactory() {
        return SwapHistoryScreen::new;
    }
}
