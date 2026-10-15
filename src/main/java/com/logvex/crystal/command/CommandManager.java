package com.logvex.crystal.command;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.ClickGui;
import com.logvex.crystal.gui.HudEditorScreen;
import com.logvex.crystal.gui.ScreenGuard;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.notification.NotificationLevel;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.ColorSetting;
import com.logvex.crystal.setting.DoubleSetting;
import com.logvex.crystal.setting.EnumSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.setting.KeybindSetting;
import com.logvex.crystal.setting.Setting;
import com.logvex.crystal.setting.SettingGroup;
import com.logvex.crystal.setting.StringSetting;
import com.logvex.crystal.theme.ThemePreset;
import com.logvex.crystal.theme.ThemeVariant;
import com.logvex.crystal.util.ColorUtil;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * The {@code .lvx} command system, dispatched from intercepted chat rather than a
 * server-side Brigadier command.
 *
 * <p>Anarchy servers routinely reject unknown {@code /} commands, so the client never
 * sends anything to the server: a line starting with {@code .lvx} is intercepted locally
 * and handled here. This keeps the command surface available everywhere.
 *
 * <p>Subcommands: {@code help, toggle, bind, set, theme, preset, variant, opacity,
 * gradient, glow, radius, scale, animation, decorations, cat, reducedmotion, blur,
 * cursor, sounds, splash, hud, hudedit, save, load, version}.
 */
public final class CommandManager {

    public static final String PREFIX = ".lvx";
    /** The alternate prefix anarchy clients conventionally accept. */
    public static final String ALT_PREFIX = ".crystal";

    private final CrystalClient client;

    public CommandManager(CrystalClient client) {
        this.client = client;
    }

    /**
     * Handles a raw chat line. Returns true when the line was a Crystal command and must
     * not be sent to the server.
     */
    public boolean handleChat(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        String body;
        if (trimmed.equalsIgnoreCase(PREFIX) || trimmed.equalsIgnoreCase(ALT_PREFIX)) {
            body = "";
        } else if (trimmed.toLowerCase(Locale.ROOT).startsWith(PREFIX + " ")) {
            body = trimmed.substring(PREFIX.length()).trim();
        } else if (trimmed.toLowerCase(Locale.ROOT).startsWith(ALT_PREFIX + " ")) {
            body = trimmed.substring(ALT_PREFIX.length()).trim();
        } else {
            return false;
        }
        List<String> tokens = CommandTokenizer.tokenize(body);
        try {
            dispatch(tokens);
        } catch (Throwable t) {
            client.notify("Command error: " + t.getMessage(), NotificationLevel.ERROR);
            CrystalClient.LOG.warn("Command failed: {}", body, t);
        }
        return true;
    }

    private void dispatch(List<String> tokens) {
        if (tokens.isEmpty()) {
            help();
            return;
        }
        String command = tokens.get(0).toLowerCase(Locale.ROOT);
        List<String> args = tokens.subList(1, tokens.size());
        switch (command) {
            case "help", "?" -> help();
            case "toggle", "t" -> toggle(args);
            case "bind", "b" -> bind(args);
            case "set" -> set(args);
            case "theme" -> theme(args);
            case "preset" -> preset(args);
            case "variant" -> variant(args);
            case "accent", "primary" -> accent(args, true);
            case "secondary" -> accent(args, false);
            case "opacity" -> setFloat(args, "opacity", v -> client.theme().settings().backgroundOpacity = v);
            case "gradient" -> setFloat(args, "gradient", v -> client.theme().settings().gradientIntensity = v);
            case "glow" -> setFloat(args, "glow", v -> client.theme().settings().glowIntensity = v);
            case "radius" -> setInt(args, "radius", v -> client.theme().settings().cornerRadius = v);
            case "scale" -> setFloat(args, "scale", v -> client.theme().settings().uiScale = v);
            case "animation", "anim" -> setFloat(args, "animation", v -> client.theme().settings().animationSpeed = v);
            case "decorations" -> toggleFlag(args, "decorations");
            case "cat", "catcosmetics" -> toggleFlag(args, "cat cosmetics");
            case "reducedmotion", "motion" -> toggleFlag(args, "reduced motion");
            case "blur" -> toggleFlag(args, "blur");
            case "cursor" -> toggleFlag(args, "custom cursor");
            case "sounds" -> toggleFlag(args, "gui sounds");
            case "splash" -> toggleFlag(args, "splash text");
            case "easteregg", "secret" -> easterEgg(args);
            case "hud" -> hud(args);
            case "hudedit" -> hudEdit();
            case "waypoint", "wp" -> waypoint(args);
            case "friend", "friends" -> friend(args);
            case "gui", "clickgui" -> openGui();
            case "save" -> save();
            case "load", "reload" -> load();
            case "list", "modules" -> list(args);
            case "version", "ver" -> client.notify("LOGVEX CRYSTAL " + version(), NotificationLevel.INFO);
            default -> client.notify("Unknown command '" + command + "'. Try .lvx help",
                    NotificationLevel.WARN);
        }
    }

