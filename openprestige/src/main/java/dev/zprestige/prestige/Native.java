package dev.zprestige.prestige;

import dev.zprestige.prestige.annotation.NativeId;
import java.util.List;

public class Native {
    public static Class callbackClass;

    @NativeId("f29b5f2e-3df2-4db8-bb6e-1c01a5f91f12")
    public static void registerModules(List modules) {
    }

    @NativeId("6937e6e7-60ef-45de-98e1-f177ec613e99")
    public static void fetchString(int id, StringBuilder out) {
    }

    @NativeId("c4f1db16-7850-44eb-8f8a-863dfe33ab26")
    public static void fetchStatus(StringBuilder out) {
    }

    @NativeId("774de945-aba3-4038-b5ae-d8ff48635feb")
    public static void request(String key, String value, boolean flag, String extra, StringBuilder out, String context) {
    }

    @NativeId("b6356619-bb5a-4616-a076-93f620a7d9de")
    public static void fetchText(int id, StringBuilder out) {
    }

    @NativeId("9ae73026-310d-4cbc-b283-12f3f530fe67")
    public static void fetchField(int id, String key, StringBuilder out) {
    }

    @NativeId("08dcf01c-9943-4f66-8dfd-120714d7abdd")
    public static void submit(String message) {
    }

    @NativeId("018b2f19-e79e-7d6a-a56d-29feb6211b04")
    public static void onClientTick() {
    }

    @NativeId("70d16b96-40d4-4e4b-8d11-111b87ee88c2")
    public static void toggleMenu() {
    }

    @NativeId("0dfaa780-8d92-4e8f-aa2e-9dae747c4dd6")
    public static boolean isMenuOpen() {
        return false;
    }
}
