package com.onemoresecret.bt.layout;

import com.onemoresecret.bt.KeyboardReport;

import java.util.ArrayList;
import java.util.List;

public class Stroke {
    private final List<KeyboardReport> reports = new ArrayList<>();
    private final List<Boolean> ucaseFlags = new ArrayList<>();

    /**
     * Get resulting sequence of KeyboardReports
     * @return
     */
    public List<KeyboardReport> get() {
        List<KeyboardReport> list = new ArrayList<>(reports);
        list.add(new KeyboardReport(0)); //release all keys
        return list;
    }

    /**
     * Clear all modifiers
     * @return
     */
    public Stroke clear() {
        reports.add(new KeyboardReport(KeyboardUsage.KBD_NONE));
        return this;
    }

    /**
     * Add modifiers one by one emulating user input
     * @param modifiers
     * @return
     */
    public Stroke press(int... modifiers) {
        int m = 0;

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
     * @param modifiers
     * @return
     */
    public Stroke release(int... modifiers) {
        int m = 0;

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
     * @param usages
     * @param ucase upper case flag
     * @return
     */
    public Stroke type(boolean ucase, int... usages) {
        int m = 0;

        if (!reports.isEmpty()) {
            m = reports.get(reports.size() - 1).getModifiers();
        }

        for(int i : usages) {
            reports.add(new KeyboardReport(m, i)); //press
            ucaseFlags.add(ucase);
            reports.add(new KeyboardReport(m, 0)); //release
            ucaseFlags.add(false);
        }

        return this;
    }

    /**
     * Type without setting the upper case flag. This will result in upper case = true, if there is only one type element, otherwise upper case = false.
     * @param usages
     * @return
     */

    public Stroke type(int... usages) {
        return type(false, usages);
    }

    /**
     * Convert this {@link Stroke} to upper case. If there is only one type, it will be set to upper case regardless of the ucase flag of this element.
     * @return
     */
    public Stroke toUpper() {
        long cnt = reports.stream().filter(r -> r.getKey() != 0).count();

        Stroke ucaseStroke = new Stroke();
        for(int i = 0; i < reports.size(); i++) {
            boolean ucase = ucaseFlags.get(i) || (i==0 && cnt == 1);

            if(ucase) {
                ucaseStroke.reports.add(reports.get(i).addModifier(KeyboardReport.LEFT_SHIFT));
            } else {
                //copy without changes
                ucaseStroke.reports.add(reports.get(i));
            }
        }

        return ucaseStroke;
    }
}
