//
// $Id$

package com.threerings.msoy.item.server.persist;

import com.samskivert.depot.Key;
import com.samskivert.depot.expression.ColumnExp;

import com.threerings.msoy.server.persist.RatingRecord;

/** Rating records for Decor. */
public class DecorRatingRecord extends RatingRecord
{
    // AUTO-GENERATED: FIELDS START
    /** The qualified column identifier for the {@link #targetId} field. */
    public static final ColumnExp TARGET_ID_C =
        new ColumnExp(DecorRatingRecord.class, TARGET_ID);

    /** The qualified column identifier for the {@link #memberId} field. */
    public static final ColumnExp MEMBER_ID_C =
        new ColumnExp(DecorRatingRecord.class, MEMBER_ID);

    /** The qualified column identifier for the {@link #rating} field. */
    public static final ColumnExp RATING_C =
        new ColumnExp(DecorRatingRecord.class, RATING);
    // AUTO-GENERATED: FIELDS END

    // AUTO-GENERATED: METHODS START
    /**
     * Create and return a primary {@link Key} to identify a {@link DecorRatingRecord}
     * with the supplied key values.
     */
    public static Key<DecorRatingRecord> getKey (int targetId, int memberId)
    {
        return new Key<DecorRatingRecord>(
                DecorRatingRecord.class,
                new String[] { TARGET_ID, MEMBER_ID },
                new Comparable[] { targetId, memberId });
    }
    // AUTO-GENERATED: METHODS END
}
