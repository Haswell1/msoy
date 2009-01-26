//
// $Id$

package com.threerings.msoy.game.data {

import com.threerings.presents.data.InvocationCodes;

/**
 * Codes and constants used by the lobby services.
 */
public class LobbyCodes extends InvocationCodes
{
    /** A mode constant for {@link LobbyService#playNow}. */
    public static const PLAY_NOW_SINGLE :int = 0;

    /** A mode constant for {@link LobbyService#playNow}. */
    public static const PLAY_NOW_ANYONE :int = 1;

    /** Used by the lobby liaison to start a single player game if that's the only option. */
    public static const PLAY_NOW_IF_SINGLE :int = 2;

    /** Used by the lobby controller and liaison. */
    public static const SHOW_LOBBY_ANY :int = 3;

    /** Used by the lobby controller and liaison. */
    public static const SHOW_LOBBY_MULTIPLAYER :int = 4;

    /** Used by the lobby controller and liaison. */
    public static const JOIN_PLAYER :int = 5;
}
}
