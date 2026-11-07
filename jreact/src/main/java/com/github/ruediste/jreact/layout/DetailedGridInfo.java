package com.github.ruediste.jreact.layout;

import java.util.ArrayList;
import java.util.List;

/** Detailed information about a computed grid layout. */
public final class DetailedGridInfo {

    public static final class TrackInfo {
        public int negativeImplicitTracks;
        public int explicitTracks;
        public int positiveImplicitTracks;
        public final List<java.lang.Float> gutters = new ArrayList<>();
        public final List<java.lang.Float> sizes = new ArrayList<>();
        public final List<Line<java.lang.Float>> positions = new ArrayList<>();
    }

    public static final class ItemInfo {
        public int rowStart;
        public int rowEnd;
        public int columnStart;
        public int columnEnd;

        public ItemInfo(int rowStart, int rowEnd, int columnStart, int columnEnd) {
            this.rowStart = rowStart;
            this.rowEnd = rowEnd;
            this.columnStart = columnStart;
            this.columnEnd = columnEnd;
        }
    }

    public final TrackInfo rows = new TrackInfo();
    public final TrackInfo columns = new TrackInfo();
    public final List<ItemInfo> items = new ArrayList<>();
}
