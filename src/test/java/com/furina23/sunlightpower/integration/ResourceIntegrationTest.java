package com.furina23.sunlightpower.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceIntegrationTest {
    private static final Path ROOT = Path.of("src/main/resources");

    @Test
    void modResourcesContainACompletePanelDefinition() {
        List<String> required = List.of(
                "fabric.mod.json",
                "pack.mcmeta",
                "assets/sunlight_power/blockstates/solar_panel.json",
                "assets/sunlight_power/models/block/solar_panel.json",
                "assets/sunlight_power/models/item/solar_panel.json",
                "assets/sunlight_power/textures/block/solar_panel_slab_top.png",
                "assets/sunlight_power/textures/block/solar_panel_slab_side.png",
                "assets/sunlight_power/textures/block/solar_panel_slab_bottom.png",
                "data/sunlight_power/recipes/solar_panel.json",
                "data/sunlight_power/loot_tables/blocks/solar_panel.json");
        for (String path : required) {
            assertTrue(Files.isRegularFile(ROOT.resolve(path)), "缺少资源: " + path);
        }
    }

    @Test
    void sourceDoesNotReferenceForgeEnergyApi() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = Files.readString(path);
                    assertTrue(!source.contains("net.minecraftforge") && !source.contains("net.neoforged"),
                            "发现 Forge/NeoForge 引用: " + path);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
    }

    @Test
    void bothLanguagesContainEveryPlayerVisibleTranslation() throws Exception {
        JsonObject english = language("en_us");
        JsonObject chinese = language("zh_cn");
        assertEquals(english.keySet(), chinese.keySet(), "中英文语言文件的键必须一致");

        Set<String> required = new TreeSet<>(Set.of(
                "block.sunlight_power.solar_panel", "item.sunlight_power.solar_panel"));
        Pattern translationKey = Pattern.compile("\"((?:block|item|itemGroup|tooltip|gui)\\.sunlight_power\\.[^\"]+)\"");
        for (Path root : List.of(Path.of("src/main/java"), Path.of("src/client/java"))) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                    var matcher = translationKey.matcher(Files.readString(path));
                    while (matcher.find()) required.add(matcher.group(1));
                }
            }
        }
        for (JsonObject language : List.of(english, chinese)) {
            for (String key : required) {
                assertTrue(language.has(key), "缺少翻译: " + key);
                assertTrue(language.get(key).isJsonPrimitive()
                        && language.getAsJsonPrimitive(key).isString(), "翻译必须为字符串: " + key);
                assertFalse(language.get(key).getAsString().isBlank(), "翻译不能为空: " + key);
            }
        }
    }

    @Test
    void translatedNumbersAndPercentagesHaveMatchingArguments() throws Exception {
        Map<String, Integer> argumentCounts = Map.ofEntries(
                Map.entry("tooltip.sunlight_power.generation", 1),
                Map.entry("tooltip.sunlight_power.capacity", 1),
                Map.entry("tooltip.sunlight_power.output", 1),
                Map.entry("gui.sunlight_power.stored", 1),
                Map.entry("gui.sunlight_power.stored_total", 2),
                Map.entry("gui.sunlight_power.capacity", 1),
                Map.entry("gui.sunlight_power.generation", 1),
                Map.entry("gui.sunlight_power.efficiency", 1),
                Map.entry("gui.sunlight_power.sun_intensity", 1));
        Pattern placeholders = Pattern.compile("%%|%s");
        for (String locale : List.of("en_us", "zh_cn")) {
            JsonObject language = language(locale);
            for (var entry : argumentCounts.entrySet()) {
                String translation = language.get(entry.getKey()).getAsString();
                long count = placeholders.matcher(translation).results()
                        .filter(match -> match.group().equals("%s")).count();
                assertEquals(entry.getValue().longValue(), count, locale + ": " + entry.getKey());
                String formatted = assertDoesNotThrow(() -> String.format(Locale.ROOT, translation, 123456, 654321));
                assertTrue(formatted.contains("123456"), locale + ": " + entry.getKey());
                if (entry.getValue() == 2) assertTrue(formatted.contains("654321"));
            }
        }
        JsonObject english = language("en_us");
        JsonObject chinese = language("zh_cn");
        for (String key : List.of("tooltip.sunlight_power.generation", "tooltip.sunlight_power.capacity",
                "tooltip.sunlight_power.output")) {
            assertNotEquals(english.get(key).getAsString(), chinese.get(key).getAsString(), key);
        }
    }

    private static JsonObject language(String locale) throws Exception {
        return JsonParser.parseString(Files.readString(
                ROOT.resolve("assets/sunlight_power/lang/" + locale + ".json"))).getAsJsonObject();
    }
}
