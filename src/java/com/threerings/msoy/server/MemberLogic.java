//
// $Id$

package com.threerings.msoy.server;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.samskivert.depot.DuplicateKeyException;
import com.samskivert.util.ArrayIntSet;
import com.samskivert.util.ArrayUtil;
import com.samskivert.util.IntSet;

import com.threerings.presents.annotation.BlockingThread;
import com.threerings.presents.server.PresentsDObjectMgr;
import com.threerings.stats.server.persist.StatRepository;

import com.whirled.game.server.persist.GameCookieRepository;

import com.threerings.msoy.peer.server.MemberNodeAction;
import com.threerings.msoy.peer.server.MsoyPeerManager;
import com.threerings.msoy.web.gwt.Args;
import com.threerings.msoy.web.gwt.MemberCard;
import com.threerings.msoy.web.gwt.Pages;
import com.threerings.msoy.web.gwt.ServiceCodes;
import com.threerings.msoy.web.gwt.ServiceException;

import com.threerings.msoy.admin.server.persist.ABTestRecord;
import com.threerings.msoy.admin.server.persist.ABTestRepository;
import com.threerings.msoy.avrg.server.persist.AVRGameRepository;
import com.threerings.msoy.badge.data.all.InProgressBadge;
import com.threerings.msoy.badge.server.BadgeLogic;
import com.threerings.msoy.badge.server.persist.BadgeRepository;
import com.threerings.msoy.comment.server.persist.CommentRepository;
import com.threerings.msoy.fora.server.persist.ForumRepository;
import com.threerings.msoy.game.data.all.GameGenre;
import com.threerings.msoy.game.server.PlayerNodeActions;
import com.threerings.msoy.game.server.persist.GameInfoRecord;
import com.threerings.msoy.game.server.persist.MsoyGameRepository;
import com.threerings.msoy.game.server.persist.TrophyRepository;
import com.threerings.msoy.group.server.persist.GroupRecord;
import com.threerings.msoy.group.server.persist.GroupRepository;
import com.threerings.msoy.group.server.persist.MedalRepository;
import com.threerings.msoy.item.server.ItemLogic;
import com.threerings.msoy.item.server.persist.FavoritesRepository;
import com.threerings.msoy.item.server.persist.ItemRecord;
import com.threerings.msoy.item.server.persist.ItemRepository;
import com.threerings.msoy.mail.server.MailLogic;
import com.threerings.msoy.mail.server.persist.MailRepository;
import com.threerings.msoy.money.data.all.Currency;
import com.threerings.msoy.money.data.all.TransactionType;
import com.threerings.msoy.money.server.MoneyLogic;
import com.threerings.msoy.money.server.persist.MoneyRepository;
import com.threerings.msoy.person.gwt.FeedMessageType;
import com.threerings.msoy.person.server.persist.FeedRepository;
import com.threerings.msoy.person.server.persist.GalleryRepository;
import com.threerings.msoy.person.server.persist.ProfileRepository;
import com.threerings.msoy.room.data.MsoySceneModel;
import com.threerings.msoy.room.data.RoomCodes;
import com.threerings.msoy.room.server.persist.MsoySceneRepository;
import com.threerings.msoy.room.server.persist.SceneRecord;

import com.threerings.msoy.data.AVRGameNavItemData;
import com.threerings.msoy.data.BasicNavItemData;
import com.threerings.msoy.data.GwtPageNavItemData;
import com.threerings.msoy.data.HomePageItem;
import com.threerings.msoy.data.MemberExperience;
import com.threerings.msoy.data.MemberObject;
import com.threerings.msoy.data.MsoyAuthCodes;
import com.threerings.msoy.data.StatType;
import com.threerings.msoy.data.UserAction;
import com.threerings.msoy.data.all.DeploymentConfig;
import com.threerings.msoy.data.all.FriendEntry;
import com.threerings.msoy.data.all.MediaDesc;
import com.threerings.msoy.data.all.MemberName;
import com.threerings.msoy.data.all.NavItemData;
import com.threerings.msoy.data.all.StaticMediaDesc;
import com.threerings.msoy.data.all.VisitorInfo;
import com.threerings.msoy.data.all.VizMemberName;
import com.threerings.msoy.server.persist.MemberExperienceRecord;
import com.threerings.msoy.server.persist.MemberRecord;
import com.threerings.msoy.server.persist.MemberRepository;
import com.threerings.msoy.server.persist.MsoyOOOUserRepository;
import com.threerings.msoy.server.persist.UserActionRepository;

