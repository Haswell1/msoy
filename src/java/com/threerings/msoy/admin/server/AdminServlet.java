//
// $Id$

package com.threerings.msoy.admin.server;

import static com.threerings.msoy.Log.log;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import com.samskivert.util.ArrayIntSet;
import com.samskivert.util.IntSet;
import com.samskivert.util.Invoker;
import com.samskivert.util.Invoker.Unit;

import com.threerings.gwt.util.PagedResult;

import com.threerings.msoy.peer.server.MsoyPeerManager;
import com.threerings.msoy.server.BureauManager;
import com.threerings.msoy.server.MsoyEventLogger;
import com.threerings.msoy.server.ServerMessages;
import com.threerings.msoy.server.persist.AffiliateMapRecord;
import com.threerings.msoy.server.persist.AffiliateMapRepository;
import com.threerings.msoy.server.persist.CharityRecord;
import com.threerings.msoy.server.persist.ContestRecord;
import com.threerings.msoy.server.persist.ContestRepository;
import com.threerings.msoy.server.persist.MemberInviteStatusRecord;
import com.threerings.msoy.server.persist.MemberRecord;
import com.threerings.msoy.server.persist.PromotionRecord;
import com.threerings.msoy.server.persist.PromotionRepository;

import com.threerings.msoy.web.gwt.Contest;
import com.threerings.msoy.web.gwt.Promotion;
import com.threerings.msoy.web.gwt.ServiceException;
import com.threerings.msoy.web.gwt.WebCreds;
import com.threerings.msoy.web.server.MsoyServiceServlet;
import com.threerings.msoy.web.server.ServletWaiter;

import com.threerings.msoy.game.server.WorldGameRegistry;
import com.threerings.msoy.item.data.ItemCodes;
import com.threerings.msoy.item.data.all.CatalogIdent;
import com.threerings.msoy.item.data.all.ItemFlag;
import com.threerings.msoy.item.data.all.ItemIdent;
import com.threerings.msoy.item.gwt.ItemDetail;
import com.threerings.msoy.item.server.ItemLogic;
import com.threerings.msoy.item.server.persist.CatalogRecord;
import com.threerings.msoy.item.server.persist.CloneRecord;
import com.threerings.msoy.item.server.persist.ItemFlagRecord;
import com.threerings.msoy.item.server.persist.ItemFlagRepository;
import com.threerings.msoy.item.server.persist.ItemRecord;
import com.threerings.msoy.item.server.persist.ItemRepository;

import com.threerings.msoy.mail.server.MailLogic;
import com.threerings.msoy.mail.server.persist.MailRepository;
import com.threerings.msoy.money.data.all.MemberMoney;
import com.threerings.msoy.money.data.all.MoneyTransaction;
import com.threerings.msoy.money.server.MoneyLogic;

import com.threerings.msoy.admin.data.MsoyAdminCodes;
import com.threerings.msoy.admin.gwt.ABTest;
import com.threerings.msoy.admin.gwt.AdminService;
import com.threerings.msoy.admin.gwt.AffiliateMapping;
import com.threerings.msoy.admin.gwt.BureauLauncherInfo;
import com.threerings.msoy.admin.gwt.MemberAdminInfo;
import com.threerings.msoy.admin.gwt.MemberInviteResult;
import com.threerings.msoy.admin.gwt.MemberInviteStatus;
import com.threerings.msoy.admin.gwt.StatsModel;
import com.threerings.msoy.admin.server.persist.ABTestRecord;
import com.threerings.msoy.admin.server.persist.ABTestRepository;
import com.threerings.msoy.data.all.CharityInfo;
import com.threerings.presents.annotation.MainInvoker;
import com.threerings.presents.dobj.RootDObjectManager;
import com.threerings.presents.peer.data.NodeObject;
import com.threerings.presents.peer.server.PeerManager.NodeAction;

/**
 * Provides the server implementation of {@link AdminService}.
 */
