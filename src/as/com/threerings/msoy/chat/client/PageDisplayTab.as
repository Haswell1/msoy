//
// $Id$

package com.threerings.msoy.chat.client {

import flash.events.Event;
import flash.events.HTTPStatusEvent;
import flash.events.IOErrorEvent;
import flash.events.SecurityErrorEvent;
import flash.events.TextEvent;
import flash.net.URLLoader;
import flash.net.URLRequest;

import mx.controls.Text;
import mx.events.ResizeEvent;

import com.threerings.msoy.client.ControlBar;
import com.threerings.msoy.client.HeaderBar;
import com.threerings.msoy.client.TopPanel;
import com.threerings.msoy.client.WorldContext;

/**
 * Displays a tab page with arbitrary HTML source, in the same space as other chat tabs.
 */
// FIXME ROBERT: This class is a work in progress
public class PageDisplayTab extends ChatTab
{
    public var tabName :String;

    public function PageDisplayTab (ctx :WorldContext, tabName :String)
    {
        super(ctx);
        this.tabName = tabName;
     
        _pageLoader = new URLLoader();
        _page = new Text();
    }

    public function init () :void
    {
        addEventListener(ResizeEvent.RESIZE, checkSizes);
        
        _pageLoader.addEventListener(Event.OPEN, loadStarted);
        _pageLoader.addEventListener(Event.COMPLETE, loadComplete);
        _pageLoader.addEventListener(HTTPStatusEvent.HTTP_STATUS, handleHttpStatusCode);
        _pageLoader.addEventListener(IOErrorEvent.IO_ERROR, handleIOError);
        _pageLoader.addEventListener(SecurityErrorEvent.SECURITY_ERROR, handleSecurityError);

        _page.addEventListener(TextEvent.LINK, handleLinkClick);
    }

    public function shutdown () :void
    {
        removeEventListener(ResizeEvent.RESIZE, checkSizes);

        _pageLoader.removeEventListener(Event.OPEN, loadStarted);
        _pageLoader.removeEventListener(Event.COMPLETE, loadComplete);
        _pageLoader.removeEventListener(HTTPStatusEvent.HTTP_STATUS, handleHttpStatusCode);
        _pageLoader.removeEventListener(IOErrorEvent.IO_ERROR, handleIOError);
        _pageLoader.removeEventListener(SecurityErrorEvent.SECURITY_ERROR, handleSecurityError);

        _page.removeEventListener(TextEvent.LINK, handleLinkClick);
    }

    /** Starts loading the specified URL. */
    public function loadUrl (url :String) :void
    {
        // shut down any operation already in progress
        try {
            _pageLoader.close();
        } catch (e: Error) {
            // no op. i'm just catching errors caused by closing an already closed stream.
            // this wouldn't be necessary if I could just ask the loader's stream was already
            // closed. but no, that would be too easy. 
        }

        trace("LOAD URL: " + url);
        
        // get a new page!
        _pageLoader.load(new URLRequest(url));
    }        

    // from ChatTab
    override public function sendChat (message :String) :void
    {
        // do nothing - help pages don't have chat channels hooked up to them
    }

    // from Container
    override protected function createChildren () :void
    {
        super.createChildren();

        _page.text = "";
        addChild(_page);
    }

    // from Container
    override protected function childrenCreated () :void
    {
        super.childrenCreated();
        
        checkSizes();
    }

    // EVENT HANDLERS
    
    protected function checkSizes (... ignored) :void
    {
        // only try this if the container is ready
        if (stage != null) {
            var height :int = stage.stageHeight - ControlBar.HEIGHT - HeaderBar.HEIGHT -
                TopPanel.DECORATIVE_MARGIN_HEIGHT;
            
            // we don't have our proper width yet (the stage has not yet resized), so we have to
            // hardcode the width to the one we know we'll be receiving
            _page.width = TopPanel.RIGHT_SIDEBAR_WIDTH;
            _page.height = height;
            _page.move(0, 0);
        }
    }

    protected function loadStarted (event :Event) :void 
    {
        trace("loadStarted: " + event);
    }

    protected function loadComplete (event :Event) :void 
    {
        trace("loadComplete: " + event);
        _page.htmlText = String(_pageLoader.data);
    }

    protected function handleHttpStatusCode (event :HTTPStatusEvent) :void
    {
        trace("handleHttpStatusCode: " + event);
    }

    protected function handleIOError (event :IOErrorEvent) :void
    {
        trace("handleIOError: " + event);
    }        
    
    protected function handleSecurityError (event :SecurityErrorEvent) :void
    {
        trace("handleSecurityError: " + event);
    }        
    
    protected function handleLinkClick (event :TextEvent) :void
    {
        trace("handleLinkClick: " + event);
    }
    
    protected var _page :Text;
    protected var _pageLoader :URLLoader;
}
}