import static com.threerings.msoy.Log.log;

/**
 * Contains member related services that are used by servlets and other blocking thread code.
 */
@BlockingThread @Singleton
public class MemberLogic
{
    /**
     * Looks up the home scene id of the specified entity (member or group).
     *
     * @return the id if the entity was found, null otherwise.
     */
    public Integer getHomeId (byte ownerType, int ownerId)
    {
        switch (ownerType) {
        case MsoySceneModel.OWNER_TYPE_MEMBER:
            MemberRecord member = _memberRepo.loadMember(ownerId);
            return (member == null) ? null : member.homeSceneId;

        case MsoySceneModel.OWNER_TYPE_GROUP:
            GroupRecord group = _groupRepo.loadGroup(ownerId);
            return (group == null) ? null : group.homeSceneId;

        default:
            log.warning("Unknown ownerType provided to getHomeId", "ownerType", ownerType,
                        "ownerId", ownerId);
            return null;
        }
    }

    /**
     * @see #establishFriendship(int, int)
     * TODO: will this be needed or was it just here for convenience?
     */
    public void establishFriendship (MemberRecord caller, int friendId)
        throws ServiceException
    {
        establishFriendship(caller.memberId, friendId);
    }

    /**
     * Establishes a friendship between the supplied two members. This handles updating the
     * respective members' stats, publishing to the feed and notifying the dobj runtime system.
     */
    public void establishFriendship (int memberId, int friendId)
        throws ServiceException
    {
        if (memberId == friendId) {
            // Let's just say that people are implicitly their own friends
            return;
        }
        try {
            MemberCard friend = _memberRepo.noteFriendship(memberId, friendId);
            if (friend == null) {
                throw new ServiceException(MsoyAuthCodes.NO_SUCH_USER);
            }

            // update the FRIENDS_MADE statistic for both players
            _statLogic.incrementStat(memberId, StatType.FRIENDS_MADE, 1);
            _statLogic.incrementStat(friendId, StatType.FRIENDS_MADE, 1);

            // publish a message to the inviting member's feed
            String data = friend.name.toString() + "\t" + friendId + "\t"
                + MediaDesc.mdToString(friend.photo);
            _feedRepo.publishMemberMessage(
                memberId, FeedMessageType.FRIEND_ADDED_FRIEND, data);

            // add them to the friends list of both parties if/wherever they are online
            // TODO: Review and optimize
            MemberCard ccard = _memberRepo.loadMemberCard(memberId, true);
            _peerMan.invokeNodeAction(new AddFriend(memberId, friend));
            _peerMan.invokeNodeAction(new AddFriend(friendId, ccard));

            // note the happy event in the log
            _eventLog.friendAdded(memberId, friendId);

        } catch (DuplicateKeyException dke) {
            // no problem, just fall through and pretend like things succeeded (we'll have skipped
            // all the announcing and stat fiddling and whatnot)
        }
    }

    /**
     * Enact the specified mute. The muter is not checked for validity, the mutee IS on mutes,
     * to make sure they're not an admin.
     */
    public void setMuted (int muterId, int muteeId, boolean muted)
        throws ServiceException
    {
        if (muted) {
            MemberRecord muteeRec = _memberRepo.loadMember(muteeId);
            if (muteeRec == null) {
                throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
            }
            if (muteeRec.isSupport()) {
                throw new ServiceException("e.cant_mute_staff");
            }
        }
        _memberRepo.setMuted(muterId, muteeId, muted);
    }

    /**
     * Clears the friendship between the two specified parties.
     */
    public void clearFriendship (int removerId, int friendId)
        throws ServiceException
    {
        // clear their friendship in the database
        _memberRepo.clearFriendship(removerId, friendId);

        // remove them from the friends list of both parties, wherever they are online
        _peerMan.invokeNodeAction(new RemoveFriend(removerId, friendId));
        _peerMan.invokeNodeAction(new RemoveFriend(friendId, removerId));

        // update the FRIENDS_MADE statistic for both players
        _statLogic.incrementStat(removerId, StatType.FRIENDS_MADE, -1);
        _statLogic.incrementStat(friendId, StatType.FRIENDS_MADE, -1);

        // note the sad event in the log
        _eventLog.friendRemoved(removerId, friendId);
    }

