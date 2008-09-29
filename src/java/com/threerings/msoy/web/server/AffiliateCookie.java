//
// $Id$

package com.threerings.msoy.web.server;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.samskivert.util.StringUtil;

import com.samskivert.servlet.util.CookieUtil;

import com.threerings.msoy.web.data.TrackingCookieUtil;

/**
 * Handles the getting and setting of the affiliate cookie.
 */
public class AffiliateCookie
{
    /** The cookie name. */
    public static final String NAME = "a";

    /**
     * Return the affiliate cookie value.
     */
    public static String get (HttpServletRequest req)
    {
        return TrackingCookieUtil.decode(
            StringUtil.unhexlate(CookieUtil.getCookieValue(req, NAME)));
    }

    /**
     * Stores a new Affiliate cookie with the specified value.
     */
    public static void set (HttpServletResponse rsp, String affiliate)
    {
        Cookie cookie = new Cookie(NAME,
            StringUtil.hexlate(TrackingCookieUtil.encode(affiliate.trim())));
        cookie.setMaxAge(365 * 24 * 60 * 60); // 1 year
        cookie.setPath("/");
        rsp.addCookie(cookie);
    }

    /**
     * Clear the cookie.
     */
    public static void clear (HttpServletResponse rsp)
    {
        CookieUtil.clearCookie(rsp, NAME);
    }
}
