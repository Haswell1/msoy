//
// $Id: GameDetailRecord.java 9296 2008-05-28 14:51:30Z mdb $

package com.threerings.msoy.item.server.persist;

import java.sql.Timestamp;

import com.samskivert.jdbc.depot.PersistentRecord;
import com.samskivert.jdbc.depot.annotation.Column;
import com.samskivert.jdbc.depot.annotation.Entity;
import com.samskivert.jdbc.depot.annotation.GeneratedValue;
import com.samskivert.jdbc.depot.annotation.Id;
import com.samskivert.jdbc.depot.annotation.Index;
import com.samskivert.jdbc.depot.expression.ColumnExp;

@Entity(indices={
    @Index(name="ixIdRecorded", fields={ GameTraceLogRecord.GAME_ID, GameTraceLogRecord.RECORDED })
})
public class GameTraceLogRecord
    extends PersistentRecord
{
    // AUTO-GENERATED: FIELDS START
    /** The column identifier for the {@link #gameId} field. */
    public static final String GAME_ID = "gameId";

    /** The qualified column identifier for the {@link #gameId} field. */
    public static final ColumnExp GAME_ID_C =
        new ColumnExp(GameTraceLogRecord.class, GAME_ID);

    /** The column identifier for the {@link #recorded} field. */
    public static final String RECORDED = "recorded";

    /** The qualified column identifier for the {@link #recorded} field. */
    public static final ColumnExp RECORDED_C =
        new ColumnExp(GameTraceLogRecord.class, RECORDED);

    /** The column identifier for the {@link #logData} field. */
    public static final String LOG_DATA = "logData";

    /** The qualified column identifier for the {@link #logData} field. */
    public static final ColumnExp LOG_DATA_C =
        new ColumnExp(GameTraceLogRecord.class, LOG_DATA);
    // AUTO-GENERATED: FIELDS END

    /** Increment this value if you modify the definition of this persistent object in a way that
     * will result in a change to its SQL counterpart. */
    public static final int SCHEMA_VERSION = 2;

    /** The primary key of this log record. */
    @Id
    @GeneratedValue
    public int logId;
    
    /** The id of the game whose logs we're recording. */
    public int gameId;

    /** The time at which these logs were recorded. */
    public Timestamp recorded;

    /** The log data in question. */
    @Column(length=65535)
    public String logData;
    
    public GameTraceLogRecord ()
    {
    }
    
    public GameTraceLogRecord (int gameId, String logData)
    {
        this.gameId = gameId;
        this.recorded = new Timestamp(System.currentTimeMillis());
        this.logData = logData;
    }
}