    /**
     * Set the display name for the specified member.
     * @throws ServiceException if the name is invalid, for some reason.
     */
    public void setDisplayName (final int memberId, final String name, boolean allowSupportNames)
        throws ServiceException
    {
        validateDisplayName(name, allowSupportNames);

        // save it to the db
        _memberRepo.configureDisplayName(memberId, name);

        // update runtime representations
        _omgr.postRunnable(new Runnable() {
            public void run () {
               MemberNodeActions.infoChanged(memberId, name, null, null);
               _playerActions.displayNameUpdated(new MemberName(name, memberId));
            }
        });
    }

    /**
     * Return the a/b test group that a member or visitor belongs to for a given a/b test,
     * generated psudo-randomly based on their tracking ID and the test name.  If the visitor is
     * not eligible for the a/b test, return < 0.
     *
     * @param testName String identifier for the test
     * @param logEvent If true, track that this visitor was added to this group
     *
     * @return The a/b group the visitor has been assigned to, or < 0 for no group.
     */
    public int getABTestGroup (String testName, VisitorInfo info, boolean logEvent)
    {
        if (info == null) { // sanity check
            log.warning("Received bogus AB test group request", "name", testName, "info", info,
                        "logEvent", logEvent);
            return -1;
        }

        ABTestRecord test = null;
        try {
            test = _testRepo.loadTestByName(testName);
            if (test == null) {
                log.warning("Unknown A/B Test in getABTestGroup", "name", testName);
                return -1;
            }
        } catch (Exception e) {
            log.warning("Failed to load A/B Test", "name", testName, e);
            return -1;
        }

        // test is not running
        if (test.enabled == false) {
            return -1;
        }

        // generate the group number based on trackingID + testName
        int group = test.toCard().getGroup(info);

        // optionally log an event to say the group was assigned
        if (logEvent && group >= 0) {
            _eventLog.testAction(info.id, "ABTestGroupAssigned", testName, group);
        }

        return group;
    }

    /**
     * Retrieves the last of recent experiences for this member.
     */
    public List<MemberExperience> getExperiences (int memberId)
    {
        return Lists.transform(_memberRepo.getExperiences(memberId),
                new Function<MemberExperienceRecord, MemberExperience>() {
            public MemberExperience apply (MemberExperienceRecord expRecord) {
                // Depending on the action type, convert data to the correct object.
                final int actionData;
                switch (expRecord.action) {
                case HomePageItem.ACTION_ROOM:
                case HomePageItem.ACTION_GAME:
                case HomePageItem.ACTION_AVR_GAME:
                    actionData = Integer.parseInt(expRecord.data);
                    break;
                default:
                    actionData = 0;
                }
                return new MemberExperience(expRecord.dateOccurred, expRecord.action, actionData);
            }
        });
    }

    /**
     * Saves the member's experiences, clearing out any old ones.
     *
     * @param memberId ID of the member whose experiences are being saved.
     * @param experiences The experiences to save.
     */
    public void saveExperiences (final int memberId, List<MemberExperience> experiences)
    {
        _memberRepo.deleteExperiences(memberId);
        List<MemberExperienceRecord> records = Lists.newArrayList();
        for (MemberExperience mexp : experiences) {
            final String actionData;
            switch (mexp.action) {
            case HomePageItem.ACTION_ROOM:
            case HomePageItem.ACTION_GAME:
            case HomePageItem.ACTION_AVR_GAME:
                actionData = Integer.toString(mexp.data);
                break;
            default:
                continue; // we don't yet handle this type of experience
            }
            records.add(new MemberExperienceRecord(memberId, mexp.getDateOccurred(),
                                                   mexp.action, actionData));
        }
        _memberRepo.saveExperiences(records);
    }