    // ------------------------------------------------------------ module commands

    private void toggle(List<String> args) {
        if (args.isEmpty()) {
            client.notify("Usage: .lvx toggle <module>", NotificationLevel.WARN);
            return;
        }
        Module module = client.modules().byName(CommandTokenizer.join(args));
        if (module == null) {
            client.notify("No module named '" + CommandTokenizer.join(args) + "'", NotificationLevel.ERROR);
            return;
        }
        module.toggle(client.modules());
        client.notify(module.name() + (module.isEnabled() ? " enabled" : " disabled"),
                module.isEnabled() ? NotificationLevel.SUCCESS : NotificationLevel.INFO);
        if (module.lastError() != null) {
            client.notify(module.lastError(), NotificationLevel.WARN);
        }
    }

    private void bind(List<String> args) {
        if (args.size() < 2) {
            client.notify("Usage: .lvx bind <module> <key>", NotificationLevel.WARN);
            return;
        }
        Module module = client.modules().byName(args.get(0));
        if (module == null) {
            client.notify("No module named '" + args.get(0) + "'", NotificationLevel.ERROR);
            return;
        }
        Integer key = KeyParser.parse(args.get(1));
        if (key == null) {
            client.notify("Unknown key '" + args.get(1) + "'", NotificationLevel.ERROR);
            return;
        }
        module.keybind().set(key);
        client.notify("Bound " + module.name() + " to " + module.keybind().displayValue(), NotificationLevel.SUCCESS);
    }

