package app.trackfit.infra.assets;

import android.content.res.AssetManager;
import app.trackfit.domain.coach.CoachDefinition;
import app.trackfit.domain.coach.CoachEngine;
import app.trackfit.domain.coach.CoachRegistry;
import app.trackfit.domain.coach.StandardCoachRules;
import app.trackfit.domain.controller.CoachController;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class CoachAssets {
    private record Phrase(String text, List<String> tags) {}

    private CoachAssets() {}

    public static CoachController.Rules load(AssetManager assets, String fallback) {
        List<CoachDefinition> definitions = new ArrayList<>();
        Map<String, Map<String, List<Phrase>>> bank = new LinkedHashMap<>();
        try {
            JSONObject catalog = new JSONObject(read(assets, "coaches/catalog.json"));
            var files = catalog.getJSONArray("files");
            for (int i = 0; i < files.length(); i++) {
                JSONObject coach = new JSONObject(read(assets, "coaches/" + files.getString(i)));
                var definition = new CoachDefinition(coach.getString("id"), coach.getString("displayName"),
                        coach.getString("characterTitle"), coach.getString("bio"), coach.getString("previewPhrase"),
                        coach.getString("avatar"), strings(coach.optJSONArray("extraRules")));
                definitions.add(definition);
                JSONObject phrases = coach.getJSONObject("phrases");
                Map<String, List<Phrase>> phrasesByKey = new LinkedHashMap<>();
                var keys = phrases.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONArray variants = phrases.getJSONArray(key);
                    List<Phrase> list = new ArrayList<>();
                    for (int j = 0; j < variants.length(); j++) {
                        JSONObject variant = variants.getJSONObject(j);
                        list.add(new Phrase(variant.getString("text"), strings(variant.optJSONArray("tags"))));
                    }
                    phrasesByKey.put(key, list);
                }
                bank.put(definition.id(), phrasesByKey);
            }
        } catch (IOException | JSONException error) {
            throw new IllegalStateException("Invalid bundled coach catalog", error);
        }
        CoachRegistry registry = new CoachRegistry(definitions, StandardCoachRules.all(), Collections.emptyMap());
        return new CoachController.Rules(registry, new CoachEngine(registry, (coach, key, tags) ->
                candidates(bank.get(coach).get(key), tags, fallback), new Random()));
    }

    private static List<String> candidates(List<Phrase> phrases, Set<String> tags, String fallback) {
        if (phrases == null || phrases.isEmpty()) return Collections.singletonList(fallback);
        List<String> exact = new ArrayList<>();
        List<String> standard = new ArrayList<>();
        for (Phrase phrase : phrases) {
            if (phrase.tags().isEmpty()) standard.add(phrase.text());
            if (!tags.isEmpty() && phrase.tags().containsAll(tags)) exact.add(phrase.text());
        }
        if (!exact.isEmpty()) return exact;
        return standard.isEmpty() ? Collections.singletonList(fallback) : standard;
    }

    private static String read(AssetManager assets, String path) throws IOException {
        try (InputStream input = assets.open(path); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> strings(JSONArray array) throws JSONException {
        if (array == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) result.add(array.getString(i));
        return result;
    }
}