    /**
     * Loads up the specified member's home page grid items.
     */
    public HomePageItem[] getHomePageGridItems (
        int memberId, final MemberExperience[] rawExperiences, boolean onTour, short badgesVersion)
    {
        // HACK for A/B testing!
        // TODO: measure test results and change this
        return new HomePageItem[] {
            makeGwtPageItem("Play Games", "games_page", Pages.GAMES),
            makeGwtPageItem("Join Groups", "groups_page", Pages.GROUPS),
            makeGwtPageItem("Browse The Shop", "shop_page", Pages.SHOP),
            makeGameItem(827), // corpse craft
            makeGameItem(1918), // bang heroes
            makeGameItem(1692), // vampires
            EXPLORE_ITEM,
            makeGwtPageItem("Invite Friends", "invite_friends", Pages.PEOPLE, "invites"),
            makeGwtPageItem("My Home Page", "me_page", Pages.ME)
        };
    }

    // TODO: measure the effectiveness of the HPG and reimplement more efficiently
    public HomePageItem[] getHomePageGridItems_Unhacked (
            int memberId, final MemberExperience[] rawExperiences, boolean onTour, short badgesVersion)
    {
        HomePageItem[] items = new HomePageItem[MWP_COUNT];
        int curItem = 0;

        // The first item on the home page is always a whirled tour unless already onTour
        if (!onTour) {
            items[curItem++] = EXPLORE_ITEM;
        }

        // The next 2 or 3 items are badges
        List<InProgressBadge> badges = _badgeLogic.getNextSuggestedBadges(
            memberId, badgesVersion, 3 - curItem);
        for (InProgressBadge badge : badges) {
            items[curItem++] = new HomePageItem(
                HomePageItem.ACTION_BADGE, badge, badge.imageMedia());
        }

        // The last 6 are determined by the user-specific home page items, depending on
        // where they were last in Whirled. We want the middle 3 to be games and the bottom
        // 3 to be rooms. Fill any slack with games
        final int desiredRoomCount = 3;
        final int desiredGameCount = 6 - curItem;
        List<List<HomePageItem>> experiences = getAllExperienceItems(
            rawExperiences, desiredRoomCount, desiredGameCount);
        List<HomePageItem> games = experiences.get(0);
        List<HomePageItem> rooms = experiences.get(1);

        IntSet haveRooms = new ArrayIntSet();
        for (HomePageItem item : rooms) {
            haveRooms.add(((BasicNavItemData)item.getNavItemData()).getId());
        }

        IntSet haveGames = new ArrayIntSet();
        for (HomePageItem item : games) {
            haveGames.add(((BasicNavItemData)item.getNavItemData()).getId());
        }

        // If there are still not enough places, fill in with some currently popular places.
        PopularPlacesSnapshot pps = _memberMan.getPPSnapshot();

        if (rooms.size() < desiredRoomCount) {
            // TODO: This is similar to some code in GalaxyServlet and GameServlet. refactor?
            for (PopularPlacesSnapshot.Place place : pps.getTopScenes()) {
                if (!haveRooms.contains(place.placeId)) {
                    SceneRecord scene = _sceneRepo.loadScene(place.placeId);
                    MediaDesc media = scene.getSnapshotFull();
                    if (media == null) {
                        media = RoomCodes.DEFAULT_SNAPSHOT_FULL;
                    }
                    rooms.add(new HomePageItem(
                        HomePageItem.ACTION_ROOM,
                        new BasicNavItemData(place.placeId, place.name), media));
                    haveRooms.add(place.placeId);
                }
                if (rooms.size() >= desiredRoomCount) {
                    break;
                }
            }
        }

        // Add the top active games.
        if (games.size() < desiredGameCount) {
            for (PopularPlacesSnapshot.Place place : pps.getTopGames()) {
                if (!haveGames.contains(place.placeId)) {
                    GameInfoRecord game = _mgameRepo.loadGame(place.placeId);
                    if (game.isAVRG && game.groupId != 0) {
                        games.add(new HomePageItem(
                                      HomePageItem.ACTION_AVR_GAME,
                                      new AVRGameNavItemData(game.gameId, game.name, game.groupId),
                                      game.getThumbMedia()));
                    } else {
                        games.add(new HomePageItem(
                                      HomePageItem.ACTION_GAME,
                                      new BasicNavItemData(game.gameId, game.name),
                                      game.getThumbMedia()));
                    }
                    haveGames.add(game.gameId);
                }
                if (games.size() >= desiredGameCount) {
                    break;
                }
            }
        }

        // Add calculated items to the array
        for (List<HomePageItem> list : experiences) {
            for (HomePageItem item : list) {
                items[curItem++] = item;
            }
        }

        // If we don't have enough games, pull from the list of all games.
        if (curItem < items.length) {
            for (GameInfoRecord game : _mgameRepo.loadGenre(GameGenre.ALL, items.length)) {
                if (!haveGames.contains(game.gameId)) {
                    if (game.isAVRG) {
                        if (game.groupId != 0) {
                            items[curItem++] = new HomePageItem(
                                HomePageItem.ACTION_AVR_GAME, new AVRGameNavItemData(
                                    game.gameId, game.name, game.groupId),
                                    game.getThumbMedia());
                        }
                    } else {
                        items[curItem++] = new HomePageItem(
                            HomePageItem.ACTION_GAME, new BasicNavItemData(
                                game.gameId, game.name), game.getThumbMedia());
                    }
                }
                if (curItem == items.length) {
                    break;
                }
            }
        }

        // If there still aren't enough places, fill in with null objects.
        while (curItem < items.length) {
            items[curItem++] = new HomePageItem(HomePageItem.ACTION_NONE, null, null);
        }

        return items;
    }

