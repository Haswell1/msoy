package {

import Math;

import mx.core.SpriteAsset;

import flash.display.Sprite;
import flash.display.MovieClip;
import flash.external.ExternalInterface;
import flash.events.TimerEvent;
import flash.utils.Timer;
import flash.ui.Keyboard;

import com.threerings.ezgame.Game;
import com.threerings.ezgame.EZGame;
import com.threerings.ezgame.MessageReceivedEvent;
import com.threerings.ezgame.MessageReceivedListener;
import com.threerings.ezgame.PropertyChangedEvent;
import com.threerings.ezgame.PropertyChangedListener;
import com.threerings.ezgame.StateChangedEvent;
import com.threerings.ezgame.StateChangedListener;
    
[SWF(width="700", height="500")]
public class Juggler extends Sprite 
    implements Game, PropertyChangedListener, StateChangedListener
{
    public function Juggler ()
    {
        addChild(new backgroundClass());

        _space = new Space(0,0, 700,500, 50);                
        _collider = new Collider(this);
        _actors = new Array();
                
        _body = new Body(this, _space);
        _body.x = 337;
        _body.y = 530;
        
        _controller = new JugglingController(_body, this);
        _body.controller = _controller;
                
        ballBox = new BallBox(this, _space);        
        
        _display = new ScoreDisplay(this);

        scoreCard = new ScoreCard();
        scoreCard.display = _display;
    }

    public function registerAsActor(actor:Actor) :void
    {
        _actors.push(actor);
    }

    public function deregisterAsActor(target:Actor) :void
    {
        Util.removeFromArray(_actors, target);
    }

    public function registerForCollisions(body:CanCollide) :void
    {
        _collider.addBody(body);
    }

    public function deregisterForCollisions(body:CanCollide) :void
    {
        _collider.removeBody(body);
    }

    public function tick(event :TimerEvent) : void 
    {
        event.updateAfterEvent();
        _collider.detectCollisions();

        for each (var actor:Actor in _actors)
        {
            actor.nextFrame();
        }
    }

    public function setGameObject (gameObj : EZGame) :void
    {
        _ezgame = gameObj;
        
        const frameTimer :Timer = new Timer(_space.frameDuration, 0);
        frameTimer.addEventListener(TimerEvent.TIMER, tick);
        frameTimer.start();
        
        _controller.eventSource = stage;
    }
        
    public function addBall() :void
    {
        if (_body.addBall()) {
            _ballsPlayed += 1;
        }
    }
    
    public function stateChanged (event: StateChangedEvent) :void
    {
        // do nothing for now
    }
    
    public function propertyChanged (event: PropertyChangedEvent) :void
    {
        // do nothing for now
    }
        
    public static const DEBUG_GRAPHICS:Boolean = false;
    
    private var _body :Body;

    private var _actors :Array;

    private var _space :Space;
            
    private var _controller :JugglingController;
            
    private var _collider :Collider;
            
    /** The game object for this event. */
    private var _ezgame :EZGame;

    private var _display:ScoreDisplay;
                
    private var _ballsPlayed:int = 0;
                
    private static const NUM_BALLS :int = 100;
             
    public var ballBox:BallBox;
     
    public var scoreCard:ScoreCard; 
     
    [Bindable]
    [Embed(source="bg_tent.swf")]
    private var backgroundClass:Class;
   
} 
}