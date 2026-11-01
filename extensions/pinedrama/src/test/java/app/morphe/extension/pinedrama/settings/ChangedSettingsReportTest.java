package app.morphe.extension.pinedrama.settings;

import static org.junit.Assert.assertEquals;

import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.pinedrama.SettingsContextRule;

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The export says which settings the reader changed. #149's reporter's export couldn't: a search
 * bar and captions were missing, and nothing in it said whether a switch was on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ChangedSettingsReportTest {
    private static final String BLOCKED_NAME = "a sound somebody blocked";

    @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();

    @Before public void setUp() {
        BaseSettings.DEBUG_LOG_FILTERS.resetToDefault();
    }

    @After public void tearDown() {
    }

    @Test public void aLongListWraps() {
        Setting<?>[] many = new Setting<?>[ChangedSettingsReport.PER_LINE + 1];
        Arrays.fill(many, Settings.HIDE_BOTTOM_SEARCH_BAR);
        Settings.HIDE_BOTTOM_SEARCH_BAR.save(true);

        List<String> lines = ChangedSettingsReport.lines(Arrays.asList(many));

        assertEquals(3, lines.size());
        assertEquals("on: hide_bottom_search_bar", lines.get(2));
    }

}
