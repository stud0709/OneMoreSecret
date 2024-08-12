package com.onemoresecret.bt.layout;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.onemoresecret.bt.KeyModifier;
import com.onemoresecret.bt.KeyboardReport;
import com.onemoresecret.bt.KeyboardUsage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonSerialize(using = StrokeSerializer.class)
public class Stroke {
    private final List<KeyboardReport> reports = new ArrayList<>();
    protected final Set<KeyModifier> modifiers = new HashSet<>();

    /**
     * Get resulting sequence of KeyboardReports
     *
     * @return key stroke sequence
     */
    public List<KeyboardReport> get() {
        List<KeyboardReport> list = new ArrayList<>(reports);
        if (!modifiers.isEmpty())
            list.add(new KeyboardReport(KeyboardUsage.KBD_NONE)); //release all keys, reset all modifiers
        return list;
    }

    /**
     * Clear all modifiers
     */
    public Stroke clear() {
        modifiers.clear();
        return this;
    }

    /**
     * Add modifiers one by one emulating user input
     */
    public Stroke press(KeyModifier... modifiers) {
        this.modifiers.addAll(Arrays.asList(modifiers));
        return this;
    }

    /**
     * Remove modifiers one by one emulating user input
     */
    public Stroke release(KeyModifier... modifiers) {
        Arrays.asList(modifiers).forEach(this.modifiers::remove);
        return this;
    }

    public Stroke type(KeyboardUsage... usages) {
        for (var u : usages) {
            var mArr = this.modifiers.toArray(new KeyModifier[]{});
            reports.add(new KeyboardReport(u, mArr)); //press
            reports.add(new KeyboardReport(KeyboardUsage.KBD_NONE, mArr)); //release
        }
        return this;
    }

    /**
     * Convert this {@link Stroke} to upper case. This method handles only simple cases like a -> A. For more
     * complex keystrokes, a separate {@link Stroke} definition should be created.
     */
    public Stroke toUpper() {
        if (reports.stream().filter(r -> r.getUsage() != KeyboardUsage.KBD_NONE).count() != 1) {
            return null; //create a dedicated Stroke instead!
        }

        var upperCaseStroke = new Stroke();

        var shift = KeyModifier.NONE;
        for (int i = 0; i < reports.size(); i++) {
            var kr = reports.get(i);
            upperCaseStroke.modifiers.addAll(kr.getModifiers());
            upperCaseStroke.modifiers.add(shift);

            if (kr.getUsage() != KeyboardUsage.KBD_NONE) {
                //we are typing smth., press SHIFT here
                upperCaseStroke.press(KeyModifier.LEFT_SHIFT);
                upperCaseStroke.type(kr.getUsage());
                shift = KeyModifier.LEFT_SHIFT;
            }
        }

        return upperCaseStroke;
    }
}
