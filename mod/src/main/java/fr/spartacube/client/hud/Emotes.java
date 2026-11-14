package fr.spartacube.client.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Émojis du chat : « :eyes: » devient 👀. Images : Twemoji (CC-BY 4.0), voir textures/emoji/CREDITS.txt. */
public final class Emotes {
    public static final Map<String, String> ALL = new LinkedHashMap<>();
    private static final Pattern CODE = Pattern.compile(":([a-z0-9_]{1,24}):");

    private static void put(String name, int codePoint) {
        ALL.put(name, new String(Character.toChars(codePoint)));
    }

    static {
        put("smile", 0x1F604);
        put("grin", 0x1F601);
        put("joy", 0x1F602);
        put("rofl", 0x1F923);
        put("wink", 0x1F609);
        put("heart_eyes", 0x1F60D);
        put("kiss", 0x1F618);
        put("thinking", 0x1F914);
        put("sunglasses", 0x1F60E);
        put("cry", 0x1F622);
        put("sob", 0x1F62D);
        put("angry", 0x1F620);
        put("rage", 0x1F621);
        put("scream", 0x1F631);
        put("sleeping", 0x1F634);
        put("skull", 0x1F480);
        put("eyes", 0x1F440);
        put("thumbsup", 0x1F44D);
        put("thumbsdown", 0x1F44E);
        put("clap", 0x1F44F);
        put("wave", 0x1F44B);
        put("pray", 0x1F64F);
        put("muscle", 0x1F4AA);
        put("ok_hand", 0x1F44C);
        put("heart", 0x2764);
        put("broken_heart", 0x1F494);
        put("fire", 0x1F525);
        put("star", 0x2B50);
        put("sparkles", 0x2728);
        put("100", 0x1F4AF);
        put("tada", 0x1F389);
        put("gift", 0x1F381);
        put("crown", 0x1F451);
        put("gem", 0x1F48E);
        put("moneybag", 0x1F4B0);
        put("swords", 0x2694);
        put("shield", 0x1F6E1);
        put("bow", 0x1F3F9);
        put("pickaxe", 0x26CF);
        put("bomb", 0x1F4A3);
        put("trophy", 0x1F3C6);
        put("warning", 0x26A0);
        put("check", 0x2705);
        put("x", 0x274C);
        put("question", 0x2753);
        put("exclamation", 0x2757);
        put("zzz", 0x1F4A4);
        put("poop", 0x1F4A9);
        put("ghost", 0x1F47B);
        put("alien", 0x1F47D);
        put("robot", 0x1F916);
        put("cat", 0x1F431);
        put("dog", 0x1F436);
        put("pig", 0x1F437);
        put("cow", 0x1F42E);
        put("chicken", 0x1F414);
        put("wolf", 0x1F43A);
        put("bee", 0x1F41D);
        put("spider", 0x1F577);
        put("dragon", 0x1F409);
        put("tree", 0x1F333);
        put("sun", 0x2600);
        put("moon", 0x1F319);
        put("cloud", 0x2601);
        put("zap", 0x26A1);
        put("snowflake", 0x2744);
        put("rainbow", 0x1F308);
        put("pizza", 0x1F355);
        put("apple", 0x1F34E);
        put("cake", 0x1F382);
        put("beer", 0x1F37A);
        put("rocket", 0x1F680);
        put("bell", 0x1F514);
        put("key", 0x1F511);
        put("lock", 0x1F512);
    }

    private Emotes() {}

    /** Remplace tous les « :nom: » connus par leur émoji. */
    public static String replace(String text) {
        if (text == null || text.indexOf(':') < 0) return text;
        Matcher mt = CODE.matcher(text);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        boolean changed = false;
        while (mt.find()) {
            String emoji = ALL.get(mt.group(1));
            if (emoji == null) continue;
            sb.append(text, last, mt.start()).append(emoji);
            last = mt.end();
            changed = true;
        }
        if (!changed) return text;
        return sb.append(text, last, text.length()).toString();
    }

    /** Noms qui commencent par (puis qui contiennent) le début tapé. */
    public static List<String> suggest(String prefix, int max) {
        List<String> out = new ArrayList<>();
        for (String n : ALL.keySet()) if (n.startsWith(prefix)) { out.add(n); if (out.size() >= max) return out; }
        for (String n : ALL.keySet()) if (!n.startsWith(prefix) && n.contains(prefix)) { out.add(n); if (out.size() >= max) return out; }
        return out;
    }
}
