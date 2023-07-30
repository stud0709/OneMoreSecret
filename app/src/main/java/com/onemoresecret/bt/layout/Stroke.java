package com.onemoresecret.bt.layout;

import com.onemoresecret.bt.KeyboardReport;

import java.util.ArrayList;
import java.util.List;

public class Stroke {
    private final List<KeyboardReport> reports = new ArrayList<>();
    private final List<Boolean> ucaseFlags = new ArrayList<>();

    /**
     * Get resulting sequence of KeyboardReports
     * @return key stroke sequence
     */
    public List<KeyboardReport> get() {
        List<KeyboardReport> list = new ArrayList<>(reports);
        list.add(new KeyboardReport(KeyboardUsage.KBD_NONE)); //release all keys
        return list;
    }

    /**
     * Clear all modifiers
     */
    public Stroke clear() {
        reports.add(new KeyboardReport(KeyboardUsage.KBD_NONE));
        return this;
    }

    /**
     * Add modifiers one by one emulating user input
     */
    public Stroke press(int... modifiers) {
        var m = 0;

        if (!reports.isEmpty()) {
            m = reports.get(reports.size() - 1).getModifiers();
        }

        for (int i : modifiers) {
            m |= i;
            reports.add(new KeyboardReport(m, KeyboardUsage.KBD_NONE));
        }

        return this;
    }

    /**
     * Remove modifiers one by one emulating user input
     */
    public Stroke release(int... modifiers) {
        var m = 0;

        if (!reports.isEmpty()) {
            m = reports.get(reports.size() - 1).getModifiers();
        }

        for (int i : modifiers) {
            m &= ~i;
            reports.add(new KeyboardReport(m, KeyboardUsage.KBD_NONE));
        }

        return this;
    }

    /**
     * Type (press + release) retaining modifiers.
     * @param usages one or more keys to type
     * @param upperCase upper case flag ("press SHIFT here")
     */
    public Stroke type(boolean upperCase, int... usages) {
        var m = 0;

        if (!reports.isEmpty()) {
            m = reports.get(reports.size() - 1).getModifiers();
        }

        for(int i : usages) {
            reports.add(new KeyboardReport(m, i)); //press
            ucaseFlags.add(upperCase);
            reports.add(new KeyboardReport(m, 0)); //release
            ucaseFlags.add(false);
        }

        return this;
    }

    /**
     * Type without setting the upper case flag. This will result in default upper case logic .
     * @param usages one or more keys to type
     * @see Stroke#toUpper()
     */

    public Stroke type(int... usages) {
        return type(false, usages);
    }

    /**
     * Convert this {@link Stroke} to upper case. If there is only one type, it will be set to upper case regardless of the upperCase flag of this element.
     */
    public Stroke toUpper() {
        var cnt = reports.stream().filter(r -> r.getKey() != 0).count();

        var upperCaseStroke = new Stroke();
        for(int i = 0; i < reports.size(); i++) {
            var upperCase = ucaseFlags.get(i) || (i==0 && cnt == 1);

            if(upperCase) {
                upperCaseStroke.reports.add(reports.get(i).addModifier(KeyboardReport.LEFT_SHIFT));
            } else {
                //copy without changes
                upperCaseStroke.reports.add(reports.get(i));
            }
        }

        return upperCaseStroke;
    }
}
