package {

import flash.display.Sprite;
import flash.display.Shape;
import flash.display.MovieClip;

import flash.media.Sound;
import flash.media.SoundTransform;

import mx.core.MovieClipAsset;

import flash.utils.ByteArray;

import flash.external.ExternalInterface;

import flash.events.KeyboardEvent;
import flash.events.TimerEvent;

import flash.utils.Timer;
import flash.utils.getTimer;

import com.threerings.ezgame.EZGameControl;
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
    implements PropertyChangedListener, MessageReceivedListener
{
    public static const WIDTH :int = 800;
    public static const HEIGHT :int = 530;

    /**
     * Constructs our main view area for the game.
     */
    public function StarFight ()
    {
        _gameCtrl = new EZGameControl(this);
        _gameCtrl.registerListener(this);

        _boardLayer = new Sprite();
        _shipLayer = new Sprite();
        _shotLayer = new Sprite();
        _statusLayer = new Sprite();
        addChild(_boardLayer);
        addChild(_shipLayer);
        addChild(_shotLayer);
        addChild(_statusLayer);

        var mask :Shape = new Shape();
        addChild(mask);
        mask.graphics.clear();
        mask.graphics.beginFill(0xFFFFFF);
        mask.graphics.drawRect(0, 0, WIDTH, HEIGHT);
        mask.graphics.endFill();
        this.mask = mask;

        graphics.beginFill(Codes.BLACK);
        graphics.drawRect(0, 0, StarFight.WIDTH, StarFight.HEIGHT);

        _lastTickTime = getTimer();

        _statusLayer.addChild(_status = new StatusOverlay());

        setGameObject();
        log("Created Game Controller");
    }

    /**
     * For debug logging.
     */
    public function log (msg :String) :void
    {
        Logger.log(msg);
    }

    // from Game
    public function setGameObject () :void
    {
        log("Got game object");
        // set up our listeners
        _gameCtrl.addEventListener(StateChangedEvent.GAME_STARTED, gameStarted);

        _gameCtrl.localChat("Welcome to Zyraxxus!");

        var boardObj :Board;
        var boardBytes :ByteArray =  ByteArray(_gameCtrl.get("board"));
        if (boardBytes != null) {
            boardObj = new Board(0, 0, false);
            boardBytes.position = 0;
            boardObj.readFrom(boardBytes);
        }

        // We don't already have a board and we're the host?  Create it and our
        //  initial ship array too.
        if ((boardObj == null) && (_gameCtrl.getMyIndex() == 0)) {
            var size :int =
                int(Math.sqrt(Math.max(1, _gameCtrl.getPlayerCount()-1)) * 50);

            boardObj = new Board(size, size, true);
            _gameCtrl.set("ship", new Array(_gameCtrl.getPlayerCount()));

            var maxPowerups :int = Math.max(1,
                boardObj.width*boardObj.height/MIN_TILES_PER_POWERUP);
            _gameCtrl.set("powerup", new Array(maxPowerups));
            _gameCtrl.set("board", boardObj.writeTo(new ByteArray()));
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
        _powerups = [];

        _bg = new BgSprite(boardObj);
        _boardLayer.addChild(_bg);
        _board = new BoardSprite(boardObj, _ships, _powerups);
        _boardLayer.addChild(_board);

        var names :Array = _gameCtrl.getPlayerNames();
        var myIdx :int = _gameCtrl.getMyIndex();

        // Create our local ship and center the board on it.
        _ownShip = new ShipSprite(_board, this, false, myIdx, names[myIdx],
            true);
        _ownShip.setPosRelTo(_ownShip.boardX, _ownShip.boardY);
        _board.setAsCenter(_ownShip.boardX, _ownShip.boardY);
        _bg.setAsCenter(_ownShip.boardX, _ownShip.boardY);
        _shipLayer.addChild(_ownShip);

        // Add ourselves to the ship array.
        _gameCtrl.set("ship", _ownShip.writeTo(new ByteArray()),
            _gameCtrl.getMyIndex());

        // Set up our initial ship sprites.
        var gameShips :Array = (_gameCtrl.get("ship") as Array);

        // The game already has some ships, create sprites for em.
        if (gameShips != null) {
            for (var ii :int = 0; ii < gameShips.length; ii++)
            {
                if (gameShips[ii] == null) {
                    _ships[ii] = null;
                } else if (ii != _gameCtrl.getMyIndex()) {
                    _ships[ii] = new ShipSprite(_board, this, true, ii,
                        names[ii], false);
                    gameShips[ii].position = 0;
                    _ships[ii].readFrom(gameShips[ii]);
                    _shipLayer.addChild(_ships[ii]);
                }
            }
        }

        // Set up our initial powerups.
        var gamePows :Array = (_gameCtrl.get("powerup") as Array);

        // The game already has some ships, create sprites for em.
        if (gamePows != null) {
            for (var pp :int = 0; pp < gamePows.length; pp++)
            {
                if (gamePows[pp] == null) {
                    _powerups[pp] = null;
                } else {
                    _powerups[pp] = new Powerup(0, 0, 0);
                    gamePows[pp].position = 0;
                    _powerups[pp].readFrom(gamePows[pp]);
                    _board.powerupLayer.addChild(_powerups[pp]);
                }
            }
        }

        // The first player is in charge of adding powerups.
        if (_gameCtrl.getMyIndex() == 0) {
            addPowerup(null);
            var timer :Timer = new Timer(20000, 0);
            timer.addEventListener(TimerEvent.TIMER, addPowerup);
            timer.start();
        }

        _ships[_gameCtrl.getMyIndex()] = _ownShip;

        // Our ship is interested in keystrokes.
        _gameCtrl.addEventListener(KeyboardEvent.KEY_DOWN, _ownShip.keyPressed);
        _gameCtrl.addEventListener(KeyboardEvent.KEY_UP, _ownShip.keyReleased);

        // Set up our ticker that will control movement.
        var screenTimer :Timer = new Timer(Codes.REFRESH_RATE, 0); // As fast as possible.
        screenTimer.addEventListener(TimerEvent.TIMER, tick);
        screenTimer.start();
    }

    /**
     * Tells everyone about a new powerup.
     */
    public function addPowerup (event :TimerEvent) :void
    {
        for (var ii :int = 0; ii < _powerups.length; ii++) {
            if (_powerups[ii] == null) {
                var x :int = Math.random() * _board.boardWidth;
                var y :int = Math.random() * _board.boardHeight;

                var repCt :int = 0;

                while (_board.getObstacleAt(x, y) ||
                    (_board.getPowerupIdx(x+0.5, y+0.5, x+0.5, y+0.5,
                        0.1) != -1)) {
                    x = Math.random() * _board.boardWidth;
                    y = Math.random() * _board.boardHeight;

                    // Safety valve - if we can't find anything after 100
                    //  tries, bail.
                    if (repCt++ > 100) {
                        return;
                    }
                }

                _powerups[ii] = new Powerup(1+Math.random()*3, x, y);

                _gameCtrl.set("powerup", _powerups[ii].writeTo(new ByteArray()),
                    ii);
                _board.powerupLayer.addChild(_powerups[ii]);
                return;
            }
        }

        // If we're all full up, don't do anything.
    }

    public function removePowerup (idx :int) :void
    {
        _gameCtrl.set("powerup", null, idx);
        _board.powerupLayer.removeChild(_powerups[idx]);
        _powerups[idx] = null;
    }

    // from PropertyChangedListener
    public function propertyChanged (event :PropertyChangedEvent) :void
    {
        var name :String = event.name;
        if (name == "board" && (_board == null)) {
            log("Got a board change");
            // Someone else initialized our board.
            var boardBytes :ByteArray =  ByteArray(_gameCtrl.get("board"));
            var boardObj :Board = new Board(0, 0, false);
            boardBytes.position = 0;
            boardObj.readFrom(boardBytes);
            gotBoard(boardObj);
        } else if ((name == "ship") && (event.index >= 0)) {
            if (_ships != null && event.index != _gameCtrl.getMyIndex()) {
                // Someone else's ship - update our sprite for em.
                var ship :ShipSprite = _ships[event.index];
                if (ship == null) {
                    _ships[event.index] =
                        ship = new ShipSprite(_board, this, true, event.index,
                            _gameCtrl.getPlayerNames()[event.index], false);
                    _shipLayer.addChild(ship);
                }
                var bytes :ByteArray = ByteArray(event.newValue);
                bytes.position = 0;
                var sentShip :ShipSprite = new ShipSprite(_board, this, true,
                    event.index, _gameCtrl.getPlayerNames()[event.index], false);
                sentShip.readFrom(bytes);
                ship.updateForReport(sentShip);
                _status.checkHiScore(ship);
            }
        } else if ((name =="powerup") && (event.index >= 0)) {
            if (_powerups != null) {
                if (event.newValue == null) {
                    if (_powerups[event.index] != null) {
                        _board.powerupLayer.removeChild(
                            _powerups[event.index]);
                        _powerups[event.index] = null;
                    }
                    return;
                }

                var pow :Powerup = _powerups[event.index];
                if (pow == null) {
                    _powerups[event.index] =
                        pow = new Powerup(0, 0, 0);
                    _board.powerupLayer.addChild(pow);
                }
                var pBytes :ByteArray = ByteArray(event.newValue);
                pBytes.position = 0;
                pow.readFrom(pBytes);
            }
        }
    }

    // from MessageReceivedListener
    public function messageReceived (event :MessageReceivedEvent) :void
    {
        if (event.name == "shot") {
            var val :Array = (event.value as Array);
            addShot(new ShotSprite(val[0], val[1], val[2], val[3], val[4],
                        val[5], this));

            if (val[6] == ShotSprite.SPREAD) {
                addShot(new ShotSprite(val[0], val[1], val[2],
                            val[3] + ShotSprite.SPREAD_FACT, val[4], val[5],
                            this));
                addShot(new ShotSprite(val[0], val[1], val[2],
                            val[3] - ShotSprite.SPREAD_FACT, val[4], val[5],
                            this));
            } else {

            }

            // Shooting sound.
            var sound :Sound = (val[6] == ShotSprite.SPREAD) ?
                ((val[5] == Codes.SHIP_1) ? Sounds.TRI_BEAM : Sounds.TRI_BEAM2) :
                ((val[5] == Codes.SHIP_1) ? Sounds.BEAM : Sounds.BEAM2);
            playSoundAt(sound, val[0], val[1]);

        } else if (event.name == "explode") {
            var arr :Array = (event.value as Array);

            _board.explode(arr[0], arr[1], arr[2], false, arr[4]);
            playSoundAt(Sounds.SHIP_EXPLODE, arr[0], arr[1]);

            if (arr[3] == _ownShip.shipId) {
                addScore(KILL_PTS);
            }
        }
    }

    /**
     * Adds a shot to the game and gets its sprite going.
     */
    protected function addShot (shot :ShotSprite) :void
    {
        _shots.push(shot);
        shot.setPosRelTo(_ownShip.boardX, _ownShip.boardY);
        _shotLayer.addChild(shot);
    }

    /**
     * Adds to our score.
     */
    protected function addScore (score :int) :void
    {
        _status.addScore(score);
        _ownShip.addScore(score);
        _status.checkHiScore(_ownShip);
    }

    /**
     * Register that a ship was hit at the location.
     */
    public function hitShip (ship :ShipSprite, x :Number, y :Number,
        shooterId :int) :void
    {
        _board.explode(x, y, 0, true, 0);

        var sound :Sound = (ship.powerups & ShipSprite.SHIELDS_MASK) ?
            Sounds.SHIELDS_HIT : Sounds.SHIP_HIT;
        playSoundAt(sound, x, y);

        if (ship == _ownShip) {
            ship.hit(shooterId);
            _status.setPower(ship.power);
        } else if (shooterId == _ownShip.shipId) {
            // We hit someone!  Give us some points.
            addScore(HIT_PTS);
        }
    }

    /**
     * Register that an obstacle was hit.
     */
    public function hitObs (obs :Obstacle, x :Number, y :Number) :void
    {
        _board.explode(x, y, 0, true, 0);

        var sound :Sound;
        switch (obs.type) {
        case Obstacle.ASTEROID_1:
        case Obstacle.ASTEROID_2:
            sound = Sounds.ASTEROID_HIT;
            break;
        case Obstacle.WALL:
        default:
            sound = Sounds.METAL_HIT;
            break;
        }
        playSoundAt(sound, x, y);
    }

    /**
     * Play a sound appropriately for the position it's at (which might be not
     *  at all...)
     */
    public function playSoundAt (sound :Sound, x :Number, y :Number) :void
    {
        var dx :Number = _ownShip.boardX - x;
        var dy :Number = _ownShip.boardY - y;
        var dist :Number = Math.sqrt(dx*dx + dy*dy);

        var vol :Number = 1.0 - (dist/25.0);
        if (vol > 0.0) {
            sound.play(0, 0, new SoundTransform(vol));
        }
    }

    /**
     * The game has started - do our initial startup.
     */
    protected function gameStarted (event :StateChangedEvent) :void
    {
        log("Game started");
        _gameCtrl.localChat("GO!!!!");
    }

    /**
     * Send a message to the server about our shot.
     */
    public function fireShot (x :Number, y :Number,
        vel :Number, angle :Number, shipId :int, shipType :int,
        type :int) :void
    {
        var args :Array = new Array(5);
        args[0] = x;
        args[1] = y;
        args[2] = vel;
        args[3] = angle;
        args[4] = shipId;
        args[5] = shipType;
        args[6] = type;
        _gameCtrl.sendMessage("shot", args);
    }

    /**
     * Register a big ole' explosion at the location.
     */
    public function explode (x :Number, y :Number, rot :int,
        shooterId :int, shipType :int) :void
    {
        var args :Array = new Array(3);
        args[0] = x;
        args[1] = y;
        args[2] = rot;
        args[3] = shooterId;
        args[4] = shipType;
        _gameCtrl.sendMessage("explode", args);
    }

    /**
     * When our screen updater timer ticks...
     */
    public function tick (event :TimerEvent) :void
    {
        var now :int = getTimer();
        var time :Number = (now - _lastTickTime)/Codes.REFRESH_RATE;

        var ownOldX :Number = _ownShip.boardX;
        var ownOldY :Number = _ownShip.boardY;

        // Update all ships.
        for each (var ship :ShipSprite in _ships) {
            if (ship != null) {
                ship.tick(time);
            }
        }

        // And then shift em based on ownship's new pos.
        for each (ship in _ships) {
            if (ship != null) {
                ship.setPosRelTo(_ownShip.boardX, _ownShip.boardY);
            }
        }

        var powIdx :int = _board.getPowerupIdx(ownOldX, ownOldY,
            _ownShip.boardX, _ownShip.boardY, ShipSprite.COLLISION_RAD);
        while (powIdx != -1) {
            _ownShip.awardPowerup(_powerups[powIdx].type);
            playSoundAt(Sounds.POWERUP, _powerups[powIdx].boardX,
                _powerups[powIdx].boardY);
            addScore(POWERUP_PTS);
            removePowerup(powIdx);

            powIdx = _board.getPowerupIdx(ownOldX, ownOldY,
                _ownShip.boardX, _ownShip.boardY, ShipSprite.COLLISION_RAD);
        }

        // Recenter the board on our ship.
        _board.setAsCenter(_ownShip.boardX, _ownShip.boardY);
        _bg.setAsCenter(_ownShip.boardX, _ownShip.boardY);
        _status.setPowerups(_ownShip.powerups);

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
            _shotLayer.removeChild(shot);
        }

        // Every few frames, broadcast our status to everyone else.
        if (_updateCount++ % Codes.FRAMES_PER_UPDATE == 0) {
            _gameCtrl.set("ship", _ownShip.writeTo(new ByteArray()),
                _gameCtrl.getMyIndex());
        }

        _lastTickTime = now;
    }

    /** Our game control object. */
    protected var _gameCtrl :EZGameControl;

    /** Our local ship. */
    protected var _ownShip :ShipSprite;

    /** All the ships. */
    protected var _ships :Array; // Array<ShipSprite>

    /** All the active powerups. */
    protected var _powerups :Array; // Array<Powerup>

    /** Live shots. */
    protected var _shots :Array; // Array<ShotSprite>

    /** The board with all its obstacles. */
    protected var _board :BoardSprite;

    /** The background graphics. */
    protected var _bg :BgSprite;

    /** Status info. */
    protected var _status :StatusOverlay;

    /** How many frames its been since we broadcasted. */
    protected var _updateCount :int = 0;

    protected var _lastTickTime :int;

    protected var _boardLayer :Sprite;
    protected var _shipLayer :Sprite;
    protected var _shotLayer :Sprite;
    protected var _statusLayer :Sprite;

    /** This could be more dynamic. */
    protected static const MIN_TILES_PER_POWERUP :int = 250;

    /** Points for various things in the game. */
    protected static const POWERUP_PTS :int = 25;
    protected static const HIT_PTS :int = 10;
    protected static const KILL_PTS :int = 50;
}
}
