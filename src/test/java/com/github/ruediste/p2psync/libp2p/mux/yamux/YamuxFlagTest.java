package com.github.ruediste.p2psync.libp2p.mux.yamux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Verifies that the Yamux flag codec accepts all valid bit combinations (such
 * as SYN|FIN) and rejects undefined bits, instead of only recognizing the four
 * single-flag values.
 */
public class YamuxFlagTest {

    @Test
    public void acceptsCombinedFlags() {
        Set<YamuxFlag> flags = YamuxFlag.fromInt(YamuxFlag.SYN.intFlag | YamuxFlag.FIN.intFlag);
        assertTrue(flags.contains(YamuxFlag.SYN));
        assertTrue(flags.contains(YamuxFlag.FIN));
        assertEquals(YamuxFlag.SYN.intFlag | YamuxFlag.FIN.intFlag, YamuxFlag.toInt(flags));
    }

    @Test
    public void rejectsUnknownBits() {
        assertThrows(IllegalArgumentException.class, () -> YamuxFlag.fromInt(0x10));
    }
}
