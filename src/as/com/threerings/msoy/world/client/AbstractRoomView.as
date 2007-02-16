//
// $Id$

package com.threerings.msoy.world.client {

import flash.display.DisplayObject;
import flash.display.Graphics;
import flash.display.Shape;
import flash.display.Sprite;

import flash.events.Event;

import flash.geom.Point;
import flash.geom.Rectangle;

import flash.utils.getTimer; // function import

import com.threerings.util.ArrayUtil;
import com.threerings.util.DisplayUtil;
import com.threerings.util.HashMap;
import com.threerings.util.Iterator;

import com.threerings.presents.dobj.EntryAddedEvent;
import com.threerings.presents.dobj.EntryRemovedEvent;
import com.threerings.presents.dobj.EntryUpdatedEvent;
import com.threerings.presents.dobj.SetListener;

import com.threerings.crowd.data.OccupantInfo;
import com.threerings.crowd.data.PlaceObject;

import com.threerings.whirled.spot.data.Location;

import com.threerings.msoy.client.WorldContext;
import com.threerings.msoy.client.MsoyPlaceView;
import com.threerings.msoy.client.Prefs;
import com.threerings.msoy.item.web.MediaDesc;
import com.threerings.msoy.world.data.FurniData;
import com.threerings.msoy.world.data.MsoyLocation;
import com.threerings.msoy.world.data.MsoyScene;
import com.threerings.msoy.world.data.MsoySceneModel;
import com.threerings.msoy.world.data.RoomObject;

public class AbstractRoomView extends Sprite
    implements MsoyPlaceView
{
    public static const FLOOR :int = 0;
    public static const BACK_WALL :int = 1;
    public static const CEILING :int = 2;
    public static const LEFT_WALL :int = 3;
    public static const RIGHT_WALL :int = 4;

    public function AbstractRoomView (ctx :WorldContext)
    {
        _ctx = ctx;
    }

    // from MsoyPlaceView
    public function setPlaceSize (
        unscaledWidth :Number, unscaledHeight :Number) :void
    {
        _actualWidth = unscaledWidth;
        _actualHeight = unscaledHeight;
        var scale :Number = (unscaledHeight / TARGET_HEIGHT);
        scaleX = scale;
        scaleY = scale;

        relayout();
        updateDrawnRoom();
    }

    /**
     * Get the room's stage bounds.
     */
    public function getGlobalBounds () :Rectangle
    {
        var r :Rectangle = new Rectangle();
        r.topLeft = localToGlobal(new Point(0, 0));
        r.bottomRight = localToGlobal(new Point(_actualWidth, _actualHeight));
        return r;
    }

    /**
     * Called by the editor to have direct access to our sprite list..
     */
    public function getFurniSprites () :Array
    {
        return _furni.values();
    }

    /**
     * Returns the room distributed object.
     */
    public function getRoomObject () :RoomObject
    {
        return _roomObj;
    }

    /**
     * Called by MsoySprite instances when they've had their location updated.
     */
    public function locationUpdated (sprite :MsoySprite) :void
    {
        // first update the position and scale
        var loc :MsoyLocation = sprite.loc;
        positionAndScale(sprite, loc);

        // then, possibly move the child up or down, depending on z order
        if (sprite.isIncludedInLayout()) {
            adjustZOrder(sprite, loc);
        }
    }

    /**
     * Enable or disable editing. Called by the EditRoomController.
     */
    public function setEditing (editing :Boolean, spriteVisitFn :Function) :void
    {
        _editing = editing;
        _furni.forEach(spriteVisitFn);

        if (editing) {

        } else {
            updateAllFurni();
        }
    }

    /**
     * Turn the screen coordinate into a MsoyLocation, with the orient field set to 0.
     *
     * @return a ClickLocation object.
     */
    public function pointToLocation (globalX :Number, globalY :Number) :ClickLocation
    {
        var p :Point = globalToLocal(new Point(globalX, globalY));
        var x :Number = p.x;
        var y :Number = p.y;

        // TODO NOW
        // TODO: much of this could be pre-computed, perhaps we store it in
        // a SceneMetrics class
        var sceneWidth :int = _scene.getWidth();
        var minScale :Number = computeMinScale();
        var backWallHeight :Number = TARGET_HEIGHT * minScale;

        var horizon :Number = 1 - _scene.getHorizon();
        var horizonY :Number = TARGET_HEIGHT * horizon;
        var backWallTop :Number = horizonY - (backWallHeight * horizon);
        var backWallBottom :Number = backWallTop + backWallHeight;
        var floorWidth :Number, floorInset :Number;
        var xx :Number, yy :Number, zz :Number;
        var scale :Number;
        var clickWall :int;

        // do some partitioning depending on where the y lies
        if (y < backWallTop) {
            clickWall = ClickLocation.CEILING;
            scale = minScale + (backWallTop - y) / backWallTop * (MAX_SCALE - minScale);

        } else if (y < backWallBottom) {
            clickWall = ClickLocation.BACK_WALL;
            scale = minScale;

        } else {
            clickWall = ClickLocation.FLOOR;
            scale = minScale + (y - backWallBottom) / (TARGET_HEIGHT - backWallBottom) *
                (MAX_SCALE - minScale);
        }

        // see how wide the floor is at that scale
        floorWidth = (sceneWidth * scale);
        floorInset = (sceneWidth - floorWidth) / 2;

        if (x < floorInset || x - floorInset > floorWidth) {
            if (x < floorInset) {
                clickWall = ClickLocation.LEFT_WALL;
                xx = 0;

            } else {
                clickWall = ClickLocation.RIGHT_WALL;
                xx = MAX_COORD;
            }

            // recalculate floorWidth at the minimum scale
            if (scale != minScale) {
                floorWidth = (sceneWidth * minScale);
                floorInset = (sceneWidth - floorWidth) / 2;
            }

            switch (clickWall) {
            case ClickLocation.LEFT_WALL:
                scale = minScale + (x / floorInset) * (MAX_SCALE - minScale);
                break;

            case ClickLocation.RIGHT_WALL:
                scale = minScale + ((sceneWidth - x) / floorInset) * (MAX_SCALE - minScale);
                break;

            default:
                throw new Error(clickWall);
            }

            // TODO: factor in horizon here
            var wallHeight :Number = (TARGET_HEIGHT * scale);
            var wallInset :Number = (TARGET_HEIGHT - wallHeight) / 2;
            yy = MAX_COORD * (1 - ((y - wallInset) / wallHeight));
            zz = MAX_COORD * ((scale - minScale) / (MAX_SCALE - minScale));

        } else {
            // normal case: the x coordinate is within the floor width
            // at that scale, so we're definitely not clicking on a side wall
            xx = ((x - floorInset) / floorWidth) * MAX_COORD;

            switch (clickWall) {
            case ClickLocation.CEILING:
            case ClickLocation.FLOOR:
                yy = (clickWall == ClickLocation.CEILING) ? MAX_COORD : 0;
                zz = MAX_COORD * (1 - ((scale - minScale) / (MAX_SCALE - minScale)));
                break;

            case ClickLocation.BACK_WALL:
                // y is simply how high they clicked on the wall
                yy = (backWallBottom - y) / backWallHeight;
                zz = 1;
                break;

            default:
                throw new Error(clickWall);
            }
        }

        return new ClickLocation(clickWall, new MsoyLocation(xx, yy, zz, 0));
    }

    protected function setBackWallBottom (bwb :Number) :void
    {
        // nada, overridden, will go away with a TODO, above
    }

    /**
     * Get the y distance represented by the specified number of pixels for the given z coordinate.
     */
    public function getYDistance (z :Number, pixels :int) :Number
    {
        var minScale :Number = computeMinScale();
        var scale :Number = minScale + ((MAX_COORD - z) / MAX_COORD) * (MAX_SCALE - minScale);
        var sheight :Number = (TARGET_HEIGHT * scale);
        return (pixels / sheight);
    }

    /**
     * Scroll the view by the specified number of pixels.
     *
     * @return true if the view is scrollable.
     */
    public function scrollViewBy (xpixels :int) :Boolean
    {
        var rect :Rectangle = scrollRect;
        if (rect == null) {
            return false;
        }

        var bounds :Rectangle = getScrollBounds();
        rect.x = Math.min(bounds.x + bounds.width - rect.width,
                          Math.max(bounds.x, rect.x + xpixels));
        scrollRect = rect;
        scrollRectUpdated();
        return true;
    }

    /**
     * Get the current scroll value.
     */
    public function getScrollOffset () :Number
    {
        var r :Rectangle = scrollRect;
        return (r == null) ? 0 : r.x;
    }

    /**
     * Get the full boundaries of our scrolling area.
     */
    public function getScrollBounds () :Rectangle
    {
        if (_scene == null) {
            return new Rectangle(0, 0, _actualWidth, _actualHeight);
        }

        var r :Rectangle = new Rectangle(0, 0, _scene.getWidth() * scaleX, _actualHeight);
        if (_editing) {
            r.inflate(_actualWidth * 2 / 3, 0);
        }
        return r;
    }

    /**
     * Add the specified sprite to this display and have the room track it.
     */
    public function addOtherSprite (sprite :MsoySprite) :void
    {
        _otherSprites.push(sprite);
        addChild(sprite);
        sprite.setLocation(sprite.loc);
    }

    /**
     * Remove the specified sprite.
     */
    public function removeOtherSprite (sprite :MsoySprite) :void
    {
        ArrayUtil.removeAll(_otherSprites, sprite);
        removeSprite(sprite);
    }

    /**
     * Calculate the info needed to perspectivize a piece of furni.
     */
    public function getPerspInfo (sprite :MsoySprite, contentWidth :int, contentHeight :int,
                                  loc :MsoyLocation) :PerspInfo
    {
        var hotSpot :Point = sprite.getMediaHotSpot();
        var mediaScaleX :Number = Math.abs(sprite.getMediaScaleX());
        var mediaScaleY :Number = Math.abs(sprite.getMediaScaleY());

        // below, 0 refers to the right side of the source sprite
        // N refers to the left side, and H refers to the location
        // of the hotspot

        // the scale of the object is determined by the z coordinate
        var distH :Number = FOCAL + (_scene.getDepth() * loc.z);
        var dist0 :Number = (hotSpot.x * mediaScaleX);
        var distN :Number = (contentWidth - hotSpot.x) * mediaScaleX;
        if (loc.x < .5) {
            dist0 *= -1;
        } else {
            distN *= -1;
        }

        var scale0 :Number = FOCAL / (distH + dist0);
        var scaleH :Number = FOCAL / distH;
        var scaleN :Number = FOCAL / (distH + distN);

        var logicalY :Number = loc.y + ((contentHeight * mediaScaleY) / TARGET_HEIGHT);

        var p0 :Point = projectedLocation(scale0, loc.x, logicalY);
        var pH :Point = projectedLocation(scaleH, loc.x, loc.y);
        var pN :Point = projectedLocation(scaleN, loc.x, logicalY);

        var height0 :Number = contentHeight * scale0 * mediaScaleY;
        var heightN :Number = contentHeight * scaleN * mediaScaleY;

        // min/max don't account for the hotspot location
        var minX :Number = Math.min(p0.x, pN.x);
        var minY :Number = Math.min(p0.y, pN.y);
        p0.offset(-minX, -minY);
        pN.offset(-minX, -minY);
        pH.offset(-minX, -minY);

        return new PerspInfo(p0, height0, pN, heightN, pH);
    }

    // documentation inherited from interface PlaceView
    public function willEnterPlace (plobj :PlaceObject) :void
    {
        // save our scene object
        _roomObj = (plobj as RoomObject);

        // get the specifics on the current scene from the scene director
        _scene = (_ctx.getSceneDirector().getScene() as MsoyScene);

        configureScrollRect();
        updateDrawnRoom();
        updateAllFurni();
    }

    public function updateAllFurni () :void
    {
        if (shouldLoadAll()) {
            // set up any furniture
            for each (var furni :FurniData in _scene.getFurni()) {
                if (!furni.media.isAudio()) {
                    updateFurni(furni);
                }
            }
        }
    }

    // documentation inherited from interface PlaceView
    public function didLeavePlace (plobj :PlaceObject) :void
    {
        removeAll(_furni);

        _roomObj = null;
        _scene = null;
    }

    /**
     * Layout everything.
     */
    protected function relayout () :void
    {
        configureScrollRect();

        var sprite :MsoySprite;
        for each (sprite in _furni.values()) {
            locationUpdated(sprite);
        }
        for each (sprite in _otherSprites) {
            locationUpdated(sprite);
        }
    }

    /**
     * Should we load everything that we know how to?
     * This is used by a subclass to restrict loading to certain things
     * when the room is first entered.
     */
    protected function shouldLoadAll () :Boolean
    {
        return true;
    }

    /**
     * Configure the rectangle used to select a portion of the view that's showing.
     */
    protected function configureScrollRect () :void
    {
        var bounds :Rectangle = getScrollBounds();
        if (bounds.width > _actualWidth) {
            scrollRect = new Rectangle(0, 0, _actualWidth, _actualHeight);

        } else {
            scrollRect = null;
        }
    }

    protected function computeMinScale () :Number
    {
        var sceneDepth :int = _scene.getDepth();
        return (sceneDepth == 0) ? 0 : (FOCAL / (FOCAL + sceneDepth));
    }

    protected function scrollRectUpdated () :void
    {
        if (_bkg != null && _scene.getSceneType() == MsoySceneModel.FIXED_IMAGE) {
            locationUpdated(_bkg);
        }
    }

    /**
     * Calculate the scale and x/y position of the specified media according to its logical
     * coordinates.
     */
    protected function positionAndScale (sprite :MsoySprite, loc :MsoyLocation) :void
    {
        // the scale of the object is determined by the z coordinate
        var minScale :Number = computeMinScale();
        var scale :Number = minScale + ((MAX_COORD - loc.z) / MAX_COORD) * (MAX_SCALE - minScale);
        sprite.setLocationScale(scale);

        var p :Point = projectedLocation(scale, loc.x, loc.y);
        var hotSpot :Point = sprite.getLayoutHotSpot();
        if (sprite == _bkg && _scene.getSceneType() == MsoySceneModel.FIXED_IMAGE) {
            // adjust the background image
            p.x += getScrollOffset();
        }
        sprite.x = p.x - hotSpot.x;
        sprite.y = p.y - hotSpot.y;
    }

    /**
     * Determine the location of the projected coordinate.
     *
     * @param x the logical x coordinate (0 - 1)
     * @param y the logical y coordinate (0 - 1)
     */
    protected function projectedLocation (scale :Number, x :Number, y :Number) :Point
    {
        var sceneWidth :Number = _scene.getWidth();

        // x position depends on logical x and the scale
        var floorWidth :Number = (sceneWidth * scale);
        var floorInset :Number = (sceneWidth - floorWidth) / 2;

        // y position depends on logical y and the scale (z)
        var horizon :Number = 1 - _scene.getHorizon();
        var horizonY :Number = TARGET_HEIGHT * horizon;

        return new Point(floorInset + (x * floorWidth),
                         horizonY + ((TARGET_HEIGHT - horizonY) - (y * TARGET_HEIGHT)) * scale);
    }

    /**
     * Adjust the z order of the specified sprite so that it is drawn according to its logical Z
     * coordinate relative to other sprites.
     */
    protected function adjustZOrder (sprite :MsoySprite, loc :MsoyLocation) :void
    {
        if (!contains(sprite)) {
            // this can happen if we're resized during editing as we
            // try to reposition a sprite that's still in our data structures
            // but that has been removed as a child.
            return;
        }

        var dex :int = getChildIndex(sprite);
        var newdex :int = dex;
        var z :Number;
        while (newdex > 0) {
            z = getZOfChildAt(newdex - 1);
            if (isNaN(z) || z >= loc.z) {
                break;
            }
            newdex--;
        }

        if (newdex == dex) {
            while (newdex < numChildren - 1) {
                if (getZOfChildAt(newdex + 1) <= loc.z) {
                    break;
                }
                newdex++;
            }
        }

        if (newdex != dex) {
            setChildIndex(sprite, newdex);
        }
    }

    /**
     * Convenience method to get the logical z coordinate of the child at the specified index.
     */
    protected function getZOfChildAt (index :int) :Number
    {
        var disp :DisplayObject = getChildAt(index);
        if (disp is MsoySprite) {
            var spr :MsoySprite = (disp as MsoySprite);
            if (!spr.isIncludedInLayout()) {
                return NaN;
            }
            return spr.loc.z;
        }
        return Number.MAX_VALUE;
    }

    /**
     */
    protected function setActive (map :HashMap, active :Boolean) :void
    {
        for each (var sprite :MsoySprite in map.values()) {
            sprite.setActive(active);
        }
    }

    /**
     * Shutdown all the sprites in the specified map.
     */
    protected function removeAll (map :HashMap) :void
    {
        for each (var sprite :MsoySprite in map.values()) {
            removeSprite(sprite);
        }
        map.clear();
    }

    protected function addFurni (furni :FurniData) :FurniSprite
    {
        var sprite :FurniSprite = _ctx.getMediaDirector().getFurni(furni);
        addChild(sprite);
        sprite.setLocation(furni.loc);
        _furni.put(furni.id, sprite);
        checkIsBackground(sprite);
        return sprite;
    }

    protected function updateFurni (furni :FurniData) :void
    {
        var sprite :FurniSprite = (_furni.get(furni.id) as FurniSprite);
        if (sprite != null) {
            sprite.update(furni);
            checkIsBackground(sprite);

        } else {
            addFurni(furni);
        }
    }

    protected function checkIsBackground (sprite :FurniSprite) :void
    {
        if (sprite.isBackground()) {
            _bkg = sprite;
            setChildIndex(_bkg, 0);
            locationUpdated(sprite);

        } else if (sprite == _bkg) {
            _bkg = null;
        }
    }

    /**
     * Remove the specified sprite from the view.
     */
    protected function removeSprite (sprite :MsoySprite) :void
    {
        removeChild(sprite);
        _ctx.getMediaDirector().returnSprite(sprite);

        if (sprite == _bkg) {
            _bkg = null;
        }
    }

    protected function updateDrawnRoom () :void
    {
        var g :Graphics = this.graphics;
        g.clear();

        // fill all our screen area with transparent pixels, so that
        // mousing anywhere in our bounds includes us in the
        // event dispatch. This is primarily necessary to get the
        // ContextMenu working properly.
        if (!isNaN(_actualWidth) && !isNaN(_actualHeight)) {
            var w :Number = _actualWidth / scaleX;
            var h :Number = _actualHeight / scaleY;
            g.beginFill(0, 0);
            g.drawRect(0, 0, w, h);
            g.endFill();
        }
 
        // TODO: this will move to metrics init method
        var minScale :Number = computeMinScale();
        var horizon :Number = 1 - _scene.getHorizon();
        var sceneWidth :int = _scene.getWidth();
        var backWallHeight :Number = TARGET_HEIGHT * minScale;

        var horizonY :Number = TARGET_HEIGHT * horizon;
        var backWallTop :Number = horizonY - (backWallHeight * horizon);
        var backWallBottom :Number = backWallTop + backWallHeight;
        setBackWallBottom(backWallBottom);

        var drawWalls :Boolean = (_scene.getSceneType() == MsoySceneModel.DRAWN_ROOM);
        var drawEdges :Boolean = drawWalls || _editing;
        if (!drawEdges) {
            return; // nothing to draw
        }

        var floorWidth :Number = (sceneWidth * minScale);
        var floorInset :Number = (sceneWidth - floorWidth) / 2;

        // rename a few things for ease of use below...
        var x1 :Number = floorInset;
        var x2 :Number = sceneWidth - floorInset;
        var y1 :Number = backWallTop;
        var y2 :Number = backWallBottom;

        if (drawWalls) {
            // fill in the floor
            g.beginFill(0x333333);
            g.moveTo(0, TARGET_HEIGHT);
            g.lineTo(x1, y2);
            g.lineTo(x2, y2);
            g.lineTo(sceneWidth, TARGET_HEIGHT);
            g.lineTo(0, TARGET_HEIGHT);
            g.endFill();

            // fill in the three walls
            g.beginFill(0x666666);
            g.moveTo(0, 0);
            g.lineTo(x1, y1);
            g.lineTo(x2, y1);
            g.lineTo(sceneWidth, 0);
            g.lineTo(sceneWidth, TARGET_HEIGHT);
            g.lineTo(x2, y2);
            g.lineTo(x1, y2);
            g.lineTo(0, TARGET_HEIGHT);
            g.lineTo(0, 0);
            g.endFill();

            // fill in the ceiling
            g.beginFill(0x999999);
            g.moveTo(0, 0);
            g.lineTo(x1, y1);
            g.lineTo(x2, y1);
            g.lineTo(sceneWidth, 0);
            g.lineTo(0, 0);
            g.endFill();

        } else {
            g.beginFill(0xFFFFFF);
            g.drawRect(0, 0, sceneWidth, TARGET_HEIGHT);
            g.endFill();
        }

        // draw the lines defining the walls
        if (drawEdges) {
            g.lineStyle(2);
            g.moveTo(0, 0);
            g.lineTo(x1, y1);
            g.lineTo(x2, y1);

            g.moveTo(sceneWidth, 0);
            g.lineTo(x2, y1);
            g.lineTo(x2, y2);

            g.moveTo(sceneWidth, TARGET_HEIGHT);
            g.lineTo(x2, y2);
            g.lineTo(x1, y2);

            g.moveTo(0, TARGET_HEIGHT);
            g.lineTo(x1, y2);
            g.lineTo(x1, y1);

            g.lineStyle(0, 0, 0); // stop drawing lines
        }
    }

    /** The msoy context. */
    protected var _ctx :WorldContext;

    /** The model of the current scene. */
    protected var _scene :MsoyScene;

    /** The transitory properties of the current scene. */
    protected var _roomObj :RoomObject;

    /** Our background sprite, if any. */
    protected var _bkg :FurniSprite;

    /** A map of id -> Furni. */
    protected var _furni :HashMap = new HashMap();

    /** A list of other sprites (used during editing). */
    protected var _otherSprites :Array = new Array();

    /** Are we editing the scene? */
    protected var _editing :Boolean = false;

    /** The actual screen width of this component. */
    protected var _actualWidth :Number;

    /** The actual screen height of this component. */
    protected var _actualHeight :Number;

    /** The focal length of our perspective rendering. */
    // This value (488) was chosen so that the standard depth (400) causes layout nearly identical
    // to the original perspective math.  So, it's purely historical, but we could choose a new
    // focal length and even a new standard scene depth.
    // TODO
    private static const FOCAL :Number = 488;

    private static const MAX_SCALE :Number = 1;
    private static const MAX_COORD :Number = 1;
    private static const PHI :Number = (1 + Math.sqrt(5)) / 2;

    private static const TARGET_WIDTH :Number = 800;
    protected static const TARGET_HEIGHT :Number = TARGET_WIDTH / PHI;
}
}