    /**
     * That the specified visitor is associated with the supplied entry vector. The values are
     * sanity checked so it's safe to pass them straight through from the untrustworthy client.
     */
    public void trackVectorAssociation (VisitorInfo info, String vector)
        throws ServiceException
    {
        if (info == null || info.id == null || vector == null) {
            log.warning("Got bogus vector data", "info", info, "vector", vector);
            return;
        }
        try {
            _memberRepo.noteEntryVector(info.id, vector);
            _eventLog.vectorAssociated(info, vector);
        } catch (DuplicateKeyException dke) {
            // this is par for the course as the client will keep reporting its association until
            // the cookie expires or the user registers
        }
    }

    /**
     * Sends a vanilla friend invitation.
     * @param memberId id of the user requesting friendship
     * @param friendId id of the user who will be sent an invitation
     * @return true if the invitee is a greeter and the friendship was automatically established
     */
    public boolean inviteToBeFriend (int memberId, int friendId)
        throws ServiceException
    {
        MemberRecord frec = _memberRepo.loadMember(friendId);
        if (frec == null) {
            log.warning("Requested to friend non-existent member", "who", memberId,
                        "friendId", friendId);

        } else if (frec.isGreeter()) {
            establishFriendship(memberId, friendId);
            return true;

        } else {
            // pass the buck
            _mailLogic.sendFriendInvite(memberId, friendId);
        }
        return false;
    }

    /**
     * Checks if the given friend id who has just leveled up is "real" enough and awards a bar
     * to the inviter, if any.
     */
    public void maybeAwardFriendBar (int friendId, int oldLevel, int newLevel)
    {
        // check they are crossing the threshold of reality
        if (newLevel < FRIEND_PAYOUT_MIN_LEVEL || oldLevel > FRIEND_PAYOUT_MIN_LEVEL) {
            return;
        }

        // this will never happen... ha!
        MemberRecord mrec = _memberRepo.loadMember(friendId);
        if (mrec == null) {
            return;
        }

        // check they are affiliated
        int payMemberId = mrec.affiliateMemberId;
        if (payMemberId == 0) {
            return;
        }

        // check they are not a permaguest
        if (mrec.isPermaguest()) {
            log.warning("Permaguest with a friend?", "permaguestId", friendId,
                "friendId", payMemberId);
            return;
        }

        // do the award
        try {
            _memberRepo.noteFriendPayment(friendId, payMemberId);
            _moneyLogic.award(payMemberId, Currency.BARS, TransactionType.FRIEND_AWARD, 1, true,
                UserAction.receivedFriendAward(payMemberId, friendId));

        } catch (DuplicateKeyException dke) {
            log.warning("Friend payment triggered twice?", "friend", friendId,
                        "payee", payMemberId);
        }
    }

