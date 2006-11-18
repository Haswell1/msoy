//
// $Id$

package com.threerings.msoy.web.server;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

import com.threerings.msoy.server.MsoyServer;

import com.threerings.msoy.web.client.MemberService;
import com.threerings.msoy.web.data.MemberGName;
import com.threerings.msoy.web.data.Neighborhood;
import com.threerings.msoy.web.data.ServiceException;
import com.threerings.msoy.web.data.WebCreds;

/**
 * Provides the server implementation of {@link MemberService}.
 */
public class MemberServlet extends RemoteServiceServlet
    implements MemberService
{
    // from MemberService
    public MemberGName getName (int memberId)
        throws ServiceException
    {
        ServletWaiter<MemberGName> waiter =
            new ServletWaiter<MemberGName>("getName[" + memberId + "]");
        MsoyServer.memberMan.getName(memberId, waiter);
        return waiter.waitForResult();
    }

    // from MemberService
    public void inviteFriend (WebCreds creds, int friendId)
        throws ServiceException
    {
        ServletWaiter<Void> waiter = new ServletWaiter<Void>("inviteFriend[" + friendId + "]");
        MsoyServer.memberMan.alterFriend(creds.memberId, friendId, true, waiter);
        waiter.waitForResult();
    }

    // from MemberService
    public void acceptFriend (WebCreds creds, int friendId)
        throws ServiceException
    {
        ServletWaiter<Void> waiter = new ServletWaiter<Void>("acceptFriend[" + friendId + "]");
        MsoyServer.memberMan.alterFriend(creds.memberId, friendId, true, waiter);
        waiter.waitForResult();
    }

    // from MemberService
    public void declineFriend (WebCreds creds, int friendId)
        throws ServiceException
    {
        ServletWaiter<Void> waiter = new ServletWaiter<Void>("declineFriend[" + friendId + "]");
        MsoyServer.memberMan.alterFriend(creds.memberId, friendId, false, waiter);
        waiter.waitForResult();
    }
    
    // from MemberService
    public Neighborhood getNeighborhood (WebCreds creds, int memberId)
        throws ServiceException
    {
        ServletWaiter<Neighborhood> waiter =
            new ServletWaiter<Neighborhood>("getNeighborhood[" + memberId + "]");
        MsoyServer.memberMan.getNeighborhood(memberId, 1, 500, waiter);
        return waiter.waitForResult();

    }
}
