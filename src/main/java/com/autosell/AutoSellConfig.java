package com.autosell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class AutoSellConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("autosell.json");

    public static AutoSellConfig INSTANCE = new AutoSellConfig();

    /** Sell mode: "MARKET" (Auction analysis) or "HOTBAR" (legacy hotbar item selling). */
    public String mode = "MARKET";

    /** Command sent to the server for sell, without the leading slash. */
    public String command = "ah sell {price}";

    /** Pause between sells in ticks for hotbar mode. */
    public int delayTicks = 6;

    /** Automatically turn pages if matching lot not found on current page. */
    public boolean autoNextPage = true;

    /** Maximum pages to scan in AH. */
    public int maxPages = 50;

    /** Smart rounding of prices (e.g. nearest 500/1000). */
    public boolean smartRounding = true;

    /** Undercut amount (0 = match lowest, >0 = decrease price by this amount). */
    public long undercut = 0;

    /** Highlight top 3 cheapest lots in AH GUI (green, yellow, orange). */
    public boolean highlightCheapest = true;

    /** Auto Resell: automatically periodically relist expired items from AH storage. */
    public boolean autoResell = false;

    /** FastExp: repeatedly use XP bottles while enabled. */
    public boolean fastExpEnabled = false;

    /** Interval for Auto Resell in seconds (default 60s). */
    public int autoResellInterval = 60;

    /** Info bar (name, ping, FPS) in the corner of the screen. */
    public boolean hudEnabled = true;

    /** Green ESP outline boxes around other players, including through walls. */
    public boolean playerEspEnabled = true;

    /** Name shown in the HUD. Leave empty to show real Minecraft nickname. */
    public String hudName = "darknight boss";
    public int hudX = 4;
    public int hudY = 4;

    /** Fallback / Hotbar prices: item id -> price. */
    public Map<String, String> prices = new LinkedHashMap<>();

    public static void load() {
        if (Files.exists(PATH)) {
            try (Reader r = Files.newBufferedReader(PATH)) {
                AutoSellConfig c = GSON.fromJson(r, AutoSellConfig.class);
                if (c != null) {
                    if (c.mode == null) c.mode = "MARKET";
                    if (c.command == null) c.command = "ah sell {price}";
                    if (c.delayTicks < 1) c.delayTicks = 6;
                    if (c.maxPages < 1) c.maxPages = 50;
                    if (c.autoResellInterval < 5) c.autoResellInterval = 60;
                    if (c.hudName == null) c.hudName = "darknight boss";
                    if (c.prices == null) c.prices = new LinkedHashMap<>();
                    ensureDefaults(c.prices);
                    INSTANCE = c;
                }
            } catch (Exception e) {
                System.err.println("[AutoSell] Failed to read config: " + e);
            }
        } else {
            ensureDefaults(INSTANCE.prices);
            save();
        }
    }

    private static void ensureDefaults(Map<String, String> p) {
        p.putIfAbsent("трезубец крушителя", "5790");
        p.putIfAbsent("лук крушителя", "4790");
        p.putIfAbsent("булава крушителя", "5790");
        p.putIfAbsent("арбалет крушителя", "5000");
        p.putIfAbsent("меч крушителя", "6000");
        p.putIfAbsent("ботинки крушителя", "4000");
        p.putIfAbsent("поножи крушителя", "4500");
        p.putIfAbsent("нагрудник крушителя", "5000");
        p.putIfAbsent("шлем крушителя", "4000");
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(PATH)) {
            GSON.toJson(INSTANCE, w);
        } catch (IOException e) {
            System.err.println("[AutoSell] Failed to save config: " + e);
        }
    }
}