    /**
     * Delete all traces of the specified members, with some exceptions. If a member is a
     * permaguest, then all traces are indeed deleted. If they are registered member, then some
     * traces of the member are retained to allow certain parts of their participation in Whirled
     * to remain (forum posts, listed items), but their account is deactivated and most of their
     * crap is deleted.
     */
    public void deleteMembers (Collection<Integer> memberIds)
    {
        // make sure all of the supplied ids are actually members and grab their account names
        List<Integer> purgeIds = Lists.newArrayList();
        List<String> purgeNames = Lists.newArrayList();
        List<Integer> killIds = Lists.newArrayList(), disableIds = Lists.newArrayList();
        for (MemberRecord mrec : _memberRepo.loadMembers(memberIds)) {
            purgeIds.add(mrec.memberId);
            purgeNames.add(mrec.accountName);
            // permaguests get fully deleted, registered members get disabled
            if (mrec.isPermaguest()) {
                killIds.add(mrec.memberId);
            } else {
                disableIds.add(mrec.memberId);
            }
        }

        // delete everything that we can unequivocally delete
        if (!purgeIds.isEmpty()) {
            _avrGameRepo.purgeMembers(purgeIds);
            _badgeRepo.purgeMembers(purgeIds);
            _commentRepo.purgeMembers(purgeIds);
            _faveRepo.purgeMembers(purgeIds);
            _feedRepo.purgeMembers(purgeIds);
            _forumRepo.purgeMembers(purgeIds);
            _galleryRepo.purgeMembers(purgeIds);
            _gcookRepo.purgePlayers(purgeIds);
            _groupRepo.purgeMembers(purgeIds);
            _mailRepo.purgeMembers(purgeIds);
            _medalRepo.purgeMembers(purgeIds);
            _memberRepo.purgeMembers(purgeIds);
            _mgameRepo.purgeMembers(purgeIds);
            _moneyRepo.purgeMembers(purgeIds);
            _profileRepo.purgeMembers(purgeIds);
            _sceneRepo.purgeMembers(purgeIds);
            _statRepo.purgePlayers(purgeIds);
            _trophyRepo.purgeMembers(purgeIds);
            _uactionRepo.purgeMembers(purgeIds);
            // delete their inventory and associated data
            for (ItemRepository<ItemRecord> irepo : _itemLogic.getRepositories()) {
                irepo.purgeMembers(purgeIds);
            }
        }
        if (!purgeNames.isEmpty()) {
            _oooAuthRepo.purgeMembers(purgeNames);
        }

        //    ItemListInfoRecord - to be removed?
        //    EventRecord - leave these around?

        // fully delete all permaguest records
        if (!killIds.isEmpty()) {
            _memberRepo.deleteMembers(killIds);
        }

        // and disable all registered member records
        if (!disableIds.isEmpty()) {
            _memberRepo.disableMembers(disableIds);
        }
    }

    /**
     * Retrieves a list of lists of experiences to be displayed on the home page. Each list is
     * a category of experiences. The first is games, the second is rooms. Within each list, each
     * experience the member has had recently will be given a weighted score to determine the order
     * of the experience.  Only the number of experiences requested will be returned as home page
     * items. If there are not enough experiences, or the experiences have a low score (too old,
     * etc.), they will not be included here.
     *
     * @param memObj Member object to get home page items for
     * @param numRooms Maximum number of rooms to retrieve.
     * @param numGames Maximum number of games to retrieve.
     * @return List of lists of the home page items, first games then rooms
     */
    protected List<List<HomePageItem>> getAllExperienceItems (
        MemberExperience[] experiences, int numRooms, int numGames)
    {
        List<ScoredExperience> scores = Lists.newArrayList();
        for (MemberExperience experience : experiences) {
            ScoredExperience newExp = new ScoredExperience(experience);

            // Has this member experienced this more than once?  If so, combine.
            for (Iterator<ScoredExperience> itor = scores.iterator(); itor.hasNext(); ) {
                ScoredExperience thisExp = itor.next();
                if (thisExp.isSameExperience(newExp)) {
                    newExp = new ScoredExperience(newExp, thisExp);
                    itor.remove();
                    break;
                }
            }

            scores.add(newExp);
        }

        // Sort by scores (highest score first), limit it to count, and return the list.
        Collections.sort(scores, new Comparator<ScoredExperience>() {
            public int compare (ScoredExperience exp1, ScoredExperience exp2) {
                return (exp1.score > exp2.score) ? -1 : ((exp1.score < exp2.score) ? 1 : 0);
            }
        });

        List<List<HomePageItem>> lists = Lists.newArrayList();
        lists.add(getExperienceItems(
            scores, numGames, HomePageItem.ACTION_GAME, HomePageItem.ACTION_AVR_GAME));
        lists.add(getExperienceItems(scores, numRooms, HomePageItem.ACTION_ROOM));
        return lists;
    }