public class AdminServlet extends MsoyServiceServlet
    implements AdminService
{
    // from interface AdminService
    public void grantInvitations (final int numberInvitations, final int memberId)
        throws ServiceException
    {
        final MemberRecord memrec = requireAdminUser();
        _memberRepo.grantInvites(memberId, numberInvitations);
        sendGotInvitesMail(memrec.memberId, memberId, numberInvitations);
    }

    // from interface AdminService
    public MemberAdminInfo getMemberInfo (final int memberId)
        throws ServiceException
    {
        requireSupportUser();

        final MemberRecord tgtrec = _memberRepo.loadMember(memberId);
        if (tgtrec == null) {
            return null;
        }

        final MemberMoney money = _moneyLogic.getMoneyFor(memberId);
        final MemberAdminInfo info = new MemberAdminInfo();
        info.name = tgtrec.getName();
        info.accountName = tgtrec.accountName;
        info.permaName = tgtrec.permaName;
        if (tgtrec.isSet(MemberRecord.Flag.MAINTAINER)) {
            info.role = WebCreds.Role.MAINTAINER;
        } else if (tgtrec.isSet(MemberRecord.Flag.ADMIN)) {
            info.role = WebCreds.Role.ADMIN;
        } else if (tgtrec.isSet(MemberRecord.Flag.SUPPORT)) {
            info.role = WebCreds.Role.SUPPORT;
        } else {
            info.role = WebCreds.Role.USER;
        }
        info.flow = money.coins;
        info.accFlow = (int)money.accCoins;
        info.gold = money.bars;
        info.sessions = tgtrec.sessions;
        info.sessionMinutes = tgtrec.sessionMinutes;
        if (tgtrec.lastSession != null) {
            info.lastSession = new Date(tgtrec.lastSession.getTime());
        }
        info.humanity = tgtrec.humanity;
        if (tgtrec.affiliateMemberId != 0) {
            // TODO: could be your inviter, but really just your affiliate
            info.inviter = _memberRepo.loadMemberName(tgtrec.affiliateMemberId);
        }
        info.invitees = _memberRepo.loadMembersInvitedBy(memberId);

        // Check if this member is set as a charity.
        CharityRecord charity = _memberRepo.getCharityRecord(memberId);
        if (charity == null) {
            info.charity = false;
            info.coreCharity = false;
            info.charityDescription = "";
        } else {
            info.charity = true;
            info.coreCharity = charity.core;
            info.charityDescription = charity.description;
        }
        return info;
    }

    // from interface AdminService
    public MemberInviteResult getPlayerList (final int inviterId)
        throws ServiceException
    {
        requireSupportUser();

        final MemberInviteResult res = new MemberInviteResult();
        final MemberRecord memRec = inviterId == 0 ? null : _memberRepo.loadMember(inviterId);
        if (memRec != null) {
            res.name = memRec.permaName == null || memRec.permaName.equals("") ?
                memRec.name : memRec.permaName;
            res.memberId = inviterId;
            // TODO: your affiliate is not necessarily your inviter
            res.invitingFriendId = memRec.affiliateMemberId;
        }

        final List<MemberInviteStatus> players = Lists.newArrayList();
        for (final MemberInviteStatusRecord rec : _memberRepo.getMembersInvitedBy(inviterId)) {
            players.add(rec.toWebObject());
        }
        res.invitees = players;
        return res;
    }

    // from interface AdminService
    public void setRole (int memberId, WebCreds.Role role)
        throws ServiceException
    {
        final MemberRecord memrec = requireAdminUser();
        final MemberRecord tgtrec = _memberRepo.loadMember(memberId);
        if (tgtrec == null) {
            return;
        }

        // log this as a warning so that it shows up in the nightly filtered logs
        log.warning("Configuring role", "setter", memrec.who(), "target", tgtrec.who(),
                    "role", role);
        tgtrec.setFlag(MemberRecord.Flag.SUPPORT, role == WebCreds.Role.SUPPORT);
        if (memrec.isMaintainer()) {
            tgtrec.setFlag(MemberRecord.Flag.ADMIN, role == WebCreds.Role.ADMIN);
        }
        if (memrec.isRoot()) {
            tgtrec.setFlag(MemberRecord.Flag.MAINTAINER, role == WebCreds.Role.MAINTAINER);
        }
        _memberRepo.storeFlags(tgtrec);
    }

    // from interface AdminService
    public List<ABTest> getABTests ()
        throws ServiceException
    {
        List<ABTestRecord> records = _testRepo.loadTests();
        final List<ABTest> tests = Lists.newArrayList();
        for (final ABTestRecord record : records) {
            final ABTest test = record.toABTest();
            tests.add(test);
        }
        return tests;
    }

    // from interface AdminService
    public void createTest (final ABTest test)
        throws ServiceException
    {
        // make sure there isn't already a test with this name
        if (_testRepo.loadTestByName(test.name) != null) {
            throw new ServiceException(MsoyAdminCodes.E_AB_TEST_DUPLICATE_NAME);
        }
        _testRepo.insertABTest(test);
    }

    // from interface AdminService
    public void updateTest (final ABTest test)
        throws ServiceException
    {
        // make sure there isn't already a test with this name
        final ABTestRecord existingTest = _testRepo.loadTestByName(test.name);
        if (existingTest != null && existingTest.abTestId != test.abTestId) {
            throw new ServiceException(MsoyAdminCodes.E_AB_TEST_DUPLICATE_NAME);
        }
        _testRepo.updateABTest(test);
    }

    // from interface AdminService
    public PagedResult<AffiliateMapping> getAffiliateMappings (
        int start, int count, boolean needTotal)
        throws ServiceException
    {
        requireSupportUser();

        PagedResult<AffiliateMapping> result = new PagedResult<AffiliateMapping>();
        result.page = Lists.newArrayList(Iterables.transform(
            _affMapRepo.getMappings(start, count), AffiliateMapRecord.TO_MAPPING));
        if (needTotal) {
            result.total = _affMapRepo.getMappingCount();
        }
        return result;
    }

    // from interface AdminService
    public void mapAffiliate (String affiliate, int memberId)
        throws ServiceException
    {
        requireSupportUser();

        _affMapRepo.storeMapping(affiliate, memberId);
        _memberRepo.updateAffiliateMemberId(affiliate, memberId);
    }

    // from interface AdminService
    public ItemFlagsResult getItemFlags (int start, int count, boolean needCount)
        throws ServiceException
    {
        requireSupportUser();
        ItemFlagsResult result = new ItemFlagsResult();

        // get the total if needed
        if (needCount) {
            result.total = _itemFlagRepo.countItemFlags();
        }

        // get the page of item flags
        result.page = Lists.newArrayList(Iterables.transform(_itemFlagRepo.loadFlags(start, count),
            new Function<ItemFlagRecord, ItemFlag>() {
                public ItemFlag apply (ItemFlagRecord rec) {
                    return rec.toItemFlag();
                }
            }));

        // collect all ids by type that we require
        Map<Byte, Set<Integer>> itemsToLoad = Maps.newHashMap();
        for (ItemFlag flag : result.page) {
            Set<Integer> itemIds = itemsToLoad.get(flag.itemIdent.type);
            if (itemIds == null) {
                itemsToLoad.put(flag.itemIdent.type, itemIds = Sets.newHashSet());
            }
            itemIds.add(flag.itemIdent.itemId);
        }

        // load items and stash by ident, also grab the creator id
        result.items = Maps.newHashMap();
        Set<Integer> memberIds = Sets.newHashSet();
        for (Map.Entry<Byte, Set<Integer>> ee : itemsToLoad.entrySet()) {
            for (ItemRecord rec : _itemLogic.getRepository(ee.getKey()).loadItems(ee.getValue())) {
                ItemDetail detail = new ItemDetail();
                detail.item = rec.toItem();
                result.items.put(detail.item.getIdent(), detail);
                memberIds.add(detail.item.creatorId);
            }
        }

        // now add all the reporters
        for (ItemFlag flag : result.page) {
            memberIds.add(flag.memberId);
        }

        // resolve and store names for display
        result.memberNames = Maps.newHashMap();
        result.memberNames.putAll(_memberRepo.loadMemberNames(memberIds));

        // backfill the creator names
        for (ItemDetail detail : result.items.values()) {
            detail.creator = result.memberNames.get(detail.item.creatorId);
        }

        return result;
    }

    // from interface AdminService
    public ItemTransactionResult getItemTransactions (
        ItemIdent iident, int from, int count, boolean needCount)
        throws ServiceException
    {
        requireSupportUser();

        final ItemRepository<ItemRecord> repo = _itemLogic.getRepository(iident.type);
        final ItemRecord item = repo.loadOriginalItem(iident.itemId);

        if (item == null) {
            throw new ServiceException(ItemCodes.E_NO_SUCH_ITEM);
        }
        if (item.catalogId == 0) {
            throw new ServiceException(ItemCodes.E_ITEM_NOT_LISTED);
        }
        ItemTransactionResult result = new ItemTransactionResult();
        if (needCount) {
            result.total = _moneyLogic.getItemTransactionCount(iident);
        }
        result.page = _moneyLogic.getItemTransactions(iident, from, count, false);

        Set<Integer> memberIds = Sets.newHashSet();
        for (MoneyTransaction tx : result.page) {
            memberIds.add(tx.memberId);
        }
        result.memberNames = Maps.newHashMap();
        result.memberNames.putAll(_memberRepo.loadMemberNames(memberIds));
        return result;
    }

    // from interface AdminService
    public ItemDeletionResult deleteItemAdmin (
        final ItemIdent iident, final String subject, final String body)
        throws ServiceException
    {
        final MemberRecord memrec = requireSupportUser();

        log.info("Deleting item for admin", "who", memrec.accountName, "item", iident,
            "subject", subject);

        final byte type = iident.type;
        final ItemRepository<ItemRecord> repo = _itemLogic.getRepository(type);
        final ItemRecord item = repo.loadOriginalItem(iident.itemId);
        final IntSet owners = new ArrayIntSet();

        ItemDeletionResult result = new ItemDeletionResult();
        owners.add(item.creatorId);

        CatalogRecord catrec = null;

        // we've loaded the original item, if it represents the original listing or a catalog
        // master item, we want to squish the original catalog listing.
        if (item.catalogId != 0) {
            catrec = repo.loadListing(item.catalogId, true);
            if (catrec != null && catrec.listedItemId != item.itemId) {
                catrec = null;
            }
        }

        if (catrec != null) {
            _itemLogic.removeListing(memrec, type, item.catalogId);
        }

        // then delete any potential clones
        for (final CloneRecord record : repo.loadCloneRecords(item.itemId)) {
            repo.deleteItem(record.itemId);
            result.deletionCount ++;
            owners.add(record.ownerId);
        }

        // finally delete the actual item
        repo.deleteItem(item.itemId);
        result.deletionCount ++;

        // notify the owners of the deletion
        for (final int ownerId : owners) {
            if (ownerId == memrec.memberId) {
                continue; // admin deleting their own item? sure, whatever!
            }
            final MemberRecord owner = _memberRepo.loadMember(ownerId);
            if (owner != null) {
                _mailLogic.startBulkConversation(memrec, owner, subject, body);
            }
        }

        // now do the refunds
        result.refunds = _moneyLogic.refundAllItemPurchases(new ItemIdent(
            type, item.itemId), item.name);

        if (catrec != null) {
            // TODO: remove after a release or two
            result.refunds = _moneyLogic.refundAllItemPurchases(new CatalogIdent(
                type, item.catalogId), item.name);
        }

        return result;
    }

    // from interface AdminService
    public void refreshBureauLauncherInfo ()
        throws ServiceException
    {
        // Post the request to the event thread and wait for result
        ServletWaiter.queueAndWait(_omgr, "refreshBureauLauncherInfo", new Callable<Void>() {
            public Void call () {
                _bureauMgr.refreshBureauLauncherInfo();
                return null;
            }
        });
    }

    // from interface AdminService
    public BureauLauncherInfo[] getBureauLauncherInfo ()
        throws ServiceException
    {
        // Post the request to the event thread and wait for result
        return ServletWaiter.queueAndWait(
            _omgr, "getBureauLauncherInfo", new Callable<BureauLauncherInfo[]>() {
                public BureauLauncherInfo[] call () {
                    return _bureauMgr.getBureauLauncherInfo();
                }
            });
    }

    // from interface AdminService
    public List<Promotion> loadPromotions ()
        throws ServiceException
    {
        requireSupportUser();
        return Lists.newArrayList(
            Lists.transform(_promoRepo.loadPromotions(), PromotionRecord.TO_PROMOTION));
    }

    // from interface AdminService
    public void addPromotion (Promotion promo)
        throws ServiceException
    {
        requireSupportUser();
        _promoRepo.addPromotion(promo);
    }

    // from interface AdminService
    public void deletePromotion (String promoId)
        throws ServiceException
    {
        requireSupportUser();
        _promoRepo.deletePromotion(promoId);
    }

    // from interface AdminService
    public List<Contest> loadContests ()
        throws ServiceException
    {
        requireSupportUser();
        return Lists.newArrayList(Lists.transform(_contestRepo.loadContests(),
            ContestRecord.TO_CONTEST));
    }

    // from interface AdminService
    public void addContest (Contest contest)
        throws ServiceException
    {
        requireSupportUser();
        _contestRepo.addContest(contest);
    }

    // from interface AdminService
    public void updateContest (Contest contest)
        throws ServiceException
    {
        requireSupportUser();
        _contestRepo.updateContest(contest);
    }

    // from interface AdminService
    public void deleteContest (String contestId)
        throws ServiceException
    {
        requireSupportUser();
        _contestRepo.deleteContest(contestId);
    }

    // from interface AdminService
    public StatsModel getStatsModel (StatsModel.Type type)
        throws ServiceException
    {
        requireSupportUser();
        try {
            return _adminMgr.compilePeerStatistics(type).get();
        } catch (InterruptedException ie) {
            log.warning("Stats compilation timed out", "type", type, "error", ie);
            throw new ServiceException(MsoyAdminCodes.E_INTERNAL_ERROR);
        } catch (Exception e) {
            log.warning("Stats compilation failed", "type", type, e);
            throw new ServiceException(MsoyAdminCodes.E_INTERNAL_ERROR);
        }
    }
    
    // from interface AdminService
    public void setCharityInfo (CharityInfo charityInfo)
        throws ServiceException
    {
        requireSupportUser();
        
        // Save or delete charity record depending on value of 'charity.
        CharityRecord charityRec = new CharityRecord(charityInfo.memberId, charityInfo.core,
            charityInfo.description);
        _memberRepo.saveCharity(charityRec);
    }
    
    // from interface AdminService
    public void removeCharityStatus (int memberId)
        throws ServiceException
    {
        requireSupportUser();
        
        _memberRepo.deleteCharity(memberId);
    }
    
    // from interface AdminService
    public Set<String> getPeerNodeNames ()
        throws ServiceException
    {
        requireSupportUser();
        
        // Collect the names of all the nodes
        final Set<String> names = Sets.newHashSet();
        _peerMgr.applyToNodes(new Function<NodeObject, Void>() {
            public Void apply (NodeObject node) {
                names.add(node.nodeName);
                return null;
            }
        });
        
        return names;
    }

    // from interface AdminService
    public void scheduleReboot (int minutes, String message)
        throws ServiceException
    {
        MemberRecord mrec = requireAdminUser();
        long time = System.currentTimeMillis() + minutes * 60 * 1000;
        _runtimeConfig.server.setCustomInitiator(mrec.accountName + ":" + mrec.memberId);
        _runtimeConfig.server.setCustomRebootMsg(message);
        _runtimeConfig.server.setNextReboot(time);
    }

    protected void sendGotInvitesMail (final int senderId, final int recipientId, final int number)
    {
        final String subject = _serverMsgs.getBundle("server").get("m.got_invites_subject", number);
        final String body = _serverMsgs.getBundle("server").get("m.got_invites_body", number);
        _mailRepo.startConversation(recipientId, senderId, subject, body, null, true);
    }
    
    public void restartPanopticon (Set<String> nodeNames)
        throws ServiceException
    {
        requireAdminUser();
        
        for (String node : nodeNames) {
            _peerMgr.invokeNodeAction(node, new RestartPanopticonAction());
        }
    }
    
    protected static class RestartPanopticonAction extends NodeAction
    {
        public RestartPanopticonAction () {}
        
        @Override 
        protected void execute () 
        {
            // Restart the logger on this node and all game servers attached to this node.
            // Restarting is a blocking operation, so run it on the invoker.
            _invoker.postUnit(new Unit() {
                @Override public boolean invoke () {
                    _nodeLogger.restart();
                    return false;
                }
            });
            _gameReg.resetEventLogger();
        }
        
        @Override 
        public boolean isApplicable (NodeObject nodeobj) 
        {
            // This will automatically go to the node we want.
            return true;
        }
        
        @Inject protected transient MsoyEventLogger _nodeLogger;
        @Inject protected transient WorldGameRegistry _gameReg;
        @Inject protected transient @MainInvoker Invoker _invoker;
    }
    
    // our dependencies
    @Inject protected ServerMessages _serverMsgs;
    @Inject protected RootDObjectManager _omgr;
    @Inject protected MsoyAdminManager _adminMgr;
    @Inject protected BureauManager _bureauMgr;
    @Inject protected ItemLogic _itemLogic;
    @Inject protected MailLogic _mailLogic;
    @Inject protected MoneyLogic _moneyLogic;
    @Inject protected MailRepository _mailRepo;
    @Inject protected ABTestRepository _testRepo;
    @Inject protected AffiliateMapRepository _affMapRepo;
    @Inject protected PromotionRepository _promoRepo;
    @Inject protected ContestRepository _contestRepo;
    @Inject protected MsoyEventLogger _eventLogger;
    @Inject protected ItemFlagRepository _itemFlagRepo;
    @Inject protected MsoyPeerManager _peerMgr;
    @Inject protected RuntimeConfig _runtimeConfig;
}
