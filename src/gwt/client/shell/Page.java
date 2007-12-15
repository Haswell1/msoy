//
// $Id$

package client.shell;

import java.util.ArrayList;

import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasAlignment;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.msoy.web.data.WebCreds;

import client.util.MsoyUI;

/**
 * Handles some standard services for a top-level MetaSOY page.
 */
public abstract class Page
{
    /** Used to dynamically create the appropriate page when we are loaded. */
    public static interface Creator {
        public Page createPage ();
    }

    // constants for our various pages
    public static final String ADMIN = "admin";
    public static final String CATALOG = "catalog";
    public static final String GAME = "game";
    public static final String GROUP = "group";
    public static final String INVENTORY = "inventory";
    public static final String MAIL = "mail";
    public static final String PROFILE = "profile";
    public static final String SWIFTLY = "swiftly";
    public static final String WHIRLED = "whirled";
    public static final String WORLD = "world";
    public static final String WRAP = "wrap";

    /**
     * Called when the page is first resolved to initialize its bits.
     */
    public void init ()
    {
        // initialize our services and translations
        initContext();
    }

    /**
     * Called when the user has navigated to this page. A call will immediately follow to {@link
     * #onHistoryChanged} with the arguments passed to this page or the empty string if no
     * arguments were supplied.
     */
    public void onPageLoad ()
    {
    }

    /**
     * Called when the user navigates to this page for the first time, and when they follow {@link
     * Application#createLink} links within tihs page.
     */
    public abstract void onHistoryChanged (Args args);

    /**
     * Called when the user navigates away from this page to another page. Gives the page a chance
     * to shut anything down before its UI is removed from the DOM.
     */
    public void onPageUnload ()
    {
    }

    /**
     * Sets the title of the browser window and the page (displayed below the Whirled logo).
     */
    public void setPageTitle (String title)
    {
        setPageTitle(title, null);
    }

    /**
     * Sets the title and subtitle of the browser window and the page. The subtitle is displayed to
     * the right of the title in the page and tacked onto the title for the browser window.
     */
    public void setPageTitle (String title, String subtitle)
    {
        if (_tabs == null) {
            createContentContainer();
        }
        _tabs.setText(0, 0, title);
        if (subtitle != null) {
            _tabs.setText(0, 1, subtitle);
            title += " - " + subtitle;
        } else {
            _tabs.setHTML(0, 1, "&nbsp;");
        }
        Window.setTitle(CShell.cmsgs.windowTitle(title));
    }

    /**
     * Requests that the specified widget be scrolled into view.
     */
    public void ensureVisible (Widget widget)
    {
        if (_scroller != null) {
            _scroller.ensureVisible(widget);
        }
    }

    /**
     * Called during initialization to give our entry point and derived classes a chance to
     * initialize their respective context classes.
     */
    protected void initContext ()
    {
    }

    /**
     * Returns the identifier of this page (used for navigation).
     */
    protected abstract String getPageId ();

    /**
     * Returns the content widget last configured with {@link #setContent}.
     */
    protected Widget getContent ()
    {
        return (_content != null && _content.isCellPresent(1, 0)) ? _content.getWidget(1, 0) : null;
    }

    /**
     * Clears out any existing content, creates a new Flash object from the definition, and sets it
     * as the new main page content. Returns the newly-created content as a widget.
     */
    protected HTML setFlashContent (String definition)
    {
        // Please note: the following is a work-around for an IE7 bug. If we create a Flash object
        // node *before* attaching it to the DOM tree, IE will silently fail to register the Flash
        // object's callback functions for access from JavaScript. To make this work, create an
        // empty node first, add it to the DOM tree, and then initialize it with the Flash object
        // definition.  Also see: WidgetUtil.embedFlashObject()
        HTML control = new HTML();
        setContent(control, false);
        control.setHTML(definition);
        return control;
    }

    /**
     * Clears out any existing content and sets the specified widget as the main page content.
     */
    protected void setContent (Widget content)
    {
        setContent(content, false);
    }

    /**
     * Clears out any existing content and sets the specified widget as the main page content.
     */
    protected void setContent (Widget content, boolean contentIsJava)
    {
        // create our content container if need be
        if (_content == null) {
            createContentContainer();
        }

        // display our content in the frame (inside a scroll panel)
        Frame.setContent(_content, contentIsJava);
        _content.setWidget(1, 0, _scroller = new ScrollPanel(content));
        _scroller.setHeight((Window.getClientHeight() - 70) + "px");

        // if there isn't anything in the tabs/subtitle area, we need something there to cause IE
        // to properly use up the space
        if (_tabs.getWidget(0, 1) == null && _tabs.getText(0, 1).length() == 0) {
            _tabs.setHTML(0, 1, "&nbsp;");
        }
    }

    protected void createContentContainer ()
    {
        _content = new FlexTable();
        _content.setCellPadding(0);
        _content.setCellSpacing(0);
        _content.setWidth("100%");
        _content.setHeight("100%");

        // a separate table for this entire row, so that
        // we can set individual cell widths correctly
        _tabs = new FlexTable();
        _tabs.setCellPadding(0);
        _tabs.setCellSpacing(0);
        _tabs.setWidth("100%");
        _tabs.getFlexCellFormatter().setStyleName(0, 0, "pageHeaderTitle");
        _tabs.getFlexCellFormatter().setStyleName(0, 1, "pageHeaderContent");
        _content.setWidget(0, 0, _tabs);

        _content.getFlexCellFormatter().setHeight(1, 0, "100%");
        _content.getFlexCellFormatter().setVerticalAlignment(1, 0, HasAlignment.ALIGN_TOP);
    }

    protected void setPageTabs (Widget tabs)
    {
        if (_tabs == null) {
            createContentContainer();
        }
        _tabs.setWidget(0, 1, tabs);
    }

    /**
     * Called when we the player logs on while viewing this page. The default implementation
     * redisplays the current page with the current args (by calling {@link #onHistoryChanged}.
     */
    protected void didLogon (WebCreds creds)
    {
        History.onHistoryChanged(History.getToken());
    }

    /**
     * Called when the player logs off while viewing this page. The default implementation
     * redisplays the current page with the current args (by calling {@link #onHistoryChanged}).
     */
    protected void didLogoff ()
    {
        History.onHistoryChanged(History.getToken());
    }

    protected ScrollPanel _scroller;
    protected FlexTable _content, _tabs;
}
