package {

import flash.display.Sprite;
import flash.display.Shape;
import flash.display.MovieClip;

import mx.core.MovieClipAsset;

import flash.utils.ByteArray;

import flash.external.ExternalInterface;

import flash.events.KeyboardEvent;
import flash.events.TimerEvent;

import flash.utils.Timer;
import flash.utils.getTimer;

import com.threerings.ezgame.Game;
import com.threerings.ezgame.EZGame;
import com.threerings.ezgame.MessageReceivedEvent;
import com.threerings.ezgame.MessageReceivedListener;
import com.threerings.ezgame.PropertyChangedEvent;
import com.threerings.ezgame.PropertyChangedListener;
import com.threerings.ezgame.StateChangedEvent;
import com.threerings.ezgame.StateChangedListener;

/**
 * The main game class for the client.
 */
[SWF(width="800", height="530")]
public class StarFight extends Sprite
    implements Game, PropertyChangedListener, MessageReceivedListener
{
    public static const WIDTH :int = 800;
    public static const HEIGHT :int = 530;

    /**
     * Constructs our main view area for the game.
     */
    public function StarFight ()
    {
        var mask :Shape = new Shape();
        addChild(mask);
        mask.graphics.clear();
        mask.graphics.beginFill(0xFFFFFF);
        mask.graphics.drawRect(0, 0, WIDTH, HEIGHT);
        mask.graphics.endFill();
        this.mask = mask;

        log("Created Game Object");

        graphics.beginFill(BLACK);
        graphics.drawRect(0, 0, StarFight.WIDTH, StarFight.HEIGHT);

        _lastTickTime = getTimer();
    }

    /**
     * For debug logging.
     */
    public function log (msg :String) :void
    {
        Logger.log(msg);
    }

    // from Game
    public function setGameObject (gameObj :EZGame) :void
    {
        log("Got game object");
        // set up our listeners
        _gameObj = gameObj;
        _gameObj.addEventListener(StateChangedEvent.GAME_STARTED, gameStarted);

        _gameObj.localChat("Welcome to Zyraxxus!");

        var boardObj :Board;
        var boardBytes :ByteArray =  ByteArray(_gameObj.get("board"));
        if (boardBytes != null) {
            boardObj = new Board(0, 0, false);
            boardBytes.position = 0;
            boardObj.readFrom(boardBytes);
        }

        // We don't already have a board and we're the host?  Create it and our
        //  initial ship array too.
        if ((boardObj == null) && (_gameObj.getMyIndex() == 0)) {
            boardObj = new Board(50, 50, true);
            _gameObj.set("ship", new Array(2));
            _gameObj.set("board", boardObj.writeTo(new ByteArray()));
        }

        // If we now have ourselves a board, do something with it, otherwise
        //  wait til we hear from the EZGame object.
        if (boardObj != null) {
            gotBoard(boardObj);
        }
    }

    /**
     * Do some initialization based on a received board.
     */
    protected function gotBoard (boardObj :Board) :void
    {
        _ships = [];
        _shots = [];

        _board = new BoardSprite(boardObj, _ships);
        addChild(_board);

        // Create our local ship and center the board on it.
        _ownShip = new ShipSprite(_board, this, false, _gameObj.getMyIndex());
        _ownShip.setPosRelTo(_ownShip.boardX, _ownShip.boardY);
        _board.setAsCenter(_ownShip.boardX, _ownShip.boardY);
        addChild(_ownShip);

        // Add ourselves to the ship array.
        _gameObj.set("ship", _ownShip.writeTo(new ByteArray()),
            _gameObj.getMyIndex());

        // Set up our initial ship sprites.
        var gameShips :Array = (_gameObj.get("ship") as Array);

        // The game already has some ships, create sprites for em.
        if (gameShips != null) {
            for (var ii :int = 0; ii < gameShips.length; ii++)
            {
                if (gameShips[ii] == null) {
                    _ships[ii] = null;
                } else {
                    _ships[ii] = new ShipSprite(_board, this, true, ii);
                    gameShips[ii].position = 0;
                    _ships[ii].readFrom(gameShips[ii]);
                    addChild(_ships[ii]);
                }
            }
        }

        _ships[_gameObj.getMyIndex()] = _ownShip;

        // Our ship is interested in keystrokes.
        stage.addEventListener(KeyboardEvent.KEY_DOWN, _ownShip.keyPressed);
        stage.addEventListener(KeyboardEvent.KEY_UP, _ownShip.keyReleased);

        // Set up our ticker that will control movement.
        var screenTimer :Timer = new Timer(REFRESH_RATE, 0); // As fast as possible.
        screenTimer.addEventListener(TimerEvent.TIMER, tick);
        screenTimer.start();
    }

    // from PropertyChangedListener
    public function propertyChanged (event :PropertyChangedEvent) :void
    {
        var name :String = event.name;
        if (name == "board" && (_board == null)) {
            log("Got a board change");
            // Someone else initialized our board.
            var boardBytes :ByteArray =  ByteArray(_gameObj.get("board"));
            var boardObj :Board = new Board(0, 0, false);
            boardBytes.position = 0;
            boardObj.readFrom(boardBytes);
            gotBoard(boardObj);
        } else if ((name == "ship") && (event.index >= 0)) {
            if (_ships != null && event.index != _gameObj.getMyIndex()) {
                // Someone else's ship - update our sprite for em.
                // TODO: Something to try to deal with latency and maybe smooth
                //  any shifts that occur.
                var ship :ShipSprite = _ships[event.index];
                if (ship == null) {
                    _ships[event.index] =
                        ship = new ShipSprite(_board, this, true, event.index);
                    addChild(ship);
                }
                var bytes :ByteArray = ByteArray(event.newValue);
                bytes.position = 0;
                ship.readFrom(bytes);
            }
        }
    }

    // from MessageReceivedListener
    public function messageReceived (event :MessageReceivedEvent) :void
    {
        if (event.name == "shot") {
            var val :Array = (event.value as Array);
            var shot :ShotSprite =
                new ShotSprite(val[0], val[1], val[2], val[3], val[4], this);
            _shots.push(shot);
            shot.setPosRelTo(_ownShip.boardX, _ownShip.boardY);
            addChild(shot);
        } else if (event.name == "explode") {
            var arr :Array = (event.value as Array);
            _board.explode(arr[0], arr[1], arr[2], false);
        }
    }

    /**
     * Register that a ship was hit at the location.
     */
    public function hit (ship :ShipSprite, x :int, y :int) :void
    {
        _board.explode(x, y, 0, true);
        if (ship == _ownShip) {
            ship.hit();
        }
    }



    /**
     * The game has started - do our initial startup.
     */
    protected function gameStarted (event :StateChangedEvent) :void
    {
        log("Game started");
        _gameObj.localChat("GO!!!!");
    }

    /**
     * Send a message to the server about our shot.
     */
    public function fireShot (x :Number, y :Number,
        xVel :Number, yVel :Number, shipId :int) :void
    {
        var args :Array = new Array(5);
        args[0] = x;
        args[1] = y;
        args[2] = xVel;
        args[3] = yVel;
        args[4] = shipId;
        _gameObj.sendMessage("shot", args);
    }

    /**
     * Register a big ole' explosion at the location.
     */
    public function explode (x :Number, y :Number, rot :int) :void
    {
        var args :Array = new Array(3);
        args[0] = x;
        args[1] = y;
        args[2] = rot;
        _gameObj.sendMessage("explode", args);
    }

    /**
     * When our screen updater timer ticks...
     */
    public function tick (event :TimerEvent) :void
    {
        var now :int = getTimer();
        var time :Number = (now - _lastTickTime)/REFRESH_RATE;

        // Update all ships.
        for each (var ship :ShipSprite in _ships) {
            if (ship != null) {
                ship.tick(time);
                ship.setPosRelTo(_ownShip.boardX, _ownShip.boardY);
            }
        }

        // Recenter the board on our ship.
        _board.setAsCenter(_ownShip.boardX, _ownShip.boardY);

        // Update all live shots.
        var completed :Array = []; // Array<ShotSprite>
        for each (var shot :ShotSprite in _shots) {
            if (shot != null) {
                shot.tick(_board, time);
                if (shot.complete) {
                    completed.push(shot);
                }
                shot.setPosRelTo(_ownShip.boardX, _ownShip.boardY);
            }
        }

        // Remove any that were done.
        for each (shot in completed) {
            _shots.splice(_shots.indexOf(shot), 1);
            removeChild(shot);
        }

        // Every few frames, broadcast our status to everyone else.
        if (_updateCount++ % FRAMES_PER_UPDATE == 0) {
            _gameObj.set("ship", _ownShip.writeTo(new ByteArray()),
                _gameObj.getMyIndex());
        }

        _lastTickTime = now;
    }

    /** The game data. */
    protected var _gameObj :EZGame;

    /** Our local ship. */
    protected var _ownShip :ShipSprite;

    /** All the ships. */
    protected var _ships :Array; // Array<ShipSprite>

    /** Live shots. */
    protected var _shots :Array; // Array<ShotSprite>

    /** The board with all its obstacles. */
    protected var _board :BoardSprite;

    /** How many frames its been since we broadcasted. */
    protected var _updateCount :int = 0;

    protected var _lastTickTime :int;

    /** Constants to control update frequency. */
    protected static const REFRESH_RATE :int = 50;
    protected static const FRAMES_PER_UPDATE :int = 2;

    /** Color constants. */
    protected static const BLACK :uint = uint(0x000000);

}
}
