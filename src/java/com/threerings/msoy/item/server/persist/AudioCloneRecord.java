//
// $Id$

package com.threerings.msoy.item.server.persist;

import com.samskivert.depot.Key;
import com.samskivert.depot.expression.ColumnExp;
import com.samskivert.depot.annotation.TableGenerator;

/** Clone records for Audios. */
@TableGenerator(name="cloneId", pkColumnValue="AUDIO_CLONE")
public class AudioCloneRecord extends CloneRecord
{
    // AUTO-GENERATED: FIELDS START
    /** The qualified column identifier for the {@link #itemId} field. */
    public static final ColumnExp ITEM_ID_C =
        new ColumnExp(AudioCloneRecord.class, ITEM_ID);

    /** The qualified column identifier for the {@link #originalItemId} field. */
    public static final ColumnExp ORIGINAL_ITEM_ID_C =
        new ColumnExp(AudioCloneRecord.class, ORIGINAL_ITEM_ID);

    /** The qualified column identifier for the {@link #ownerId} field. */
    public static final ColumnExp OWNER_ID_C =
        new ColumnExp(AudioCloneRecord.class, OWNER_ID);

    /** The qualified column identifier for the {@link #purchaseTime} field. */
    public static final ColumnExp PURCHASE_TIME_C =
        new ColumnExp(AudioCloneRecord.class, PURCHASE_TIME);

    /** The qualified column identifier for the {@link #currency} field. */
    public static final ColumnExp CURRENCY_C =
        new ColumnExp(AudioCloneRecord.class, CURRENCY);

    /** The qualified column identifier for the {@link #amountPaid} field. */
    public static final ColumnExp AMOUNT_PAID_C =
        new ColumnExp(AudioCloneRecord.class, AMOUNT_PAID);

    /** The qualified column identifier for the {@link #used} field. */
    public static final ColumnExp USED_C =
        new ColumnExp(AudioCloneRecord.class, USED);

    /** The qualified column identifier for the {@link #location} field. */
    public static final ColumnExp LOCATION_C =
        new ColumnExp(AudioCloneRecord.class, LOCATION);

    /** The qualified column identifier for the {@link #lastTouched} field. */
    public static final ColumnExp LAST_TOUCHED_C =
        new ColumnExp(AudioCloneRecord.class, LAST_TOUCHED);

    /** The qualified column identifier for the {@link #name} field. */
    public static final ColumnExp NAME_C =
        new ColumnExp(AudioCloneRecord.class, NAME);

    /** The qualified column identifier for the {@link #mediaHash} field. */
    public static final ColumnExp MEDIA_HASH_C =
        new ColumnExp(AudioCloneRecord.class, MEDIA_HASH);

    /** The qualified column identifier for the {@link #mediaStamp} field. */
    public static final ColumnExp MEDIA_STAMP_C =
        new ColumnExp(AudioCloneRecord.class, MEDIA_STAMP);
    // AUTO-GENERATED: FIELDS END

    // AUTO-GENERATED: METHODS START
    /**
     * Create and return a primary {@link Key} to identify a {@link AudioCloneRecord}
     * with the supplied key values.
     */
    public static Key<AudioCloneRecord> getKey (int itemId)
    {
        return new Key<AudioCloneRecord>(
                AudioCloneRecord.class,
                new String[] { ITEM_ID },
                new Comparable[] { itemId });
    }
    // AUTO-GENERATED: METHODS END
}
