package de.hysky.skyblocker.skyblock.end;

import com.mojang.authlib.properties.PropertyMap;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.skyblock.tabhud.widget.Widget;
import de.hysky.skyblocker.skyblock.tabhud.widget.component.IcoTextComponent;
import de.hysky.skyblocker.skyblock.tabhud.widget.component.PlainTextComponent;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

public class EndHudWidget extends Widget {
    private static final MutableText TITLE = Text.literal("The End").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD);

    public static final EndHudWidget INSTANCE = new EndHudWidget(TITLE, Formatting.DARK_PURPLE.getColorValue());
    private static final NumberFormat DECIMAL_FORMAT = NumberFormat.getInstance(Locale.US);
    private static final ItemStack ENDERMAN_HEAD = new ItemStack(Items.PLAYER_HEAD);
    private static final ItemStack POPPY = new ItemStack(Items.POPPY);

    static {
        DECIMAL_FORMAT.setMinimumFractionDigits(0);
        DECIMAL_FORMAT.setMaximumFractionDigits(2);

        ENDERMAN_HEAD.set(DataComponentTypes.PROFILE, new ProfileComponent(Optional.of("MHF_Enderman"), Optional.empty(), new PropertyMap()));
        POPPY.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);

        INSTANCE.setX(SkyblockerConfigManager.get().otherLocations.end.x);
        INSTANCE.setY(SkyblockerConfigManager.get().otherLocations.end.y);
    }

    public EndHudWidget(MutableText title, Integer colorValue) {
        super(title, colorValue);
        this.setX(5);
        this.setY(5);
        this.update();
    }

    @Override
    public void updateContent() {
        // Zealots
        if (SkyblockerConfigManager.get().otherLocations.end.zealotKillsEnabled) {
            addComponent(new IcoTextComponent(ENDERMAN_HEAD, Text.literal("Zealots").formatted(Formatting.BOLD)));
            addComponent(new PlainTextComponent(Text.translatable("skyblocker.end.hud.zealotsSinceLastEye", TheEnd.zealotsSinceLastEye)));
            addComponent(new PlainTextComponent(Text.translatable("skyblocker.end.hud.zealotsTotalKills", TheEnd.zealotsKilled)));
            String avg = TheEnd.eyes == 0 ? "???" : DECIMAL_FORMAT.format((float) TheEnd.zealotsKilled / TheEnd.eyes);
            addComponent(new PlainTextComponent(Text.translatable("skyblocker.end.hud.avgKillsPerEye", avg)));
        }

        // Endstone protector
        if (SkyblockerConfigManager.get().otherLocations.end.protectorLocationEnabled) {
            addComponent(new IcoTextComponent(POPPY, Text.literal("Endstone Protector").formatted(Formatting.BOLD)));
            if (TheEnd.stage == 5) {
                addComponent(new PlainTextComponent(Text.translatable("skyblocker.end.hud.stage", "IMMINENT")));
            } else {
                addComponent(new PlainTextComponent(Text.translatable("skyblocker.end.hud.stage", String.valueOf(TheEnd.stage))));
            }
            if (TheEnd.currentProtectorLocation == null) {
                addComponent(new PlainTextComponent(Text.translatable("skyblocker.end.hud.location", "?")));
            } else {
                addComponent(new PlainTextComponent(Text.translatable("skyblocker.end.hud.location", TheEnd.currentProtectorLocation.name())));
            }
        }
    }
}
