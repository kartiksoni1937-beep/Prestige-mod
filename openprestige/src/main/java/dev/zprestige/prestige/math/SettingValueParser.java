package dev.zprestige.prestige.math;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.KeybindSetting;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Setting;
import dev.zprestige.prestige.TextSetting;
import java.awt.Color;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class SettingValueParser {
    private SettingValueParser() {
    }

    public static void applyValue(Setting setting, Object value) {
        if (setting == null) {
            throw new IllegalArgumentException("setting is missing");
        } else if (value == null) {
            throw new IllegalArgumentException("value is null");
        } else {
            String text = value.toString();
            if (setting instanceof BoolSetting boolSetting) {
                boolSetting.setValue(parseBoolean(text));
            } else if (setting instanceof KeybindSetting keybindSetting) {
                String[] keyParts = text.split("\\.", -1);
                if (keyParts.length != 2) {
                    throw new IllegalArgumentException("expected key.hold");
                } else {
                    int key = parseInt(keyParts[0], "key");
                    boolean holdMode = parseBoolean(keyParts[1]);
                    if (key >= -1 && (key <= 348 || key >= 9995 && key <= 9999)) {
                        keybindSetting.setValue(key);
                        keybindSetting.setHoldMode(holdMode);
                    } else {
                        throw new IllegalArgumentException("key is outside the supported bind range");
                    }
                }
            } else if (setting instanceof ColorSetting colorSetting) {
                String[] rgba = text.split("\\.", -1);
                if (rgba.length != 4) {
                    throw new IllegalArgumentException("expected red.green.blue.alpha");
                } else {
                    colorSetting.setValue(new Color(parseColorChannel(rgba[0], "red"), parseColorChannel(rgba[1], "green"), parseColorChannel(rgba[2], "blue"), parseColorChannel(rgba[3], "alpha")));
                }
            } else if (setting instanceof FloatSetting floatSetting) {
                floatSetting.setValue(Math.clamp(parseFloat(text), floatSetting.getMin(), floatSetting.getMax()));
            } else if (setting instanceof IntSetting intSetting) {
                intSetting.setValue(Math.clamp((long)parseInt(text, "integer"), intSetting.getMin(), intSetting.getMax()));
            } else if (setting instanceof EnumSetting enumSetting) {
                if (Arrays.stream(enumSetting.getOptions()).noneMatch(text::equals)) {
                    throw new IllegalArgumentException("unknown mode '" + truncate(text, 32) + "'");
                } else {
                    enumSetting.setValue(text);
                }
            } else if (!(setting instanceof MultiSetting multiSetting)) {
                if (setting instanceof RangeSetting rangeSetting) {
                    String[] bounds = text.split("\\|", -1);
                    if (bounds.length != 2) {
                        throw new IllegalArgumentException("expected minimum|maximum");
                    } else {
                        float rawMin = parseFloat(bounds[0]);
                        float rawMax = parseFloat(bounds[1]);
                        float min = Math.clamp(rawMin, rangeSetting.getMin(), rangeSetting.getMax());
                        float max = Math.clamp(rawMax, rangeSetting.getMin(), rangeSetting.getMax());
                        if (min > max) {
                            throw new IllegalArgumentException("minimum is greater than maximum");
                        } else {
                            float rolled = rangeSetting.getRolled();
                            float clampedRolled = Float.isFinite(rolled) ? Math.clamp(rolled, min, max) : min;
                            rangeSetting.setLow(min);
                            rangeSetting.setHigh(max);
                            rangeSetting.setRolled(clampedRolled);
                        }
                    }
                } else if (setting instanceof TextSetting textSetting) {
                    textSetting.setValue(text);
                } else {
                    throw new IllegalArgumentException("unsupported setting type " + setting.getClass().getSimpleName());
                }
            } else {
                Map selections = parseMultiSelection(multiSetting, text);

                for (Entry entry : (Iterable<Entry>)(selections.entrySet())) {
                    multiSetting.setSelected((String)entry.getKey(), (Boolean)entry.getValue());
                }
            }
        }
    }

    public static boolean parseBoolean(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("value is null");
        } else {
            String text = value.toString().trim();
            if ("true".equalsIgnoreCase(text)) {
                return true;
            } else if ("false".equalsIgnoreCase(text)) {
                return false;
            } else {
                throw new IllegalArgumentException("expected true or false");
            }
        }
    }

    public static float parseFloat(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("value is null");
        } else {
            float parsed;
            try {
                parsed = Float.parseFloat(value.toString().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("expected a number", exception);
            }

            if (!Float.isFinite(parsed)) {
                throw new IllegalArgumentException("number must be finite");
            } else {
                return parsed;
            }
        }
    }

    private static Map parseMultiSelection(MultiSetting setting, String text) {
        LinkedHashMap selections = new LinkedHashMap();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("mode list is empty");
        } else {
            for (String token : text.split("&", -1)) {
                if (!token.isEmpty()) {
                    int dotIndex = token.lastIndexOf(46);
                    if (dotIndex <= 0 || dotIndex == token.length() - 1) {
                        throw new IllegalArgumentException("expected option.true or option.false");
                    }

                    String option = token.substring(0, dotIndex);
                    if (!Arrays.stream(setting.getOptions()).anyMatch(option::equals)) {
                        throw new IllegalArgumentException("unknown option '" + truncate(option, 32) + "'");
                    }

                    if (selections.containsKey(option)) {
                        throw new IllegalArgumentException("duplicate option '" + truncate(option, 32) + "'");
                    }

                    selections.put(option, parseBoolean(token.substring(dotIndex + 1)));
                }
            }

            for (String requiredOption : setting.getOptions()) {
                if (!requiredOption.isEmpty() && !selections.containsKey(requiredOption)) {
                    throw new IllegalArgumentException("missing option '" + truncate(requiredOption, 32) + "'");
                }
            }

            return selections;
        }
    }

    private static int parseInt(String text, String description) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("expected " + description, exception);
        }
    }

    private static int parseColorChannel(String text, String channelName) {
        int channel = parseInt(text, channelName + " channel");
        if (channel >= 0 && channel <= 255) {
            return channel;
        } else {
            throw new IllegalArgumentException(channelName + " channel must be between 0 and 255");
        }
    }

    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 3) + "...";
    }
}