    private void set(List<String> args) {
        if (args.size() < 3) {
            client.notify("Usage: .lvx set <module> <setting> <value>", NotificationLevel.WARN);
            return;
        }
        Module module = client.modules().byName(args.get(0));
        if (module == null) {
            client.notify("No module named '" + args.get(0) + "'", NotificationLevel.ERROR);
            return;
        }
        Setting<?> setting = findSetting(module, args.get(1));
        if (setting == null) {
            client.notify("No setting '" + args.get(1) + "' on " + module.name(), NotificationLevel.ERROR);
            return;
        }
        String value = CommandTokenizer.join(args.subList(2, args.size()));
        if (applyValue(setting, value)) {
            client.notify(module.name() + " " + setting.name() + " = " + setting.displayValue(),
                    NotificationLevel.SUCCESS);
        } else {
            client.notify("Invalid value '" + value + "' for " + setting.name(), NotificationLevel.ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean applyValue(Setting<?> setting, String value) {
        try {
            if (setting instanceof BooleanSetting bool) {
                bool.set(parseBool(value));
            } else if (setting instanceof IntSetting integer) {
                integer.set(Integer.parseInt(value.trim()));
            } else if (setting instanceof DoubleSetting decimal) {
                decimal.set(Double.parseDouble(value.trim()));
            } else if (setting instanceof StringSetting string) {
                string.set(value);
            } else if (setting instanceof KeybindSetting keybind) {
                Integer key = KeyParser.parse(value);
                if (key == null) {
                    return false;
                }
                keybind.set(key);
            } else if (setting instanceof ColorSetting color) {
                color.set(parseColor(value));
            } else if (setting instanceof EnumSetting<?> enumeration) {
                return applyEnumByName(enumeration, value);
            } else {
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean applyEnumByName(EnumSetting<?> setting, String value) {
        for (Object candidate : setting.values()) {
            if (((Enum<?>) candidate).name().equalsIgnoreCase(value.trim())) {
                applyEnumConstant(setting, candidate);
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyEnumConstant(EnumSetting setting, Object constant) {
        setting.set((Enum) constant);
    }

    private <E extends Enum<E>> boolean applyEnum(EnumSetting<E> setting, String value) {
        for (E candidate : setting.values()) {
            if (candidate.name().equalsIgnoreCase(value.trim())) {
                setting.set(candidate);
                return true;
            }
        }
        return false;
    }

    private Setting<?> findSetting(Module module, String name) {
        for (SettingGroup group : module.groups()) {
            for (Setting<?> setting : group.settings()) {
                if (setting.name().equalsIgnoreCase(name)) {
                    return setting;
                }
            }
        }
        if (module.keybind().name().equalsIgnoreCase(name)) {
            return module.keybind();
        }
        return null;
    }

    // ------------------------------------------------------------ theme commands

    private void theme(List<String> args) {
        if (args.isEmpty()) {
            client.notify("Theme: " + client.theme().palette().preset().label()
                    + " (" + client.theme().palette().variant().label() + ")", NotificationLevel.INFO);
            client.notify("Presets: " + String.join(", ", ThemePreset.labels()), NotificationLevel.INFO);
            return;
        }
        preset(args);
    }

    private void preset(List<String> args) {
        if (args.isEmpty()) {
            client.notify("Presets: " + String.join(", ", ThemePreset.labels()), NotificationLevel.INFO);
            return;
        }
        String name = CommandTokenizer.join(args);
        if (client.theme().applyPreset(name)) {
            client.notify("Theme: " + client.theme().palette().preset().label(), NotificationLevel.SUCCESS);
            client.saveAll();
        } else {
            client.notify("Unknown preset '" + name + "'", NotificationLevel.ERROR);
        }
    }

    private void variant(List<String> args) {
        if (args.isEmpty()) {
            client.notify("Variant: " + client.theme().palette().variant().label(), NotificationLevel.INFO);
            return;
        }
        client.theme().setVariant(ThemeVariant.parse(args.get(0)));
        client.notify("Variant: " + client.theme().palette().variant().label(), NotificationLevel.SUCCESS);
    }

    private void accent(List<String> args, boolean primary) {
        if (args.isEmpty()) {
            client.notify("Usage: .lvx " + (primary ? "accent" : "secondary") + " <#RRGGBB|reset>",
                    NotificationLevel.WARN);
            return;
        }
        if (args.get(0).equalsIgnoreCase("reset")) {
            client.theme().settings().clearOverrides();
            client.theme().update();
            client.notify("Accent overrides cleared", NotificationLevel.SUCCESS);
            return;
        }
        int color = parseColor(args.get(0));
        if (primary) {
            client.theme().setPrimary(color);
        } else {
            client.theme().setSecondary(color);
        }
        client.notify((primary ? "Primary" : "Secondary") + " accent set to "
                + String.format("#%06X", color & 0xFFFFFF), NotificationLevel.SUCCESS);
    }

    private void setFloat(List<String> args, String label, java.util.function.Consumer<Float> setter) {
        if (args.isEmpty()) {
            client.notify("Usage: .lvx " + label + " <0..1>", NotificationLevel.WARN);
            return;
        }
        try {
            setter.accept(Float.parseFloat(args.get(0)));
            client.theme().update();
            client.notify(label + " set", NotificationLevel.SUCCESS);
        } catch (NumberFormatException e) {
            client.notify("Invalid number '" + args.get(0) + "'", NotificationLevel.ERROR);
        }
    }

    private void setInt(List<String> args, String label, java.util.function.IntConsumer setter) {
        if (args.isEmpty()) {
            client.notify("Usage: .lvx " + label + " <int>", NotificationLevel.WARN);
            return;
        }
        try {
            setter.accept(Integer.parseInt(args.get(0)));
            client.theme().update();
            client.notify(label + " set", NotificationLevel.SUCCESS);
        } catch (NumberFormatException e) {
            client.notify("Invalid number '" + args.get(0) + "'", NotificationLevel.ERROR);
        }
    }

    private void toggleFlag(List<String> args, String label) {
        var s = client.theme().settings();
        Boolean explicit = args.isEmpty() ? null : parseBool(args.get(0));
        boolean value;
        switch (label) {
            case "decorations" -> s.decorations = value = (explicit != null ? explicit : !s.decorations);
            case "cat cosmetics" -> s.catCosmetics = value = (explicit != null ? explicit : !s.catCosmetics);
            case "reduced motion" -> s.reducedMotion = value = (explicit != null ? explicit : !s.reducedMotion);
            case "blur" -> s.blur = value = (explicit != null ? explicit : !s.blur);
            case "custom cursor" -> s.customCursor = value = (explicit != null ? explicit : !s.customCursor);
            case "gui sounds" -> s.guiSounds = value = (explicit != null ? explicit : !s.guiSounds);
            case "splash text" -> s.splashText = value = (explicit != null ? explicit : !s.splashText);
            default -> {
                return;
            }
        }
        client.theme().update();
        client.notify(label + ": " + (value ? "on" : "off"), NotificationLevel.SUCCESS);
    }

    private void easterEgg(List<String> args) {
        var s = client.theme().settings();
        s.easterEgg = args.isEmpty() ? !s.easterEgg : parseBool(args.get(0));
        client.theme().update();
        if (s.easterEgg) {
            com.logvex.crystal.cosmetic.GuiSounds.secret();
            client.notify("\u2661 purr... you found the secret \u2661", NotificationLevel.CUTE);
        } else {
            client.notify("Easter egg: off", NotificationLevel.INFO);
        }
    }

    // ------------------------------------------------------------ hud / gui / io

    private void hud(List<String> args) {
        if (args.isEmpty()) {
            client.hud().toggleHud();
            client.notify("HUD " + (client.hud().isHudVisible() ? "shown" : "hidden"), NotificationLevel.SUCCESS);
            return;
        }
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "toggle" -> {
                client.hud().toggleHud();
                client.notify("HUD " + (client.hud().isHudVisible() ? "shown" : "hidden"), NotificationLevel.SUCCESS);
            }
            case "edit" -> hudEdit();
            case "on", "off" -> {
                client.hud().setHudVisible(args.get(0).equalsIgnoreCase("on"));
                client.notify("HUD " + args.get(0).toLowerCase(Locale.ROOT), NotificationLevel.SUCCESS);
            }
            default -> {
                if (args.size() >= 2) {
                    boolean visible = parseBool(args.get(1));
                    client.hud().setElementVisible(args.get(0), visible);
                    client.notify(args.get(0) + ": " + (visible ? "on" : "off"), NotificationLevel.SUCCESS);
                } else {
                    client.notify("Usage: .lvx hud <element> <on|off> | toggle | edit", NotificationLevel.WARN);
                }
            }
        }
    }

    private void hudEdit() {
        if (!ScreenGuard.fontsReady()) {
            return;
        }
        MinecraftClient.getInstance().setScreen(new HudEditorScreen(client.hud(), client.theme()));
    }

    // ------------------------------------------------------------ waypoints / friends

    private void waypoint(List<String> args) {
        if (args.isEmpty()) {
            client.notify("Usage: .lvx waypoint <add|remove|list|clear> [...]", NotificationLevel.WARN);
            return;
        }
        var store = client.waypoints();
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "add", "set" -> {
                if (args.size() < 2) {
                    client.notify("Usage: .lvx waypoint add <name> [x y z]", NotificationLevel.WARN);
                    return;
                }
                String name = args.get(1);
                MinecraftClient mc = MinecraftClient.getInstance();
                int x;
                int y;
                int z;
                if (args.size() >= 5) {
                    try {
                        x = (int) Double.parseDouble(args.get(2));
                        y = (int) Double.parseDouble(args.get(3));
                        z = (int) Double.parseDouble(args.get(4));
                    } catch (NumberFormatException e) {
                        client.notify("Coordinates must be numbers", NotificationLevel.ERROR);
                        return;
                    }
                } else if (mc.player != null) {
                    x = (int) Math.floor(mc.player.getX());
                    y = (int) Math.floor(mc.player.getY());
                    z = (int) Math.floor(mc.player.getZ());
                } else {
                    client.notify("Not in a world; supply coordinates", NotificationLevel.ERROR);
                    return;
                }
                String dimension = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";
                store.put(new com.logvex.crystal.util.waypoint.Waypoint(name, x, y, z,
                        client.theme().palette().primary(), dimension));
                client.saveAll();
                client.notify("Waypoint '" + name + "' at " + x + " " + y + " " + z, NotificationLevel.SUCCESS);
            }
            case "remove", "del", "delete", "rm" -> {
                if (args.size() < 2) {
                    client.notify("Usage: .lvx waypoint remove <name>", NotificationLevel.WARN);
                    return;
                }
                if (store.remove(args.get(1))) {
                    client.saveAll();
                    client.notify("Removed waypoint '" + args.get(1) + "'", NotificationLevel.SUCCESS);
                } else {
                    client.notify("No waypoint named '" + args.get(1) + "'", NotificationLevel.ERROR);
                }
            }
            case "clear" -> {
                store.clear();
                client.saveAll();
                client.notify("Waypoints cleared", NotificationLevel.SUCCESS);
            }
            case "list" -> {
                if (store.size() == 0) {
                    client.notify("No waypoints saved", NotificationLevel.INFO);
                    return;
                }
                for (var waypoint : store.all()) {
                    client.notify(waypoint.name() + " " + waypoint.x() + " " + waypoint.y() + " "
                            + waypoint.z(), NotificationLevel.INFO);
                }
            }
            default -> client.notify("Usage: .lvx waypoint <add|remove|list|clear> [...]",
                    NotificationLevel.WARN);
        }
    }

    private void friend(List<String> args) {
        if (args.isEmpty()) {
            client.notify("Usage: .lvx friend <add|remove|list> [name]", NotificationLevel.WARN);
            return;
        }
        var friends = client.friends();
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.size() < 2) {
                    client.notify("Usage: .lvx friend add <name>", NotificationLevel.WARN);
                    return;
                }
                String name = CommandTokenizer.join(args.subList(1, args.size()));
                if (friends.add(name)) {
                    client.saveAll();
                    client.notify("Added friend '" + name + "' \u2661", NotificationLevel.CUTE);
                } else {
                    client.notify("'" + name + "' is already a friend", NotificationLevel.INFO);
                }
            }
            case "remove", "del", "rm" -> {
                if (args.size() < 2) {
                    client.notify("Usage: .lvx friend remove <name>", NotificationLevel.WARN);
                    return;
                }
                String name = CommandTokenizer.join(args.subList(1, args.size()));
                if (friends.remove(name)) {
                    client.saveAll();
                    client.notify("Removed friend '" + name + "'", NotificationLevel.SUCCESS);
                } else {
                    client.notify("'" + name + "' is not a friend", NotificationLevel.ERROR);
                }
            }
            case "list" -> client.notify(friends.size() == 0 ? "No friends added"
                    : "Friends: " + String.join(", ", friends.names()), NotificationLevel.INFO);
            default -> client.notify("Usage: .lvx friend <add|remove|list> [name]", NotificationLevel.WARN);
        }
    }

    private void openGui() {
        ClickGui gui = client.clickGui();
        if (gui != null) {
            gui.open();
        }
    }

    private void save() {
        client.saveAll();
        client.notify("Configuration saved \u2661", NotificationLevel.SUCCESS);
    }

    private void load() {
        client.reloadAll();
        client.notify("Configuration reloaded", NotificationLevel.SUCCESS);
    }

    private void list(List<String> args) {
        if (args.isEmpty()) {
            client.notify(client.modules().size() + " modules loaded", NotificationLevel.INFO);
            return;
        }
        for (com.logvex.crystal.module.Category category : com.logvex.crystal.module.Category.values()) {
            if (category.name().equalsIgnoreCase(args.get(0))) {
                StringBuilder builder = new StringBuilder(category.displayName() + ": ");
                for (Module module : client.modules().byCategory(category)) {
                    builder.append(module.name()).append(' ');
                }
                client.notify(builder.toString().trim(), NotificationLevel.INFO);
                return;
            }
        }
        client.notify("Unknown category '" + args.get(0) + "'", NotificationLevel.ERROR);
    }

    private void help() {
        client.notify("\u2661 LOGVEX CRYSTAL commands \u2661", NotificationLevel.CUTE);
        for (String line : new String[]{
                ".lvx toggle <module>",
                ".lvx bind <module> <key>",
                ".lvx set <module> <setting> <value>",
                ".lvx preset <name> | theme | variant <dark|light>",
                ".lvx accent <#RRGGBB|reset> | secondary ...",
                ".lvx opacity|gradient|glow|scale|animation <0..1>",
                ".lvx radius <0..12> | decorations|cat|motion|blur|cursor|sounds|splash <on|off>",
                ".lvx hud <element> <on|off> | hudedit | gui",
                ".lvx waypoint <add|remove|list|clear> [name] [x y z]",
                ".lvx friend <add|remove|list> [name]",
                ".lvx save | load | list [category] | version"
        }) {
            client.notify(line, NotificationLevel.INFO);
        }
    }

    // ------------------------------------------------------------ helpers

    private static Boolean parseBool(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "1", "enable", "enabled" -> true;
            case "false", "off", "no", "0", "disable", "disabled" -> false;
            default -> null;
        };
    }

    /** Parses {@code #RRGGBB}, {@code #AARRGGBB} or a decimal integer into an ARGB int. */
    private static int parseColor(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("#")) {
            String hex = trimmed.substring(1);
            if (hex.length() == 6) {
                return 0xFF000000 | (int) Long.parseLong(hex, 16);
            }
            if (hex.length() == 8) {
                return (int) Long.parseLong(hex, 16);
            }
            throw new IllegalArgumentException("Bad hex colour: " + value);
        }
        return (int) Long.parseLong(trimmed);
    }

    private static String version() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("logvex-crystal")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }

    /** Sends a local-only message to the player's chat (never the server). */
    public void printLocal(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }

    /** Exposes the colour helper for tests. */
    static int colorOf(int r, int g, int b) {
        return ColorUtil.rgb(r, g, b);
    }
}
