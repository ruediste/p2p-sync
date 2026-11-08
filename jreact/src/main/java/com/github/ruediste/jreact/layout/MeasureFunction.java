package com.github.ruediste.jreact.layout;

import com.antepod.taffy.geometry.Size;
import com.antepod.taffy.style.AvailableSpace;

/**
 * Measures a leaf node.
 *
 * <p>
 * A measurement callback is attached directly to the node it measures, see
 * {@link LayoutNode#measure}.
 */
@FunctionalInterface
public interface MeasureFunction {

    /**
     * @param knownDimensions dimensions already constrained by the layout algorithm
     * @param availableSpace  space offered to the node
     * @param node            the node being measured
     * @return measured border-box size
     */
    Size<java.lang.Float> measure(Size<AvailableSpace> knownDimensions,
            Size<AvailableSpace> availableSpace, LayoutNode node);
}
