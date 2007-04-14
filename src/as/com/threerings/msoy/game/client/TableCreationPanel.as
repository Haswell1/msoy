package com.threerings.msoy.game.client {

import mx.core.ScrollPolicy;

import mx.containers.HBox;
import mx.containers.VBox;

import com.threerings.flex.CommandButton;
import com.threerings.util.CommandEvent;

import com.threerings.parlor.data.TableConfig;

import com.threerings.parlor.client.TableConfigurator;
import com.threerings.parlor.client.DefaultFlexTableConfigurator;

import com.threerings.parlor.game.data.GameConfig;

import com.threerings.parlor.game.client.GameConfigurator;

import com.threerings.ezgame.client.EZGameConfigurator;

import com.threerings.msoy.client.Msgs;
import com.threerings.msoy.client.WorldContext;

import com.threerings.msoy.item.web.Game;

import com.threerings.msoy.game.data.MsoyGameConfig;
import com.threerings.msoy.game.data.GameDefinition;

public class TableCreationPanel extends HBox
{
    public function TableCreationPanel (ctx :WorldContext, panel :LobbyPanel)
    {
        _ctx = ctx;
        _game = panel.getGame();
        _panel = panel;
    }

    public function getCreateButton () :CommandButton
    {
        return _createBtn;
    }

    override protected function createChildren () :void
    {
        styleName = "tableCreationPanel";
        percentWidth = 100;
        percentHeight = 100;

        var gconf :EZGameConfigurator = new EZGameConfigurator();
        var gconfigger :GameConfigurator = gconf;
        gconfigger.init(_ctx);
        var gameDef :GameDefinition = _game.getGameDefinition();
        gconf.setXMLConfig(gameDef.config);
        var tconfigger :TableConfigurator;
        if (gameDef.gameType == GameConfig.PARTY) {
            tconfigger = new DefaultFlexTableConfigurator(-1, -1, -1, true);
        } else if (gameDef.gameType == GameConfig.SEATED_GAME)  {
            // using min_seats for start_seats until we put start_seats in the configuration
            tconfigger = new DefaultFlexTableConfigurator(gameDef.minSeats, gameDef.minSeats,
                gameDef.maxSeats, true, Msgs.GAME.get("l.players"), Msgs.GAME.get("l.private"));
        } else { 
            Log.getLog(this).warning("<match type='" + gameDef.gameType + "'> is not a valid type");
            return;
        }
        tconfigger.init(_ctx, gconfigger);

        var config :MsoyGameConfig = new MsoyGameConfig();
        config.gameMedia = _game.gameMedia.getMediaPath();
        config.persistentGameId = _game.getPrototypeId();
        config.gameType = gameDef.gameType;
        gconf.setGameConfig(config);

        _createBtn = new CommandButton();
        _createBtn.setFunction(function () :void {
            CommandEvent.dispatch(_panel, LobbyController.SUBMIT_TABLE,
                [ tconfigger.getTableConfig(), gconfigger.getGameConfig() ]);
        });
        _createBtn.label = Msgs.GAME.get("b.create");
        var btnBox :HBox = new HBox();
        btnBox.percentWidth = 100;
        btnBox.percentHeight = 100;
        btnBox.setStyle("verticalAlign", "middle");
        btnBox.setStyle("horizontalAlign", "center");
        btnBox.addChild(_createBtn);
        gconf.getContainer().addChild(btnBox);

        addChild(gconf.getContainer());
    }

    protected var _ctx :WorldContext;

    /** The game item, for configuration reference. */
    protected var _game :Game;

    /** The lobby panel we're in. */
    protected var _panel :LobbyPanel;

    protected var _createBtn :CommandButton;
}
}
