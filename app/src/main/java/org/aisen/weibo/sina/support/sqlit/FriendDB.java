package org.aisen.weibo.sina.support.sqlit;

import android.text.TextUtils;

import org.aisen.android.common.context.GlobalContext;
import org.aisen.android.common.setting.Setting;
import org.aisen.android.common.utils.ActivityHelper;
import org.aisen.android.component.orm.extra.Extra;
import org.aisen.android.component.orm.utils.FieldUtils;
import org.aisen.android.network.biz.IResult;
import org.aisen.android.network.cache.ICacheUtility;
import org.aisen.android.network.http.Params;
import org.aisen.weibo.sina.base.AppContext;
import org.aisen.weibo.sina.sinasdk.bean.Friendship;
import org.aisen.weibo.sina.sinasdk.bean.WeiBoUser;
import org.aisen.weibo.sina.support.utils.CacheTimeUtils;

import java.util.List;

/**
 * 保存当前登录用户的最多200个好友信息
 * 
 * @author wangdan
 *
 */
public class FriendDB implements ICacheUtility {

	public static void insertFriends(List<WeiBoUser> friendList) {
		SinaDB.getDB().insert(new Extra(AppContext.getAccount().getUser().getIdstr(), "Friends"), friendList);
	}
	
	public static List<WeiBoUser> query(String q) {
		if (TextUtils.isEmpty(q))
			return FriendMentionDB.getRecentMention(null);
		
		q = "%" + q + "%";
		
		String selection = String.format(" %s = ? and %s = ? and ( %s like ? or %s like ? ) ", FieldUtils.OWNER, FieldUtils.KEY, "screen_name", "remark");
		String[] selectionArgs = new String[]{ AppContext.getAccount().getUser().getIdstr(), "Friends", q, q };
		
		return SinaDB.getDB().select(WeiBoUser.class, selection, selectionArgs);
	}

	public static List<WeiBoUser> selectAll() {
		String selection = String.format(" %s = ? and %s = ? ", FieldUtils.OWNER, FieldUtils.KEY);
		String[] selectionArgs = new String[]{ AppContext.getAccount().getUser().getIdstr(), "Friends" };
		
		return SinaDB.getDB().select(WeiBoUser.class, selection, selectionArgs, null, null, null, null);
	}
	
	public static void clear() {
		SinaDB.getDB().deleteAll(new Extra(AppContext.getAccount().getUser().getIdstr(), "Friends"), WeiBoUser.class);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public IResult findCacheData(Setting action, Params params) {
		if (params.containsKey("uid")) {
			if (!params.getParameter("uid").equals(AppContext.getAccount().getUser().getIdstr()))
				return null;
		}
		else if (params.containsKey("screen_name")) {
			if (!params.getParameter("screen_name").equals(AppContext.getAccount().getUser().getScreen_name()))
				return null;
		}
		
		List<WeiBoUser> userList = selectAll();
		if (userList.size() > 0) {
			Friendship users = new Friendship();
			users.setUsers(userList);
			users.setFromCache(true);
			users.setOutofdate(CacheTimeUtils.isOutofdate("Friends", AppContext.getAccount().getUser()));
			users.setNext_cursor(ActivityHelper.getIntShareData(GlobalContext.getInstance(), "Friends" + AppContext.getAccount().getUser().getIdstr(), 0));
			return users;
		}
		
		return null;
	}

	@Override
	public void addCacheData(Setting action, Params params, IResult responseObj) {
		boolean save = true;
		if (params.containsKey("uid")) {
			save = params.getParameter("uid").equals(AppContext.getAccount().getUser().getIdstr());
		}
		else if (params.containsKey("screen_name")) {
			save = params.getParameter("screen_name").equals(AppContext.getAccount().getUser().getScreen_name());
		}
		
		if (save) {
			Friendship users = (Friendship) responseObj;
			if (users.getUsers().size() > 0) {
				
				if (params.containsKey("cursor") && Integer.parseInt(params.getParameter("cursor")) == 0) {
					CacheTimeUtils.saveTime("Friends", AppContext.getAccount().getUser());
					
					clear();
				}
				
				insertFriends(users.getUsers());
				
				ActivityHelper.putIntShareData(GlobalContext.getInstance(), "Friends" + AppContext.getAccount().getUser().getIdstr(), users.getNext_cursor());
			}
		}
	}
	
}
