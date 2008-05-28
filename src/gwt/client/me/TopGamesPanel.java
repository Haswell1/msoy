//
// $Id$

package client.me;

import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasAlignment;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.gwt.ui.SmartTable;
import com.threerings.gwt.ui.WidgetUtil;

import com.threerings.msoy.item.data.all.Game;
import com.threerings.msoy.item.data.all.MediaDesc;
import com.threerings.msoy.web.data.FeaturedGameInfo;

import client.games.CGames;
import client.games.GameBitsPanel;
import client.games.GameNamePanel;
import client.shell.Application;
import client.shell.Args;
import client.shell.Page;
import client.util.MediaUtil;
import client.util.MsoyUI;
import client.util.RoundBox;
import client.util.ThumbBox;

/**
 * Displays a summary of what Whirled is and calls to action.
 */
public class TopGamesPanel extends AbsolutePanel
{
    public TopGamesPanel ()
    {
        setStyleName("TopGamesPanel");
    }

    /**
     * Called when the list of featured games has been fetched.
     */
    public void setGames (FeaturedGameInfo[] games)
    {
        // take the first 5 featured games maximum
        if (games.length > 5) {
           _games = new FeaturedGameInfo[5];
           for (int i = 0; i < 5; i++) {
               _games[i] = games[i];
           }
        }
        else {
            _games = games;
        }

        // game info panel
        final RoundBox gameInfoBox = new RoundBox(RoundBox.BLUE);
        _gameInfo = new SimplePanel();
        _gameInfo.setStyleName("GameInfo");
        gameInfoBox.add(_gameInfo);
        add(gameInfoBox, 0, 0);

        // top games list
        _topGamesTable = new SmartTable(0, 0);
        for (int i = 0; i < _games.length; i++) {
            _topGamesTable.setWidget(i, 0, createGameListItem(i));
        }
        add(_topGamesTable, 445, 25);

        // top games header
        FlowPanel topGamesHeader = new FlowPanel();
        topGamesHeader.setStyleName("TopGamesHeader");
        add(topGamesHeader, 445, 0);

        showGame(0);
    }

    /**
     * Create and return a panel with the game thumbnail and name
     */
    protected Widget createGameListItem (final int index)
    {
        FeaturedGameInfo game = _games[index];

        // Outer panel with onclick - change the game
        FocusPanel gamePanel = new FocusPanel();
        gamePanel.setStyleName("GameListItem");
        ClickListener gameClick = new ClickListener() {
            public void onClick (Widget sender) {
                showGame(index);
            }
        };
        gamePanel.addClickListener(gameClick);

        // Inner flow panel with thumbnail and name
        SmartTable gamePanelInner = new SmartTable(0, 0);

        SimplePanel thumbnail = new SimplePanel();
        Widget thumbnailImage = MediaUtil.createMediaView(
            game.thumbMedia, MediaDesc.HALF_THUMBNAIL_SIZE);
        thumbnail.setStyleName("Thumbnail");
        thumbnail.add(thumbnailImage);
        gamePanelInner.setWidget(0, 0, thumbnail);
        gamePanelInner.getFlexCellFormatter().setHorizontalAlignment(
            0, 0, HasAlignment.ALIGN_CENTER);
        gamePanelInner.getFlexCellFormatter().setVerticalAlignment(0, 0, HasAlignment.ALIGN_MIDDLE);

        SimplePanel name = new SimplePanel();
        name.setStyleName("Name");
        name.add(new HTML(game.name));
        gamePanelInner.setWidget(0, 1, name);
        gamePanelInner.getFlexCellFormatter().setVerticalAlignment(0, 1, HasAlignment.ALIGN_MIDDLE);

        gamePanel.add(gamePanelInner);

        return gamePanel;
    }

