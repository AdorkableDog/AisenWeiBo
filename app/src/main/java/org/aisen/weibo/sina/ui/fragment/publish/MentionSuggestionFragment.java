package org.aisen.weibo.sina.ui.fragment.publish;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import org.aisen.android.component.bitmaploader.BitmapLoader;
import org.aisen.android.network.task.TaskException;
import org.aisen.android.network.task.WorkTask;
import org.aisen.android.support.inject.ViewInject;
import org.aisen.android.ui.fragment.AListFragment;
import org.aisen.android.ui.fragment.adapter.ARecycleViewItemView;
import org.aisen.android.ui.fragment.itemview.IITemView;
import org.aisen.android.ui.fragment.itemview.IItemViewCreator;
import org.aisen.weibo.sina.R;
import org.aisen.weibo.sina.base.AppContext;
import org.aisen.weibo.sina.sinasdk.SinaSDK;
import org.aisen.weibo.sina.sinasdk.bean.SuggestionAtUser;
import org.aisen.weibo.sina.sinasdk.bean.WeiBoUser;
import org.aisen.weibo.sina.support.bean.MentionSuggestionBean;
import org.aisen.weibo.sina.support.bean.MentionSuggestionBeans;
import org.aisen.weibo.sina.support.sqlit.FriendDB;
import org.aisen.weibo.sina.support.sqlit.FriendMentionDB;
import org.aisen.weibo.sina.support.utils.AisenUtils;
import org.aisen.weibo.sina.support.utils.ImageConfigUtils;

import java.util.ArrayList;
import java.util.List;

import fr.castorflex.android.smoothprogressbar.SmoothProgressBar;

/**
 * \@联想搜索建议,搜着关注好友
 * 
 * @author wangdan
 *
 */
