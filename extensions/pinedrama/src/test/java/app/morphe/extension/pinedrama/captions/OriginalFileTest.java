/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.pinedrama.captions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.pinedrama.settings.Settings;
import app.morphe.extension.shared.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Issue #8: in another app language a drama can play from a copy with the dub mixed in, which
 * nothing marks as dubbed. Force original dub hands the player PineDrama's original file instead.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class OriginalFileTest {
    /** The feed model's address, with the getters the reader looks for. */
    public static final class Address {
        private final String uri;
        private final List<String> urlList;
        private String urlKey;
        Address(String uri, String... urls) { this.uri = uri; this.urlList = List.of(urls); }
        public String getUri() { return uri; }
        public List<String> getUrlList() { return urlList; }
        public String getUrlKey() { return urlKey; }
        public String getFileHash() { return "hash-" + uri; }
        public int getWidth() { return 540; }
        public int getHeight() { return 960; }
        public long getSize() { return 1234L; }
    }

    public static final class Gear {
        public final Address playAddr;
        Gear(Address address) { playAddr = address; }
    }

    /** The feed model's video. */
    public static final class Video {
        final List<Gear> gears = new ArrayList<>();
        Address playAddr;
        public Address downloadNoWatermarkAddr;
        public Address downloadAddr;
        boolean dash;
        public List<Gear> getRawBitRate() { return gears; }
        public Address getPlayAddr() { return playAddr; }
        public boolean hasDashBitrate() { return dash; }
    }

    /** The player kit's address, filled through setters the way PineDrama fills its own. */
    public static final class PlayerAddress {
        String uri, urlKey, fileHash, sourceId, format;
        List<?> urlList, bitRate;
        int width, height;
        long size;
        double duration;
        public PlayerAddress() {}
        public void setUri(String value) { uri = value; }
        public void setUrlKey(String value) { urlKey = value; }
        public void setUrlList(List<?> value) { urlList = value; }
        public void setFileHash(String value) { fileHash = value; }
        public void setWidth(int value) { width = value; }
        public void setHeight(int value) { height = value; }
        public void setSize(long value) { size = value; }
        public PlayerAddress setSourceId(String value) { sourceId = value; return this; }
        public String getSourceId() { return sourceId; }
        public void setDuration(double value) { duration = value; }
        public double getDuration() { return duration; }
        public void setFormat(String value) { format = value; }
        public void setBitRate(List<?> value) { bitRate = value; }
    }

    /** The player kit's video. */
    public static final class PlayerVideo {
        List<?> bitRate = List.of("dubbed gear");
        List<?> audioBitRate = List.of("audio");
        String videoModelStr = "{model}";
        Map<?, ?> videoModelMap = Map.of("k", "v");
        Object videoModel = new Object();
        String format = "mp4";
        PlayerAddress playAddr, playAddrH264, playAddrBytevc1;
        public void setBitRate(List<?> value) { bitRate = value; }
        public void setAudioBitRate(List<?> value) { audioBitRate = value; }
        public void setVideoModelStr(String value) { videoModelStr = value; }
        public void setVideoModelMap(Map<?, ?> value) { videoModelMap = value; }
        public void setVideoModel(Object value) { videoModel = value; }
        public void setFormat(String value) { format = value; }
        public void setPlayAddr(PlayerAddress value) { playAddr = value; }
        public void setPlayAddrH264(PlayerAddress value) { playAddrH264 = value; }
        public void setPlayAddrBytevc1(PlayerAddress value) { playAddrBytevc1 = value; }
        public PlayerAddress getPlayAddr() { return playAddr; }
    }

    private static Video localized() {
        Video video = new Video();
        video.gears.add(new Gear(new Address("v1c836g5000cdubbed", "https://example.com/dubbed")));
        video.playAddr = video.gears.get(0).playAddr;
        video.downloadNoWatermarkAddr = new Address("v1c025g50000source", "https://example.com/clean");
        video.downloadAddr = new Address("v1c025g50000source", "https://example.com/stamped");
        return video;
    }

    private static PlayerVideo player() {
        PlayerVideo player = new PlayerVideo();
        PlayerAddress dubbed = new PlayerAddress();
        dubbed.setUri("v1c836g5000cdubbed");
        dubbed.setSourceId("v1c836g5000cdubbed");
        dubbed.setDuration(128.5);
        player.playAddr = dubbed;
        player.playAddrH264 = dubbed;
        player.playAddrBytevc1 = dubbed;
        return player;
    }

    @Before
    public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.FORCE_ORIGINAL_DUB.save(false);
    }

    @After
    public void tearDown() {
        Settings.FORCE_ORIGINAL_DUB.resetToDefault();
    }

    @Test
    public void forceOriginalDubPlaysTheOriginalFileInPlaceOfALocalizedCopy() {
        Settings.FORCE_ORIGINAL_DUB.save(true);
        PlayerVideo player = player();
        TrackPreference.playOriginal(localized(), player);

        PlayerAddress address = player.playAddr;
        assertEquals("v1c025g50000source", address.uri);
        assertEquals(List.of("https://example.com/clean"), address.urlList);
        assertEquals("the uri keys the cache when the file has no key", "v1c025g50000source", address.urlKey);
        assertEquals("hash-v1c025g50000source", address.fileHash);
        assertEquals(540, address.width);
        assertEquals(960, address.height);
        assertEquals(1234L, address.size);
        assertEquals("v1c836g5000cdubbed", address.sourceId);
        assertEquals(128.5, address.duration, 0);
        assertEquals("mp4", address.format);
        assertTrue(address.bitRate.isEmpty());

        assertSame(address, player.playAddrH264);
        assertNull("no dubbed rendition is left to pick", player.playAddrBytevc1);
        assertTrue(player.bitRate.isEmpty());
        assertTrue(player.audioBitRate.isEmpty());
        assertNull(player.videoModelStr);
        assertNull(player.videoModelMap);
        assertNull(player.videoModel);
        assertEquals("mp4", player.format);
    }

    @Test
    public void aCopyWithoutRenditionsIsComparedByItsPlayAddress() {
        Settings.FORCE_ORIGINAL_DUB.save(true);
        Video video = localized();
        video.gears.clear();
        video.downloadNoWatermarkAddr = null;
        PlayerVideo player = player();
        TrackPreference.playOriginal(video, player);
        assertEquals("the stamped copy has the same sound", List.of("https://example.com/stamped"), player.playAddr.urlList);
    }

    @Test
    public void aLocalizedCopyPlaysAsItCameWithoutForceOriginalDub() {
        PlayerVideo player = player();
        PlayerAddress dubbed = player.playAddr;
        TrackPreference.playOriginal(localized(), player);
        assertSame(dubbed, player.playAddr);
        assertEquals(List.of("dubbed gear"), player.bitRate);
    }

    @Test
    public void pineDramasOwnFileAndSplitTracksAreLeftAlone() {
        Settings.FORCE_ORIGINAL_DUB.save(true);
        Video same = localized();
        same.downloadNoWatermarkAddr = new Address("v1c836g5000cdubbed", "https://example.com/clean");
        same.downloadAddr = null;
        PlayerVideo player = player();
        PlayerAddress dubbed = player.playAddr;
        TrackPreference.playOriginal(same, player);
        assertSame("the same file", dubbed, player.playAddr);

        Video dash = localized();
        dash.dash = true;
        TrackPreference.playOriginal(dash, player);
        assertSame("split tracks carry their own sound", dubbed, player.playAddr);
        assertEquals(List.of("dubbed gear"), player.bitRate);
    }
}
