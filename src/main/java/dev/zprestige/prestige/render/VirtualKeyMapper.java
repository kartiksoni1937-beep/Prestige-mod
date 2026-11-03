package dev.zprestige.prestige.render;

import java.util.HashMap;
import java.util.Map;

public class VirtualKeyMapper {
    private static final Map VK_TO_GLFW = new HashMap();

    private VirtualKeyMapper() {
    }

    public static int toGlfwKey(int virtualKey) {
        return !VK_TO_GLFW.containsKey(virtualKey) ? virtualKey : (Integer)VK_TO_GLFW.getOrDefault(virtualKey, virtualKey);
    }

    static {
        for (int letter = 0; letter < 26; letter++) {
            VK_TO_GLFW.put(65 + letter, 65 + letter);
        }

        for (int digit = 0; digit < 10; digit++) {
            VK_TO_GLFW.put(48 + digit, 48 + digit);
        }

        VK_TO_GLFW.put(188, 44);
        VK_TO_GLFW.put(190, 46);
        VK_TO_GLFW.put(191, 47);
        VK_TO_GLFW.put(186, 59);
        VK_TO_GLFW.put(222, 39);
        VK_TO_GLFW.put(219, 91);
        VK_TO_GLFW.put(221, 93);
        VK_TO_GLFW.put(220, 92);
        VK_TO_GLFW.put(189, 45);
        VK_TO_GLFW.put(187, 61);
        VK_TO_GLFW.put(192, 96);
        VK_TO_GLFW.put(32, 32);
        VK_TO_GLFW.put(13, 257);
        VK_TO_GLFW.put(9, 258);
        VK_TO_GLFW.put(8, 259);
        VK_TO_GLFW.put(46, 261);
        VK_TO_GLFW.put(45, 260);
        VK_TO_GLFW.put(39, 262);
        VK_TO_GLFW.put(37, 263);
        VK_TO_GLFW.put(40, 264);
        VK_TO_GLFW.put(38, 265);
        VK_TO_GLFW.put(33, 266);
        VK_TO_GLFW.put(34, 267);
        VK_TO_GLFW.put(36, 268);
        VK_TO_GLFW.put(35, 269);
        VK_TO_GLFW.put(27, 256);
        VK_TO_GLFW.put(44, 283);
        VK_TO_GLFW.put(145, 281);
        VK_TO_GLFW.put(19, 284);
        VK_TO_GLFW.put(20, 280);
        VK_TO_GLFW.put(144, 282);
        VK_TO_GLFW.put(93, 348);

        for (int functionKey = 0; functionKey < 24; functionKey++) {
            VK_TO_GLFW.put(112 + functionKey, 290 + functionKey);
        }

        VK_TO_GLFW.put(160, 340);
        VK_TO_GLFW.put(161, 344);
        VK_TO_GLFW.put(162, 341);
        VK_TO_GLFW.put(163, 345);
        VK_TO_GLFW.put(164, 342);
        VK_TO_GLFW.put(165, 346);
        VK_TO_GLFW.put(91, 343);
        VK_TO_GLFW.put(92, 347);
        VK_TO_GLFW.put(16, 340);
        VK_TO_GLFW.put(17, 341);
        VK_TO_GLFW.put(18, 342);

        for (int numpadKey = 0; numpadKey < 10; numpadKey++) {
            VK_TO_GLFW.put(96 + numpadKey, 320 + numpadKey);
        }

        VK_TO_GLFW.put(106, 332);
        VK_TO_GLFW.put(107, 334);
        VK_TO_GLFW.put(109, 333);
        VK_TO_GLFW.put(110, 330);
        VK_TO_GLFW.put(111, 331);
    }
}
