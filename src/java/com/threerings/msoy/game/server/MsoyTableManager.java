//
// $Id$

package com.threerings.msoy.game.server;

import com.google.inject.Inject;

import com.threerings.presents.annotation.EventThread;
import com.threerings.presents.dobj.RootDObjectManager;
import com.threerings.presents.server.InvocationException;
import com.threerings.presents.server.InvocationManager;

import com.threerings.crowd.data.BodyObject;
import com.threerings.crowd.server.PlaceRegistry;

import com.threerings.parlor.data.Table;
import com.threerings.parlor.game.data.GameConfig;
import com.threerings.parlor.game.server.GameManager;
import com.threerings.parlor.server.TableManager;

import com.threerings.msoy.peer.data.MsoyNodeObject;
import com.threerings.msoy.peer.server.MsoyPeerManager;

import com.threerings.msoy.game.data.LobbyObject;
import com.threerings.msoy.game.data.MsoyMatchConfig;
import com.threerings.msoy.game.data.ParlorGameConfig;
import com.threerings.msoy.game.data.PlayerObject;
import com.threerings.msoy.game.data.TablesWaiting;

/**
 * Customizes the basic table manager with MSOY specific bits.
 */
@EventThread
public class MsoyTableManager extends TableManager
{
    @Inject public MsoyTableManager (
        RootDObjectManager omgr, InvocationManager invmgr, PlaceRegistry plreg)
    {
        super(omgr, invmgr, plreg, null);
        _allowBooting = true;
    }

    public void init (LobbyManager lmgr)
    {
        _lmgr = lmgr;
        _lobj = lmgr.getLobbyObject();
        setTableObject(_lobj);
    }

    @Override
    protected void tableCreated (Table table)
    {
        super.tableCreated(table);
        _lmgr.cancelShutdowner();
    }

    @Override
    protected void purgeTable (Table table)
    {
        super.purgeTable(table);
        if (_tables.size() == 0) {
            _lmgr.recheckShutdownInterval();
        }
    }

    @Override
    protected GameConfig createConfig (Table table)
    {
        ParlorGameConfig config = (ParlorGameConfig)super.createConfig(table);
        _lmgr.initConfig(config);
        return config;
    }

    @Override
    protected void notePlayerAdded (Table table, BodyObject body)
    {
        super.notePlayerAdded(table, body);

        // mark this player as "in" this game if they're not already
        _playerActions.updatePlayerGame((PlayerObject) body, _lobj.game);
    }

    @Override
    protected GameManager createGameManager (GameConfig config)
        throws InstantiationException, InvocationException
    {
        return _lmgr.createGameManager((ParlorGameConfig)config);
    }

    @Override
    protected boolean shouldPublish (Table table)
    {
        // remove unactionable tables from the lobby as normal players don't see them and we don't
        // want huge numbers of hidden single player tables clogging up our lobby object
        ParlorGameConfig config = (ParlorGameConfig)table.config;
        MsoyMatchConfig matchConfig = (MsoyMatchConfig)config.getGameDefinition().match;
        return !table.inPlay() ||
            !(config.getMatchType() != GameConfig.PARTY && matchConfig.unwatchable);
    }

    @Override
    protected void addTableToLobby (Table table)
    {
        super.addTableToLobby(table);

        if (!_publishedPending) {
            // we know that super has added the table, so publish a pending game object
            ((MsoyNodeObject) _peerMgr.getNodeObject()).addToTablesWaiting(
                new TablesWaiting(_lobj.game.gameId, _lobj.game.name));
            _publishedPending = true;
        }
    }

    @Override
    protected void removeTableFromLobby (Integer tableId)
    {
        super.removeTableFromLobby(tableId);

        if (_publishedPending && (0 == _tlobj.getTables().size())) {
            // if we are publishing, and the lobby is now empty, stop publishing
            ((MsoyNodeObject) _peerMgr.getNodeObject()).removeFromTablesWaiting(_lobj.game.gameId);
            _publishedPending = false;
        }
    }

    /** Are we currently advertising in the node object that there are pending tables? */
    protected boolean _publishedPending;

    protected LobbyManager _lmgr;
    protected LobbyObject _lobj;

    // our dependencies
    @Inject protected MsoyPeerManager _peerMgr;
    @Inject protected PlayerNodeActions _playerActions;
}
