package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.TextSetting;
import dev.zprestige.prestige.event.TextReplaceEvent;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.RandomUtil;
import java.util.function.Predicate;

public class NameHider extends Module {
    private EnumSetting mode;
    private TextSetting customName;
    private BoolSetting randomize;
    private FloatSetting speed;
    private StopWatch rerollTimer = new StopWatch();
    private String replacementName = "";

    public NameHider() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.speed
            .visibleWhen(
                (Predicate<Float>)ignored -> this.randomize.getValue() && this.mode.getValue().equals("Random")
            );
        this.randomize.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Random"));
        this.customName.visibleWhen((Predicate<String>)ignored -> this.mode.getValue().equals("Custom"));
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (mc.player == null) {
            return;
        }

        if (this.randomize.getValue()) {
            if (this.rerollTimer.hasElapsed(this.speed.getValue())) {
                this.replacementName = this.generateName();
                this.rerollTimer.reset();
            }
        }

        if (this.mode.getValue().equals("Custom")) {
            this.replacementName = this.generateName();
        }
    }

    @Subscribe
    public void onTextReplace(TextReplaceEvent event) {
        String username = mc.getSession().getUsername();
        if (username != null && !username.isEmpty() && event.getText() != null && event.getText().contains(username)) {
            event.setTarget(mc.getSession().getUsername());
            event.setText(this.replacementName);
            event.cancel();
        }
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            this.replacementName = this.generateName();
        }
    }

    private String generateName() {
        if (this.mode.getValue().equals("Custom")) {
            return this.customName.getValue().toString();
        } else {
            StringBuilder builder = new StringBuilder();
            String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";

            for (int index = 0; index < mc.player.getDisplayName().getString().length(); index++) {
                char ch = alphabet.charAt(RandomUtil.RANDOM.nextInt(alphabet.length()));
                if (Character.isAlphabetic(ch) && RandomUtil.RANDOM.nextFloat() > 0.5F) {
                    ch = Character.toUpperCase(ch);
                }

                builder.append(ch);
            }

            return builder.toString();
        }
    }
}
