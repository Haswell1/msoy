//
// $Id$

package com.threerings.msoy.server.persist;

import com.samskivert.depot.Key;
import com.samskivert.depot.PersistentRecord;
import com.samskivert.depot.annotation.*; // for Depot annotations
import com.samskivert.depot.expression.ColumnExp;

/**
 * Represents a mute relationship.
 */
@Entity(uniqueConstraints={
    @UniqueConstraint(name="muterMutee", fields={ "muterId", "muteeId" })
})
public class MuteRecord extends PersistentRecord
{
    // AUTO-GENERATED: FIELDS START
    public static final Class<MuteRecord> _R = MuteRecord.class;
    public static final ColumnExp MUTER_ID = colexp(_R, "muterId");
    public static final ColumnExp MUTEE_ID = colexp(_R, "muteeId");
    // AUTO-GENERATED: FIELDS END

    public static final int SCHEMA_VERSION = 1;

    /** The memberId of the muter. */
    @Id @Index(name="ixMuterId")
    public int muterId;

    /** The memberId of the mutee. */
    @Id
    public int muteeId;

    // AUTO-GENERATED: METHODS START
    /**
     * Create and return a primary {@link Key} to identify a {@link MuteRecord}
     * with the supplied key values.
     */
    public static Key<MuteRecord> getKey (int muterId, int muteeId)
    {
        return new Key<MuteRecord>(
                MuteRecord.class,
                new ColumnExp[] { MUTER_ID, MUTEE_ID },
                new Comparable[] { muterId, muteeId });
    }
    // AUTO-GENERATED: METHODS END
}