public class MentionSuggestionFragment extends AListFragment<MentionSuggestionBean, MentionSuggestionBeans>
											implements OnItemClickListener {

	public static MentionSuggestionFragment newInstance() {
		return new MentionSuggestionFragment();
	}
	
	@ViewInject(id = R.id.progress)
	SmoothProgressBar progressBar;
	@ViewInject(id = R.id.txtNone)
	View viewNone;
	
	private ArrayList<MentionSuggestionBean> localUserList;
	private ArrayList<MentionSuggestionBean> remoteUserList;
	
	private WorkTask<String, Void, List<SuggestionAtUser>> remoteTask;
	
	@Override
	public int inflateContentView() {
		return R.layout.ui_mention_suggestion;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	protected void layoutInit(android.view.LayoutInflater inflater, Bundle savedInstanceSate) {
		super.layoutInit(inflater, savedInstanceSate);
		
		localUserList = savedInstanceSate == null ? null
												  : (ArrayList<MentionSuggestionBean>) savedInstanceSate.getSerializable("local");
		remoteUserList = savedInstanceSate == null ? null
				  							      : (ArrayList<MentionSuggestionBean>) savedInstanceSate.getSerializable("remote");
		
		progressBar.setVisibility(View.GONE);
		progressBar.setIndeterminate(true);
		
		getRefreshView().setOnItemClickListener(this);
	};
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		outState.putSerializable("local", localUserList);
		outState.putSerializable("remote", remoteUserList);
	}
	
	public void query(String q) {
		if (q == null)
			q = "";
		
		new LocalTask(RefreshMode.reset).execute(q);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		MentionSuggestionBean bean = getAdapterItems().get(position);
		
		WeiBoUser friend = bean.getUser();
		if (friend == null) {
			friend = new WeiBoUser();
			friend.setIdstr(bean.getSuggestUser().getUid());
			friend.setScreen_name(bean.getSuggestUser().getNickname());
			
			new UserShowTask().execute(friend.getScreen_name());
		}
		else {
			FriendMentionDB.addFriend(friend);
		}
		
		Intent data = new Intent();
		data.putExtra("bean", friend);
		getActivity().setResult(Activity.RESULT_OK, data);
		getActivity().finish();
	}

	@Override
	public IItemViewCreator<MentionSuggestionBean> configItemViewCreator() {
		return new IItemViewCreator<MentionSuggestionBean>() {

			@Override
			public View newContentView(LayoutInflater inflater, ViewGroup parent, int viewType) {
				return inflater.inflate(R.layout.item_friend, parent, false);
			}

			@Override
			public IITemView<MentionSuggestionBean> newItemView(View convertView, int viewType) {
				return new SuggestionItemView(convertView);
			}

		};
	}

	@Override
	public void requestData(RefreshMode mode) {
		new LocalTask(RefreshMode.reset).execute("");
	}
	
	class LocalTask extends APagingTask<String, Void, MentionSuggestionBeans> {

		public LocalTask(RefreshMode mode) {
			super(mode);
		}
		
		@Override
		protected void onPrepare() {
			super.onPrepare();
			
			getRefreshView().setVisibility(View.VISIBLE);
			viewNone.setVisibility(View.GONE);
		}

		@Override
		protected List<MentionSuggestionBean> parseResult(MentionSuggestionBeans result) {
			return result.getList();
		}

		@Override
		protected MentionSuggestionBeans workInBackground(RefreshMode mode, String previousPage,
				String nextPage, String... params) throws TaskException {
			String q = params[0];
			
			List<WeiBoUser> userList = FriendDB.query(q);
			localUserList = new ArrayList<MentionSuggestionBean>();
			for (WeiBoUser user : userList) {
				MentionSuggestionBean bean = new MentionSuggestionBean();
				bean.setUser(user);
				localUserList.add(bean);
			}
			MentionSuggestionBeans beans = new MentionSuggestionBeans();
			beans.setList(localUserList);
			remoteUserList = new ArrayList<MentionSuggestionBean>();
			
			return beans;
		}
		
		@Override
		protected void onFinished() {
			super.onFinished();
			
			if (remoteTask != null)
				remoteTask.cancel(true);
			
			// 搜索建议不为空，搜索服务端数据
			if (!TextUtils.isEmpty(getParams()[0]))
				remoteTask = new RemoteTask().execute(getParams()[0]);
		}
		
	}
	
	class RemoteTask extends WorkTask<String, Void, List<SuggestionAtUser>> {

		@Override
		protected void onPrepare() {
			super.onPrepare();
			
			progressBar.setVisibility(View.VISIBLE);
		}
		
		@Override
		public List<SuggestionAtUser> workInBackground(String... params) throws TaskException {
			try {
				Thread.sleep(500);
			} catch (Exception e) {
			}
			
			SuggestionAtUser[] userArr = SinaSDK.getInstance(AppContext.getAccount().getAccessToken()).searchSuggestionsAtUsers(params[0]);
			List<SuggestionAtUser> suggestionAtUsers = new ArrayList<SuggestionAtUser>();
			
			// 将本地匹配的数据过滤掉
			for (SuggestionAtUser suggestionAtUser : userArr) {
				boolean exist = false;
				
				for (MentionSuggestionBean bean : getAdapterItems()) {
					if (bean.getUser() != null && bean.getUser().getIdstr().equals(suggestionAtUser.getUid())) {
						exist = true;
						break;
					}
					
				}
				
				if (!exist) {
					for (SuggestionAtUser user : suggestionAtUsers) {
						if (user.getUid().equals(suggestionAtUser.getUid())) {
							exist = true;
							break;
						}
					}
				}
				
				if (!exist)
					suggestionAtUsers.add(suggestionAtUser);
			}
			
			return suggestionAtUsers;
		}
		
		@Override
		protected void onFinished() {
			super.onFinished();
			
			progressBar.setVisibility(View.GONE);
		}
		
		@Override
		protected void onSuccess(List<SuggestionAtUser> result) {
			super.onSuccess(result);
			
			if (result != null) {
				remoteUserList = new ArrayList<MentionSuggestionBean>();
				
				for (SuggestionAtUser suggestionAtUser : result) {
					MentionSuggestionBean bean = new MentionSuggestionBean();
					bean.setSuggestUser(suggestionAtUser);
					remoteUserList.add(bean);
				}
				
				getAdapterItems().addAll(remoteUserList);
				getAdapter().notifyDataSetChanged();
			}
			
			if (getAdapter().getDatas().size() == 0) {
				getRefreshView().setVisibility(View.GONE);
				viewNone.setVisibility(View.VISIBLE);
			}
		}
		
		@Override
		protected void onFailure(TaskException exception) {
			super.onFailure(exception);
			
			showMessage(exception.getMessage());
		}
		
	}

	class SuggestionItemView extends ARecycleViewItemView<MentionSuggestionBean> {

		@ViewInject(id = R.id.imgPhoto)
		ImageView imgPhoto;
		@ViewInject(id = R.id.txtName)
		TextView txtName;
		@ViewInject(id = R.id.txtRemark)
		TextView txtRemark;
		@ViewInject(id = R.id.txtDivider)
		TextView txtDivider;
		@ViewInject(id = R.id.layDivider)
		View layDivider;

		public SuggestionItemView(View itemView) {
			super(getActivity(), itemView);
		}

		@Override
		public void onBindData(View convertView, MentionSuggestionBean data, int position) {
			String screenName = null;
			String remark = null;

			if (data.getUser() != null) {
				BitmapLoader.getInstance().display(MentionSuggestionFragment.this,
						AisenUtils.getUserPhoto(data.getUser()),
						imgPhoto, ImageConfigUtils.getLargePhotoConfig());

				imgPhoto.setVisibility(View.VISIBLE);

				screenName = data.getUser().getScreen_name();
				remark = data.getUser().getRemark();
			}
			else {
				imgPhoto.setVisibility(View.GONE);

				screenName = data.getSuggestUser().getNickname();
				remark = data.getSuggestUser().getRemark();
			}

			txtName.setText(screenName);
			txtRemark.setText(remark);

			if (localUserList.size() > 0 && remoteUserList.size() > 0) {
				layDivider.setVisibility(getPosition() == 0 || getPosition() == localUserList.size() ? View.VISIBLE : View.GONE);
				if (getPosition() == 0)
					txtDivider.setText(R.string.publish_local_datas);
				else if (getPosition() == localUserList.size())
					txtDivider.setText(R.string.publish_service_datas);
			}
			else {
				layDivider.setVisibility(View.GONE);
			}
		}

	}
	
	// 添加一个关注好友到搜索记录
	class UserShowTask extends WorkTask<String, Void, WeiBoUser> {

		@Override
		public WeiBoUser workInBackground(String... params) throws TaskException {
			WeiBoUser user = SinaSDK.getInstance(AppContext.getAccount().getAccessToken()).userShow(null, params[0]);
			FriendMentionDB.addFriend(user);
//			List<WeiBoUser> userList = new ArrayList<WeiBoUser>();
//			userList.add(user);
//			FriendDB.insertFriends(userList);
			return null;
		}
		
	}

}
