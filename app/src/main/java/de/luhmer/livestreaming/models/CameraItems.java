package de.luhmer.livestreaming.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for providing sample content for user interfaces created by
 * Android template wizards.
 */
public class CameraItems {

    /**
     * An array of CameraItems.
     */
    public static final List<CameraItem> ITEMS = new ArrayList<>();

    /**
     * A dummy item representing a piece of content.
     */
    public static class CameraItem {
        public final String id;
        public final String ip;
        public final Boolean isReachable;
        public CameraItem(String id, String ip, boolean isReachable) {
            this.id = id;
            this.ip = ip;
            this.isReachable = isReachable;
        }

        @Override
        public String toString() {
            return ip;
        }
    }
}