    /**
     * Select and display one of the featured games.
     */
    protected void showGame (final int index)
    {
        final FeaturedGameInfo game = _games[index];

        // select the game being shown
        for (int i = 0; i < _games.length; i++) {
            FocusPanel gamePanel = (FocusPanel)_topGamesTable.getWidget(i, 0);
            if (i == index) {
                gamePanel.setStyleName("GameListItemSelected");
            }
            else {
                gamePanel.setStyleName("GameListItem");
            }
        }

        SmartTable gameInfoTable = new SmartTable("featuredGame", 0, 0);

        ClickListener onClick = Application.createLinkListener(
            Page.GAMES, Args.compose("d", game.gameId));

        VerticalPanel left = new VerticalPanel();
        left.setHorizontalAlignment(HasAlignment.ALIGN_CENTER);
        left.add(new ThumbBox(game.getShotMedia(), Game.SHOT_WIDTH, Game.SHOT_HEIGHT, onClick));
        left.add(WidgetUtil.makeShim(10, 10));
        left.add(new GameBitsPanel(game.minPlayers, game.maxPlayers, game.avgDuration, 0));

        // left and right arrows
        left.add(WidgetUtil.makeShim(10, 10));
        left.add(MsoyUI.createPrevNextButtons(new ClickListener() {
            public void onClick (Widget sender) {
                showGame((index+_games.length-1)%_games.length);
            }
        }, new ClickListener() {
            public void onClick (Widget sender) {
                showGame((index+1)%_games.length);
            }
        }));
        left.add(WidgetUtil.makeShim(10, 10));

        // more games button
        Widget moreGamesButton = new Button(
            CMe.msgs.landingMoreGames(), Application.createLinkListener(Page.GAMES, ""));
        left.add(moreGamesButton);

        gameInfoTable.setWidget(0, 0, left);
        gameInfoTable.getFlexCellFormatter().setVerticalAlignment(0, 0, HasAlignment.ALIGN_TOP);
        gameInfoTable.getFlexCellFormatter().setWidth(0, 0, Game.SHOT_WIDTH + "px");
        gameInfoTable.setWidget(0, 1, WidgetUtil.makeShim(10, 10));

        // game text info on the right
        VerticalPanel right = new GameNamePanel(
            game.name, game.genre, game.creator, game.description);
        right.add(WidgetUtil.makeShim(5, 10));

        // display single only if no multi exists
        FlowPanel playPanel = new FlowPanel();
        if (game.maxPlayers == 1) {
            PushButton singleButton = makePlayButton("SinglePlay", new ClickListener() {
                public void onClick (Widget sender) {
                    Application.go(Page.WORLD, Args.compose("game", "s", "" + game.gameId));
                }
            });
            playPanel.add(singleButton);
        }
        else {
            PushButton multiButton = makePlayButton("FriendPlay", new ClickListener() {
                public void onClick (Widget sender) {
                    Application.go(Page.WORLD, Args.compose("game", "l", "" + game.gameId));
                }
            });
            playPanel.add(multiButton);
        }
        right.add(playPanel);

        if (game.playersOnline > 0) {
            right.add(WidgetUtil.makeShim(10, 10));
            right.add(MsoyUI.createLabel(
                CGames.msgs.featuredOnline("" + game.playersOnline), "Online"));
        }

        gameInfoTable.setWidget(0, 2, right);
        gameInfoTable.getFlexCellFormatter().setVerticalAlignment(0, 2, HasAlignment.ALIGN_TOP);
        gameInfoTable.setWidget(1, 0, WidgetUtil.makeShim(5, 5));
        gameInfoTable.getFlexCellFormatter().setColSpan(1, 0, 3);

        // replace the game details
        if (_gameInfo.getWidget() != null) {
            _gameInfo.remove(_gameInfo.getWidget());
        }
        _gameInfo.add(gameInfoTable);
    }

    /**
     * Create a new play button with the given style and onclick event
     */
    protected PushButton makePlayButton (String styleName, ClickListener onClick)
    {
        PushButton play = new PushButton("", onClick);
        play.setStyleName(styleName);
        play.addStyleName("PlayButton");
        return play;
    }

    /** Game data */
    protected FeaturedGameInfo[] _games;

    /** Game list table; every row is a game whose class changes when selected */
    protected SmartTable _topGamesTable;

    /** Game info panel; changes when a new game is selected */
    protected SimplePanel _gameInfo;
}