    /**
     * Converts scored experiences to home page items, filtered by type, up to a maximum number.
     */
    protected List<HomePageItem> getExperienceItems (
        List<ScoredExperience> scores, int count, byte ...actionTypes)
    {
        // Convert our scored experiences to home page items.
        List<HomePageItem> items = Lists.newArrayList();
        for (ScoredExperience se : scores) {
            if (ArrayUtil.indexOf(actionTypes, se.experience.action) == -1) {
                continue;
            }

            MediaDesc media;
            final NavItemData data;
            switch (se.experience.action) {
            case HomePageItem.ACTION_ROOM: {
                SceneRecord scene = _sceneRepo.loadScene(se.experience.data);
                if (scene == null) {
                    continue;
                }
                media = scene.getSnapshotFull();
                if (media == null) {
                    media = RoomCodes.DEFAULT_SNAPSHOT_FULL;
                }
                data = new BasicNavItemData(se.experience.data, scene.name);
                break;
            }
            case HomePageItem.ACTION_GAME:
            case HomePageItem.ACTION_AVR_GAME:
                GameInfoRecord game = _mgameRepo.loadGame(se.experience.data);
                if (game == null) {
                    continue;
                }
                media = game.getThumbMedia();
                if (se.experience.action == HomePageItem.ACTION_GAME) {
                    data = new BasicNavItemData(game.gameId, game.name);
                } else {
                    // suppress games with no home whirled (e.g. in-development games)
                    if (game.groupId == 0) {
                        continue;
                    }
                    data = new AVRGameNavItemData(game.gameId, game.name, game.groupId);
                }
                break;
            default:
                // if we have no data, our caller will freak out, so skip this experience
                continue;
            }
            items.add(new HomePageItem(se.experience.action, data, media));
            if (items.size() == count) {
                break; // stop when we reach our desired count
            }
        }
        return items;
    }

    protected HomePageItem makeGwtPageItem (
        String name, String imagePath, Pages page, Object... args)
    {
        return new HomePageItem(HomePageItem.ACTION_GWT_PAGE,
            new GwtPageNavItemData(name, page.getPath(), Args.compose(args).toToken()),
            new StaticMediaDesc(MediaDesc.IMAGE_PNG, "icon", imagePath));
    }

    protected HomePageItem makeGameItem (int gameId)
    {
        GameInfoRecord game = _mgameRepo.loadGame(gameId);
        if (game == null) {
            return new HomePageItem(HomePageItem.ACTION_NONE, null, null);
        }
        if (game.isAVRG && game.groupId != 0) {
            return new HomePageItem(HomePageItem.ACTION_AVR_GAME,
                new AVRGameNavItemData(game.gameId, game.name, game.groupId), game.getThumbMedia());
        } else {
            return new HomePageItem(HomePageItem.ACTION_GAME, new BasicNavItemData(
                game.gameId, game.name), game.getThumbMedia());
        }
    }

    /**
     * Validate that the specified display name is kosher.
     */
    protected void validateDisplayName (String name, boolean allowSupportNames)
        throws ServiceException
    {
        if (!MemberName.isValidDisplayName(name) ||
                (!allowSupportNames && !MemberName.isValidNonSupportName(name))) {
            throw new ServiceException(ServiceCodes.E_INTERNAL_ERROR);
        }

        // TODO: improve. Load patterns from the database at startup?
        // validate the name against some simple stop patterns.
        name = name.toLowerCase();
        if ((-1 != name.indexOf("nigger")) || (-1 != name.indexOf("faggot"))) {
            throw new ServiceException("e.bad_displayname");
        }
    }

    /**
     * A member experience that has been scored.
     *
     * @author Kyle Sampson <kyle@threerings.net>
     */
    protected static class ScoredExperience
    {
        public final MemberExperience experience;
        public final float score;

        /**
         * Creates a scored experience based on the information from the given
         * {@link MemberExperienceRecord}.
         */
        public ScoredExperience (MemberExperience experience)
        {
            this.experience = experience;

            // The score for a standard record starts at 14 and decrements by 1 for every day
            // since the experience occurred.  Cap at 0; thus, anything older than 2 weeks has
            // the same score.
            float newScore = 14f -
                (System.currentTimeMillis() - experience.dateOccurred) /
                (1000f * 60f * 60f * 24f);
            score = (newScore < 0) ? 0f : newScore;
        }

