package com.autosell;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Auction Helper auto-sell and search system for SpookyTime.
 * Supports vanilla items and custom server items (Крушитель set, custom enchants, original tags).
 */
public class AuctionHelper {

    private enum SellState {
        IDLE,
        WAITING_AH_OPEN,
        READING_PRICES,
        NEXT_PAGE,
        SEND
    }

    private static SellState sellState = SellState.IDLE;
    private static String savedItemName = "";
    private static int savedItemCount = 1;
    private static long savedSellPrice = 0;
    private static int waitTicks = 0;
    private static List<String> handEnchants = new ArrayList<>();
    private static ItemEnchantmentsComponent handEnchantComp = null;
    private static float handDurabilityPercent = -1f;
    private static final float DURABILITY_TOLERANCE = 0.15f;
    private static Item savedItem = null;
    private static boolean savedIsOriginal = false;
    private static int nextPageSlot = -1;
    private static int pagesScanned = 0;

    public static boolean isRunning() {
        return sellState != SellState.IDLE;
    }

    public static void stop() {
        if (sellState != SellState.IDLE) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.currentScreen instanceof GenericContainerScreen) {
                mc.player.closeHandledScreen();
            }
            resetState();
            actionbar("§c[AH] Авто-продажа отменена");
        }
    }

    public static void performSearch() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        ItemStack hand = mc.player.getMainHandStack();
        if (hand.isEmpty()) {
            actionbar("§c[AH] Возьми предмет в руку!");
            return;
        }

        String name = cleanItemName(hand.getName().getString());
        if (name.isEmpty()) return;

        mc.getNetworkHandler().sendChatCommand("ah search " + name);
        actionbar("§a[AH] Поиск: " + name);
    }

    public static void startSell() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (sellState != SellState.IDLE) return;

        ItemStack hand = mc.player.getMainHandStack();
        if (hand.isEmpty()) {
            actionbar("§c[AH] Возьми предмет в руку!");
            return;
        }

        savedItemName = cleanItemName(hand.getName().getString());
        savedItemCount = hand.getCount();
        savedItem = hand.getItem();
        savedIsOriginal = isOriginalItem(hand);
        handEnchants = getEnchantNames(hand);

        ItemEnchantmentsComponent comp = hand.get(DataComponentTypes.ENCHANTMENTS);
        handEnchantComp = (comp != null && !comp.isEmpty()) ? comp : null;

        if (savedItemName.isEmpty()) return;

        if (hasMending(hand)) {
            handDurabilityPercent = -1f;
        } else {
            float dur = getDurabilityPercent(hand);
            handDurabilityPercent = (dur < 0.999f) ? dur : -1f;
        }

        mc.getNetworkHandler().sendChatCommand("ah search " + savedItemName);

        if (savedIsOriginal) {
            chat("§6[AH] Предмет: §e" + savedItemName + " §6[★ Оригинал]");
        } else {
            chat("§e[AH] Предмет: §f" + savedItemName);
        }

        List<String> allEnchants = new ArrayList<>();
        if (handEnchantComp != null) {
            for (RegistryEntry<Enchantment> entry : handEnchantComp.getEnchantments()) {
                allEnchants.add(getEnchantDisplayName(entry, handEnchantComp.getLevel(entry)));
            }
        }
        allEnchants.addAll(handEnchants);

        if (!allEnchants.isEmpty()) {
            chat("§e[AH] Зачарования (" + allEnchants.size() + "): §f" + String.join(", ", allEnchants));
        }
        if (handDurabilityPercent >= 0) {
            chat("§e[AH] Прочность: §f" + (int)(handDurabilityPercent * 100) + "%");
        }
        actionbar("§e[AH] Анализ цен...");

        sellState = SellState.WAITING_AH_OPEN;
        waitTicks = 0;
        pagesScanned = 0;
        nextPageSlot = -1;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || sellState == SellState.IDLE) return;

        switch (sellState) {
            case WAITING_AH_OPEN -> {
                if (++waitTicks > 40) {
                    chat("§c[AH] Аукцион не открылся (таймаут).");
                    resetState();
                    return;
                }
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    if (waitTicks < 4) return;
                    sellState = SellState.READING_PRICES;
                    waitTicks = 0;
                }
            }

            case READING_PRICES -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                    resetState();
                    return;
                }

                GenericContainerScreenHandler handler = screen.getScreenHandler();
                int containerSlots = handler.getRows() * 9;
                long cheapestPerOne = Long.MAX_VALUE;
                int found = 0;

                float closestDurDiff = Float.MAX_VALUE;
                long closestCheapest = Long.MAX_VALUE;

                for (int i = 0; i < containerSlots; i++) {
                    Slot slot = handler.getSlot(i);
                    long price = getPrice(slot);
                    if (price <= 0) continue;

                    ItemStack slotStack = slot.getStack();
                    if (savedItem != null && slotStack.getItem() != savedItem) continue;

                    // Match exact custom item names (e.g. Крушитель items)
                    String slotCleanName = cleanItemName(slotStack.getName().getString());
                    if (!savedItemName.isEmpty()) {
                        if (isKruhsitelItem(savedItemName) || isKruhsitelItem(slotCleanName)) {
                            if (!savedItemName.equalsIgnoreCase(slotCleanName)) {
                                continue;
                            }
                        }
                    }

                    // Original item match ([★] Оригинальный предмет)
                    if (savedIsOriginal != isOriginalItem(slotStack)) continue;

                    // 1. Vanilla enchantments match (Sharpness, Smite, Looting, Unbreaking, Mending, etc.)
                    if (handEnchantComp != null) {
                        ItemEnchantmentsComponent slotComp = slotStack.get(DataComponentTypes.ENCHANTMENTS);
                        if (!enchantCompMatch(handEnchantComp, slotComp)) continue;
                    }

                    // 2. Custom lore enchantments match (Детекция, Окисление, Вампиризм, Опытный, Яд, etc.)
                    if (!handEnchants.isEmpty()) {
                        try {
                            if (!enchantsMatch(handEnchants, getEnchantNames(slotStack))) continue;
                        } catch (Exception ignored) {
                            continue;
                        }
                    }

                    long perOne = price / Math.max(slotStack.getCount(), 1);

                    if (handDurabilityPercent >= 0) {
                        float slotDur = getDurabilityPercent(slotStack);
                        float diff = Math.abs(slotDur - handDurabilityPercent);

                        if (diff < closestDurDiff || (diff == closestDurDiff && perOne < closestCheapest)) {
                            closestDurDiff = diff;
                            closestCheapest = perOne;
                        }

                        if (diff > DURABILITY_TOLERANCE) continue;
                    }

                    found++;
                    if (perOne < cheapestPerOne) cheapestPerOne = perOne;
                }

                if (found == 0 && handDurabilityPercent >= 0 && closestCheapest != Long.MAX_VALUE) {
                    chat("§e[AH] Точной прочности нет, ближайшая (" + (int)(closestDurDiff * 100) + "% разница)");
                    cheapestPerOne = closestCheapest;
                    found = 1;
                }

                AutoSellConfig cfg = AutoSellConfig.INSTANCE;
                if (found == 0 || cheapestPerOne == Long.MAX_VALUE) {
                    int npSlot = cfg.autoNextPage ? findNextPageSlot(handler, containerSlots) : -1;
                    if (npSlot >= 0 && pagesScanned < cfg.maxPages) {
                        nextPageSlot = npSlot;
                        pagesScanned++;
                        sellState = SellState.NEXT_PAGE;
                        waitTicks = 0;
                        return;
                    }

                    // Fallback to configured price if no lots on auction
                    String fallbackPrice = AutoSellConfig.INSTANCE.prices.get(savedItemName.toLowerCase());
                    if (fallbackPrice != null && !fallbackPrice.isBlank()) {
                        try {
                            long fb = Long.parseLong(fallbackPrice.trim().replaceAll("[^0-9]", ""));
                            if (fb > 0) {
                                chat("§e[AH] Лотов на AH нет. Использую цену из конфига: §l$" + formatNumber(fb));
                                savedSellPrice = fb;
                                if (mc.player != null) mc.player.closeHandledScreen();
                                sellState = SellState.SEND;
                                waitTicks = 0;
                                return;
                            }
                        } catch (Exception ignored) {}
                    }

                    chat("§c[AH] Нет подходящих лотов (просмотрено страниц: " + pagesScanned + ").");
                    if (mc.player != null) mc.player.closeHandledScreen();
                    resetState();
                    return;
                }

                long totalPrice = cfg.smartRounding ? roundPrice(cheapestPerOne * savedItemCount) : (cheapestPerOne * savedItemCount);
                if (cfg.undercut > 0) {
                    totalPrice = Math.max(1, totalPrice - cfg.undercut);
                }
                savedSellPrice = totalPrice;

                chat("§a[AH] Нашёл " + found + " лотов. Продаю за §l$" + formatNumber(totalPrice));

                if (mc.player != null) mc.player.closeHandledScreen();
                sellState = SellState.SEND;
                waitTicks = 0;
            }

            case NEXT_PAGE -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                    resetState();
                    return;
                }
                if (waitTicks == 0 && mc.interactionManager != null && mc.player != null) {
                    GenericContainerScreenHandler handler = screen.getScreenHandler();
                    mc.interactionManager.clickSlot(handler.syncId, nextPageSlot, 0, SlotActionType.PICKUP, mc.player);
                }
                if (++waitTicks >= 6) {
                    sellState = SellState.READING_PRICES;
                    waitTicks = 0;
                }
            }

            case SEND -> {
                if (++waitTicks < 4) return;
                if (savedSellPrice > 0 && mc.getNetworkHandler() != null) {
                    // Apply a 10% discount to the final calculated/fallback selling price.
                    long discountedPrice = Math.max(1, Math.round(savedSellPrice * 0.90));
                    String cmd = AutoSellConfig.INSTANCE.command
                            .replace("{price}", String.valueOf(discountedPrice))
                            .replace("{count}", String.valueOf(savedItemCount))
                            .replace("{item}", savedItemName)
                            .trim();
                    if (cmd.startsWith("/")) cmd = cmd.substring(1);
                    mc.getNetworkHandler().sendChatCommand(cmd);
                }
                resetState();
            }
        }
    }

    public static void renderSlot(DrawContext context, Slot slot, int rank) {
        int fill = switch (rank) {
            case 1 -> 0x6055FF55;
            case 2 -> 0x60FFFF55;
            default -> 0x60FFAA00;
        };
        int outline = switch (rank) {
            case 1 -> 0xFF00DD00;
            case 2 -> 0xFFDDDD00;
            default -> 0xFFFF8800;
        };
        int x = slot.x, y = slot.y;
        context.fill(x, y, x + 16, y + 16, fill);
        context.fill(x, y, x + 16, y + 1, outline);
        context.fill(x, y + 15, x + 16, y + 16, outline);
        context.fill(x, y, x + 1, y + 16, outline);
        context.fill(x + 15, y, x + 16, y + 16, outline);
    }

    public static long getPrice(Slot slot) {
        if (!slot.hasStack()) return 0;
        LoreComponent lore = slot.getStack().getComponents().get(DataComponentTypes.LORE);
        if (lore == null) return 0;
        for (Text line : lore.lines()) {
            String text = line.getString();
            if (text.contains("$") || text.toLowerCase().contains("цена") || text.toLowerCase().contains("стоимость")) {
                String digits = text.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    try {
                        return Long.parseLong(digits);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return 0;
    }

    public static long roundPrice(long price) {
        if (price <= 0) return 1;

        if (price >= 1000) {
            long thousands = price / 1000;
            if (price % 1000 >= 500) {
                return thousands * 1000 + 500;
            }
            return thousands * 1000;
        }

        return (price / 10) * 10;
    }

    public static List<String> getEnchantNames(ItemStack stack) {
        List<String> result = new ArrayList<>();
        if (stack == null || stack.isEmpty()) return result;
        LoreComponent lore = stack.getComponents().get(DataComponentTypes.LORE);
        if (lore == null) return result;
        for (Text line : lore.lines()) {
            String raw = line.getString().replaceAll("§.", "").replace('\u00A0', ' ').trim();
            if (raw.isEmpty() || raw.contains("$") || raw.toLowerCase().contains("цена")
                    || raw.contains("Баланс") || raw.contains("Продавец") || raw.contains("Истекает")
                    || raw.contains("Нажмите") || raw.contains("Починка")
                    || raw.contains("★") || raw.contains("Оригинальный")
                    || raw.contains("Когда в") || raw.contains("Урон при") || raw.contains("Скорость атаки")
                    || raw.contains("Коэффициент")) continue;
            if (raw.matches(".*[IVX1-9]+$") || isKnownEnchant(raw)) {
                result.add(raw);
            }
        }
        return result;
    }

    public static boolean isOriginalItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        LoreComponent lore = stack.getComponents().get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                String s = line.getString().replaceAll("§.", "").toLowerCase();
                if (s.contains("★") || s.contains("оригинальный предмет")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isKruhsitelItem(String name) {
        if (name == null) return false;
        String l = name.toLowerCase();
        return l.contains("крушител") || l.contains("круша");
    }

    public static boolean isKnownEnchant(String s) {
        if (s == null) return false;
        return s.contains("Острота") || s.contains("Эффективность") || s.contains("Прочность")
                || s.contains("Удача") || s.contains("Добыча") || s.contains("Небесная кара")
                || s.contains("Заговор огня") || s.contains("Бич") || s.contains("Разящий")
                || s.contains("Вампиризм") || s.contains("Окисление") || s.contains("Яд")
                || s.contains("Детекция") || s.contains("Бульдозер") || s.contains("Магнит")
                || s.contains("Авто-Плавка") || s.contains("Паутина") || s.contains("Пингер")
                || s.contains("Опытный") || s.contains("Шёлковое") || s.contains("Шелковое")
                || s.contains("Подводная") || s.contains("Защита") || s.contains("Отделка")
                || s.contains("Улучшение") || s.contains("Шипы") || s.contains("Легкость")
                || s.contains("Книга")
                // Kruhsitel and SpookyTime enchants:
                || s.contains("Подрывник") || s.contains("Возвращение") || s.contains("Ступор")
                || s.contains("Скаут") || s.contains("Притяжение") || s.contains("Снайпер")
                || s.contains("Тягун") || s.contains("Верность") || s.contains("Громовержец")
                || s.contains("Пронзание") || s.contains("Акробатика") || s.contains("Взрывоустойчивость")
                || s.contains("Огнеупорность") || s.contains("Невесомость") || s.contains("Тайфун")
                || s.contains("Левитация") || s.contains("Замедление") || s.contains("Ослепление")
                || s.contains("Иссушение") || s.contains("Мрак") || s.contains("Крушитель")
                || s.contains("Каратель") || s.contains("Головорез") || s.contains("Берсерк");
    }

    private static boolean enchantCompMatch(ItemEnchantmentsComponent hand, ItemEnchantmentsComponent slot) {
        if (slot == null || slot.isEmpty()) return false;
        for (RegistryEntry<Enchantment> entry : hand.getEnchantments()) {
            if (hand.getLevel(entry) != slot.getLevel(entry)) return false;
        }
        if (slot.getEnchantments().size() != hand.getEnchantments().size()) {
            return false;
        }
        return true;
    }

    private static boolean enchantsMatch(List<String> hand, List<String> slot) {
        if (hand.isEmpty()) return true;
        if (slot.isEmpty()) return false;
        for (String he : hand) {
            String base = extractBase(he);
            int lvl = extractLevel(he);
            boolean found = false;
            for (String se : slot) {
                if (extractBase(se).contains(base) || base.contains(extractBase(se))) {
                    if (extractLevel(se) == lvl) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static String extractBase(String s) {
        return s.replaceAll("\\s+[IVX0-9]+$", "").trim();
    }

    private static int extractLevel(String s) {
        String trimmed = s.trim();
        int sp = trimmed.lastIndexOf(' ');
        if (sp == -1) return 1;
        String suf = trimmed.substring(sp + 1);
        int r = parseRoman(suf);
        if (r > 0) return r;
        try {
            return Integer.parseInt(suf);
        } catch (NumberFormatException ignored) {}
        return 1;
    }

    private static int parseRoman(String r) {
        return switch (r) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            case "VI" -> 6;
            case "VII" -> 7;
            case "VIII" -> 8;
            case "IX" -> 9;
            case "X" -> 10;
            case "XI" -> 11;
            case "XII" -> 12;
            case "XIII" -> 13;
            case "XIV" -> 14;
            case "XV" -> 15;
            default -> 0;
        };
    }

    private static String getEnchantDisplayName(RegistryEntry<Enchantment> entry, int level) {
        String id = entry.getIdAsString();
        String name = switch (id) {
            case "minecraft:sharpness" -> "Острота";
            case "minecraft:smite" -> "Небесная кара";
            case "minecraft:bane_of_arthropods" -> "Бич членистоногих";
            case "minecraft:sweeping_edge" -> "Разящий клинок";
            case "minecraft:fire_aspect" -> "Заговор огня";
            case "minecraft:looting" -> "Добыча";
            case "minecraft:unbreaking" -> "Прочность";
            case "minecraft:mending" -> "Починка";
            case "minecraft:protection" -> "Защита";
            case "minecraft:fire_protection" -> "Огнеупорность";
            case "minecraft:feather_falling" -> "Невесомость";
            case "minecraft:blast_protection" -> "Взрывоустойчивость";
            case "minecraft:projectile_protection" -> "Защита от снарядов";
            case "minecraft:respiration" -> "Подводное дыхание";
            case "minecraft:aqua_affinity" -> "Подводник";
            case "minecraft:thorns" -> "Шипы";
            case "minecraft:depth_strider" -> "Подводная ходьба";
            case "minecraft:frost_walker" -> "Ледоход";
            case "minecraft:soul_speed" -> "Скорость души";
            case "minecraft:swift_sneak" -> "Прокрадывание";
            case "minecraft:efficiency" -> "Эффективность";
            case "minecraft:silk_touch" -> "Шёлковое касание";
            case "minecraft:fortune" -> "Удача";
            case "minecraft:power" -> "Сила";
            case "minecraft:punch" -> "Откидывание";
            case "minecraft:flame" -> "Воспламенение";
            case "minecraft:infinity" -> "Бесконечность";
            case "minecraft:loyalty" -> "Верность";
            case "minecraft:impaling" -> "Пронзание";
            case "minecraft:riptide" -> "Тягун";
            case "minecraft:channeling" -> "Громовержец";
            case "minecraft:multishot" -> "Тройной выстрел";
            case "minecraft:quick_charge" -> "Быстрая перезарядка";
            case "minecraft:piercing" -> "Пронзающая стрела";
            case "minecraft:density" -> "Тяжесть";
            case "minecraft:breach" -> "Бронебойность";
            case "minecraft:wind_burst" -> "Ветровой заряд";
            default -> {
                int colon = id.indexOf(':');
                yield colon >= 0 ? id.substring(colon + 1) : id;
            }
        };
        if ("minecraft:mending".equals(id) || (level <= 1 && "minecraft:silk_touch".equals(id))) {
            return name;
        }
        return name + " " + toRoman(level);
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }

    public static String cleanItemName(String name) {
        if (name == null || name.isEmpty()) return "";
        name = name.replaceAll("§.", "").replace('\u00A0', ' ');
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '\'' || c == '_')
                sb.append(c);
        }
        String clean = sb.toString().trim()
                .replaceAll("(?i)\\b123\\b", "")
                .replaceAll("(?i)\\bxxx\\b", "")
                .replaceAll("^\\d+\\s+", "")
                .replaceAll("\\s+\\d+$", "")
                .replace("Пузырёк опыта 15 Ур", "Пузырек с уровнем 15")
                .replace("Пузырёк опыта 30 Ур", "Пузырек с уровнем 30")
                .replace("Пузырёк опыта 45 Ур", "Пузырек с уровнем 45")
                .replace("Пузырёк опыта 50 Ур", "Пузырек с уровнем 50")
                .replace("TNT - TIER WHITE", "Таер вайт")
                .replace("TNT - TIER BLACK", "Таер блэк")
                .replace("Динамит", "Динамит обычный")
                .replace("Яйцо призыва крестьянина-зомби", "яйцо зомби-крестьянина")
                .replace("Снежок заморозка", "Снежок заморозка")
                .replaceAll("\\s+", " ")
                .trim();

        // Exact normalization for Kruhsitel items
        String lower = clean.toLowerCase();
        if (lower.contains("трезубец") && lower.contains("крушит")) return "Трезубец Крушителя";
        if (lower.contains("лук") && lower.contains("крушит")) return "Лук Крушителя";
        if (lower.contains("булав") && lower.contains("крушит")) return "Булава Крушителя";
        if (lower.contains("арбалет") && lower.contains("крушит")) return "Арбалет Крушителя";
        if (lower.contains("меч") && lower.contains("крушит")) return "Меч Крушителя";
        if (lower.contains("ботинк") && lower.contains("крушит")) return "Ботинки Крушителя";
        if (lower.contains("понож") && lower.contains("крушит")) return "Поножи Крушителя";
        if (lower.contains("нагрудник") && lower.contains("крушит")) return "Нагрудник Крушителя";
        if (lower.contains("шлем") && lower.contains("крушит")) return "Шлем Крушителя";

        return clean;
    }

    public static float getDurabilityPercent(ItemStack stack) {
        Integer damage = stack.get(DataComponentTypes.DAMAGE);
        Integer maxDamage = stack.get(DataComponentTypes.MAX_DAMAGE);
        if (damage == null || maxDamage == null || maxDamage == 0) return 1.0f;
        return (float) (maxDamage - damage) / maxDamage;
    }

    public static boolean hasMending(ItemStack stack) {
        ItemEnchantmentsComponent comp = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (comp != null && !comp.isEmpty()) {
            for (RegistryEntry<Enchantment> entry : comp.getEnchantments()) {
                if (entry.getIdAsString().contains("mending")) return true;
            }
        }
        LoreComponent lore = stack.getComponents().get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                if (line.getString().contains("Починка")) return true;
            }
        }
        return false;
    }

    public static int findNextPageSlot(GenericContainerScreenHandler handler, int containerSlots) {
        int lastRowStart = containerSlots - 9;
        for (int i = lastRowStart; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.hasStack()) continue;
            String name = slot.getStack().getName().getString().replaceAll("§.", "");
            if (name.contains("Следующая страница")) return i;
        }
        return -1;
    }

    public static String formatNumber(long n) {
        return String.format("%,d", n).replace(',', '.');
    }

    public static void actionbar(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(msg), true);
        }
    }

    public static void chat(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(msg), false);
        }
    }

    private static void resetState() {
        sellState = SellState.IDLE;
        waitTicks = 0;
        savedItemName = "";
        savedItemCount = 1;
        savedSellPrice = 0;
        savedIsOriginal = false;
        handEnchants.clear();
        handEnchantComp = null;
        handDurabilityPercent = -1f;
        savedItem = null;
        nextPageSlot = -1;
        pagesScanned = 0;
    }
}
