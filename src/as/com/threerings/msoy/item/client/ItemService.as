//
// $Id$

package com.threerings.msoy.item.client {

import com.threerings.presents.client.Client;
import com.threerings.presents.client.InvocationService;
import com.threerings.presents.client.InvocationService_ResultListener;

/**
 * Provides services related to items.
 */
public interface ItemService extends InvocationService
{
    /**
     * Get the items in the user's inventory.
     * TODO: WTF? Can we ever load the inventory? At best we can display
     *       a page of it.
     */
    function getInventory (
        client :Client, type :String,
        listener :InvocationService_ResultListener) :void;
}
}