        /**
         * Combines two identical (i.e., {@link #isSameExperience(ScoredExperience)} returns true})
         * scored experiences into one, combining their scores.
         */
        public ScoredExperience (ScoredExperience exp1, ScoredExperience exp2)
        {
            experience = exp1.experience;   // exp2.item should be the same.
            score = exp1.score + exp2.score;    // Both scores positive
        }

        /**
         * Null experience
         */
        public ScoredExperience ()
        {
            experience = new MemberExperience(new Date(), HomePageItem.ACTION_NONE, 0);
            score = 0f;
        }

        /**
         * Returns true if the given scored experience represents the same experience as this one.
         * They may have different scores, but this indicates the user did the same thing twice.
         */
        public boolean isSameExperience (ScoredExperience other)
        {
            return this.experience.action == other.experience.action &&
                this.experience.data == other.experience.data;
        }
    }

    protected static class AddFriend extends MemberNodeAction
    {
        public AddFriend (int memberId, MemberCard friend) {
            super(memberId);
            _entry = new FriendEntry(
                new VizMemberName(friend.name.toString(), friend.name.getMemberId(), friend.photo),
                friend.headline);
        }

        public AddFriend () {
        }

        @Override protected void execute (MemberObject memobj) {
            _friendMan.addNewFriend(memobj, _entry);
        }

        protected FriendEntry _entry;

        @Inject protected transient MsoyPeerManager _peerMan;
        @Inject protected transient FriendManager _friendMan;
    }

    protected static class RemoveFriend extends MemberNodeAction
    {
        public RemoveFriend (int memberId, int friendId) {
            super(memberId);
            _friendId = friendId;
        }

        public RemoveFriend () {
        }

        @Override protected void execute (MemberObject memobj) {
            _friendMan.removeFriend(memobj, _friendId);
        }

        protected int _friendId;

        @Inject protected transient FriendManager _friendMan;
    }

    // general dependencies
    @Inject protected ABTestRepository _testRepo;
    @Inject protected BadgeLogic _badgeLogic;
    @Inject protected FeedRepository _feedRepo;
    @Inject protected GroupRepository _groupRepo;
    @Inject protected ItemLogic _itemLogic;
    @Inject protected MemberManager _memberMan;
    @Inject protected MemberRepository _memberRepo;
    @Inject protected MoneyLogic _moneyLogic;
    @Inject protected MsoyEventLogger _eventLog;
    @Inject protected MsoyGameRepository _mgameRepo;
    @Inject protected MsoyPeerManager _peerMan;
    @Inject protected MsoySceneRepository _sceneRepo;
    @Inject protected PlayerNodeActions _playerActions;
    @Inject protected PresentsDObjectMgr _omgr;
    @Inject protected StatLogic _statLogic;
    @Inject protected MailLogic _mailLogic; 

    // member purging dependencies
    @Inject protected AVRGameRepository _avrGameRepo;
    @Inject protected BadgeRepository _badgeRepo;
    @Inject protected CommentRepository _commentRepo;
    @Inject protected FavoritesRepository _faveRepo;
    @Inject protected ForumRepository _forumRepo;
    @Inject protected GalleryRepository _galleryRepo;
    @Inject protected GameCookieRepository _gcookRepo;
    @Inject protected MailRepository _mailRepo;
    @Inject protected MedalRepository _medalRepo;
    @Inject protected MoneyRepository _moneyRepo;
    @Inject protected MsoyOOOUserRepository _oooAuthRepo;
    @Inject protected ProfileRepository _profileRepo;
    @Inject protected StatRepository _statRepo;
    @Inject protected TrophyRepository _trophyRepo;
    @Inject protected UserActionRepository _uactionRepo;

    /** The whirled tour home page item. */
    protected static final HomePageItem EXPLORE_ITEM = new HomePageItem(
        HomePageItem.ACTION_EXPLORE, null, new StaticMediaDesc(
            MediaDesc.IMAGE_PNG, "icon", "home_page_tour"));

    /** The number of slots we have in My Whired Places. */
    protected static final int MWP_COUNT = 9;

    /** Minimum level a player must achieve to trigger a payment to her inviter. */
    protected static final int FRIEND_PAYOUT_MIN_LEVEL = DeploymentConfig.devDeployment ? 2 : 5;
}
