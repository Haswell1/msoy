//
// $Id$

package com.threerings.msoy.web.server;

import java.io.IOException;
import java.io.PrintStream;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.samskivert.io.StreamUtil;

import com.threerings.msoy.server.ServerConfig;
import com.threerings.msoy.web.data.LaunchConfig;
import com.threerings.msoy.web.data.ServiceException;

import static com.threerings.msoy.Log.log;

/**
 * A simple servlet that tells the Flash embed client where to find games and rooms because it
 * can't rely on the GWT client to do so.
 */
public class EmbedRouterServlet extends HttpServlet
{
    @Override // from HttpServlet
    protected void doGet (HttpServletRequest req, HttpServletResponse rsp)
        throws IOException
    {
        String info = (req.getPathInfo() == null) ? "" : req.getPathInfo();
        String host;
        int port;

        try {
            if (info.startsWith("/g")) {
                int gameId = Integer.parseInt(info.substring(2));
                LaunchConfig config = ServletUtil.loadLaunchConfig(null, gameId);
                sendResponse(rsp, config.server + ":" + config.port + ":" + config.guestId);

            } else if (info.startsWith("/s")) {
                int sceneId = Integer.parseInt(info.substring(2));
                // TODO: if someone is already hosting this scene, send them directly there
                sendResponse(rsp, ServerConfig.serverHost + ":" + ServerConfig.serverPorts[0]);

            } else {
                sendResponse(rsp, ServerConfig.serverHost + ":" + ServerConfig.serverPorts[0]);
            }

        } catch (ServiceException e) {
            log.warning("Failed to provide host to embedded client [info=" + info +
                        ", error=" + e + "].");
            rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    protected void sendResponse (HttpServletResponse rsp, String response)
        throws IOException
    {
        PrintStream out = null;
        try {
            out = new PrintStream(rsp.getOutputStream());
            out.println(response);
        } finally {
            StreamUtil.close(out);
        }
    }
}
