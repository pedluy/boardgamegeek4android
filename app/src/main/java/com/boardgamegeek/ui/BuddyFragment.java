package com.boardgamegeek.ui;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.boardgamegeek.R;
import com.boardgamegeek.events.UpdateCompleteEvent;
import com.boardgamegeek.events.UpdateEvent;
import com.boardgamegeek.provider.BggContract.Buddies;
import com.boardgamegeek.provider.BggContract.PlayPlayers;
import com.boardgamegeek.provider.BggContract.PlayerColors;
import com.boardgamegeek.provider.BggContract.Plays;
import com.boardgamegeek.service.UpdateService;
import com.boardgamegeek.tasks.BuddyNicknameUpdateTask;
import com.boardgamegeek.tasks.RenamePlayerTask;
import com.boardgamegeek.ui.dialog.EditTextDialogFragment;
import com.boardgamegeek.ui.dialog.EditTextDialogFragment.EditTextDialogListener;
import com.boardgamegeek.ui.dialog.UpdateBuddyNicknameDialogFragment;
import com.boardgamegeek.ui.dialog.UpdateBuddyNicknameDialogFragment.UpdateBuddyNicknameDialogListener;
import com.boardgamegeek.ui.model.Buddy;
import com.boardgamegeek.ui.model.Player;
import com.boardgamegeek.ui.model.PlayerColor;
import com.boardgamegeek.ui.widget.TimestampView;
import com.boardgamegeek.util.ActivityUtils;
import com.boardgamegeek.util.ColorUtils;
import com.boardgamegeek.util.DialogUtils;
import com.boardgamegeek.util.HttpUtils;
import com.boardgamegeek.util.PresentationUtils;
import com.boardgamegeek.util.TaskUtils;
import com.boardgamegeek.util.UIUtils;
import com.squareup.picasso.Picasso;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import hugo.weaving.DebugLog;
import icepick.Icepick;
import icepick.State;
import timber.log.Timber;

public class BuddyFragment extends Fragment implements LoaderCallbacks<Cursor>, OnRefreshListener {
	private static final int PLAYS_TOKEN = 1;
	private static final int COLORS_TOKEN = 2;
	private static final int TOKEN = 0;

	private String buddyName;
	private String playerName;
	private boolean isRefreshing;
	@State boolean hasBeenRefreshed;

	private Unbinder unbinder;
	private SwipeRefreshLayout swipeRefreshLayout;
	@BindView(R.id.buddy_info) View buddyInfoView;
	@BindView(R.id.full_name) TextView fullNameView;
	@BindView(R.id.username) TextView usernameView;
	@BindView(R.id.avatar) ImageView avatarView;
	@BindView(R.id.nickname) TextView nicknameView;
	@BindView(R.id.collection_card) View collectionCard;
	@BindView(R.id.plays_label) TextView playsView;
	@BindView(R.id.wins_label) TextView winsView;
	@BindView(R.id.wins_percentage) TextView winPercentageView;
	@BindView(R.id.color_container) LinearLayout colorContainer;
	@BindView(R.id.updated) TimestampView updatedView;
	private int defaultTextColor;
	private int lightTextColor;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Icepick.restoreInstanceState(this, savedInstanceState);

		final Intent intent = UIUtils.fragmentArgumentsToIntent(getArguments());
		buddyName = intent.getStringExtra(ActivityUtils.KEY_BUDDY_NAME);
		playerName = intent.getStringExtra(ActivityUtils.KEY_PLAYER_NAME);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Icepick.saveInstanceState(this, outState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.fragment_buddy, container, false);

		unbinder = ButterKnife.bind(this, rootView);

		buddyInfoView.setVisibility(isUser() ? View.VISIBLE : View.GONE);
		collectionCard.setVisibility(isUser() ? View.VISIBLE : View.GONE);
		updatedView.setVisibility(isUser() ? View.VISIBLE : View.GONE);

		swipeRefreshLayout = (SwipeRefreshLayout) rootView;
		if (isUser()) {
			swipeRefreshLayout.setOnRefreshListener(this);
			swipeRefreshLayout.setColorSchemeResources(PresentationUtils.getColorSchemeResources());
			swipeRefreshLayout.setEnabled(true);
		} else {
			swipeRefreshLayout.setEnabled(false);
		}

		defaultTextColor = nicknameView.getTextColors().getDefaultColor();
		lightTextColor = ContextCompat.getColor(getContext(), R.color.secondary_text);

		if (isUser()) {
			getLoaderManager().restartLoader(TOKEN, null, this);
		} else {
			nicknameView.setTextColor(defaultTextColor);
			nicknameView.setText(playerName);
		}
		getLoaderManager().restartLoader(PLAYS_TOKEN, null, this);
		getLoaderManager().restartLoader(COLORS_TOKEN, null, this);

		return rootView;
	}

	private boolean isUser() {
		return !TextUtils.isEmpty(buddyName);
	}

	@DebugLog
	@Override
	public void onStart() {
		super.onStart();
		EventBus.getDefault().register(this);
	}

	@DebugLog
	@Override
	public void onStop() {
		EventBus.getDefault().unregister(this);
		super.onStop();
	}

	@Override
	public void onDestroyView() {
		if (unbinder != null) unbinder.unbind();
		super.onDestroyView();
	}

	@SuppressWarnings("unused")
	@Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
	public void onEvent(UpdateEvent event) {
		isRefreshing = event.getType() == UpdateService.SYNC_TYPE_BUDDY;
		updateRefreshStatus();
	}

	@SuppressWarnings("unused")
	@DebugLog
	@Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
	public void onEvent(UpdateCompleteEvent event) {
		isRefreshing = false;
		updateRefreshStatus();
	}

	private void updateRefreshStatus() {
		if (swipeRefreshLayout != null) {
			swipeRefreshLayout.post(new Runnable() {
				@Override
				public void run() {
					swipeRefreshLayout.setRefreshing(isRefreshing);
				}
			});
		}
	}

	@Override
	@DebugLog
	public Loader<Cursor> onCreateLoader(int id, Bundle data) {
		CursorLoader loader = null;
		switch (id) {
			case TOKEN:
				loader = new CursorLoader(getActivity(), Buddies.buildBuddyUri(buddyName), Buddy.PROJECTION, null, null, null);
				break;
			case PLAYS_TOKEN:
				if (isUser()) {
					loader = new CursorLoader(getActivity(),
						Plays.buildPlayersByUniqueUserUri(),
						Player.PROJECTION,
						PlayPlayers.USER_NAME + "=?",
						new String[] { buddyName },
						null);

				} else {
					loader = new CursorLoader(getActivity(),
						Plays.buildPlayersByUniquePlayerUri(),
						Player.PROJECTION,
						"(" + PlayPlayers.USER_NAME + "=? OR " + PlayPlayers.USER_NAME + " IS NULL) AND play_players." + PlayPlayers.NAME + "=?",
						new String[] { "", playerName },
						null);
				}
				break;
			case COLORS_TOKEN:
				loader = new CursorLoader(getActivity(),
					isUser() ? PlayerColors.buildUserUri(buddyName) : PlayerColors.buildPlayerUri(playerName),
					PlayerColor.PROJECTION,
					null, null, null);
				break;
		}
		return loader;
	}

	@Override
	@DebugLog
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
		if (getActivity() == null) {
			return;
		}

		switch (loader.getId()) {
			case TOKEN:
				onBuddyQueryComplete(cursor);
				break;
			case PLAYS_TOKEN:
				onPlaysQueryComplete(cursor);
				break;
			case COLORS_TOKEN:
				onColorsQueryComplete(cursor);
				break;
			default:
				cursor.close();
				break;
		}
	}

	@Override
	@DebugLog
	public void onLoaderReset(Loader<Cursor> loader) {
	}

	@DebugLog
	@OnClick(R.id.nickname)
	public void onEditNicknameClick() {
		if (isUser()) {
			showNicknameDialog(nicknameView.getText().toString(), buddyName);
		} else {
			showPlayerNameDialog(nicknameView.getText().toString());
		}
	}

	@DebugLog
	@OnClick(R.id.collection_root)
	public void onCollectionClick() {
		Intent intent = new Intent(getActivity(), BuddyCollectionActivity.class);
		intent.putExtra(ActivityUtils.KEY_BUDDY_NAME, buddyName);
		startActivity(intent);
	}

	@DebugLog
	@OnClick(R.id.plays_root)
	public void onPlaysClick() {
		if (isUser()) {
			Intent intent = new Intent(getActivity(), BuddyPlaysActivity.class);
			intent.putExtra(ActivityUtils.KEY_BUDDY_NAME, buddyName);
			startActivity(intent);
		} else {
			ActivityUtils.startPlayerPlaysActivity(getActivity(), playerName, buddyName);
		}
	}

	@DebugLog
	@OnClick(R.id.colors_root)
	public void onColorsClick() {
		Intent intent = new Intent(getActivity(), BuddyColorsActivity.class);
		intent.putExtra(ActivityUtils.KEY_BUDDY_NAME, buddyName);
		intent.putExtra(ActivityUtils.KEY_PLAYER_NAME, playerName);
		startActivity(intent);
	}

	private void onBuddyQueryComplete(Cursor cursor) {
		if (cursor == null || !cursor.moveToFirst()) {
			requestRefresh();
			return;
		}

		Buddy buddy = Buddy.fromCursor(cursor);

		Picasso.with(getActivity())
			.load(HttpUtils.ensureScheme(buddy.getAvatarUrl()))
			.placeholder(R.drawable.person_image_empty)
			.error(R.drawable.person_image_empty)
			.fit().into(avatarView);
		fullNameView.setText(buddy.getFullName());
		usernameView.setText(buddyName);
		if (TextUtils.isEmpty(buddy.getNickName())) {
			nicknameView.setTextColor(lightTextColor);
			nicknameView.setText(buddy.getFirstName());
		} else {
			nicknameView.setTextColor(defaultTextColor);
			nicknameView.setText(buddy.getNickName());
		}
		updatedView.setTimestamp(buddy.getUpdated());
	}

	@DebugLog
	private void onPlaysQueryComplete(Cursor cursor) {
		if (cursor == null || !cursor.moveToFirst()) {
			return;
		}

		Player player = Player.fromCursor(cursor);
		final int playCount = player.getPlayCount();
		final int winCount = player.getWinCount();
		playsView.setText(PresentationUtils.getQuantityText(getContext(), R.plurals.plays_suffix, playCount, playCount));
		winsView.setText(PresentationUtils.getQuantityText(getContext(), R.plurals.wins_suffix, winCount, winCount));
		winPercentageView.setText(getString(R.string.percentage, (int) ((double) winCount / playCount * 100)));
	}

	@DebugLog
	private void onColorsQueryComplete(Cursor cursor) {
		if (cursor == null) {
			return;
		}

		colorContainer.removeAllViews();

		for (int i = 0; i < 3; i++) {
			if (cursor.moveToNext()) {
				colorContainer.setVisibility(View.VISIBLE);
				ImageView view = createViewToBeColored();
				PlayerColor color = PlayerColor.fromCursor(cursor);
				ColorUtils.setColorViewValue(view, ColorUtils.parseColor(color.getColor()));
				colorContainer.addView(view);
			} else {
				colorContainer.setVisibility(View.GONE);
				return;
			}
		}
	}

	@DebugLog
	private ImageView createViewToBeColored() {
		ImageView view = new ImageView(getActivity());
		int size = getResources().getDimensionPixelSize(R.dimen.color_circle_diameter_small);
		int margin = getResources().getDimensionPixelSize(R.dimen.color_circle_diameter_small_margin);
		LayoutParams lp = new LayoutParams(size, size);
		lp.setMargins(margin, margin, margin, margin);
		view.setLayoutParams(lp);
		return view;
	}

	@DebugLog
	@Override
	public void onRefresh() {
		requestRefresh();
	}

	@DebugLog
	private void requestRefresh() {
		if (!hasBeenRefreshed) {
			forceRefresh();
			hasBeenRefreshed = true;
		}
	}

	@DebugLog
	public void forceRefresh() {
		if (isUser()) {
			UpdateService.start(getActivity(), UpdateService.SYNC_TYPE_BUDDY, buddyName);
		} else {
			Timber.w("Something tried to refresh a player that wasn't a user!");
		}
	}

	@DebugLog
	private void showNicknameDialog(final String nickname, final String username) {
		UpdateBuddyNicknameDialogFragment dialogFragment = UpdateBuddyNicknameDialogFragment.newInstance(R.string.title_edit_nickname, null, new UpdateBuddyNicknameDialogListener() {
			@Override
			public void onFinishEditDialog(String newNickname, boolean updatePlays) {
				if (!TextUtils.isEmpty(newNickname)) {
					BuddyNicknameUpdateTask task = new BuddyNicknameUpdateTask(getActivity(), username, newNickname, updatePlays);
					TaskUtils.executeAsyncTask(task);
				}
			}
		});
		dialogFragment.setNickname(nickname);
		DialogUtils.showFragment(getActivity(), dialogFragment, "edit_nickname");
	}

	@DebugLog
	private void showPlayerNameDialog(final String oldName) {
		EditTextDialogFragment editTextDialogFragment = EditTextDialogFragment.newInstance(R.string.title_edit_player, null, new EditTextDialogListener() {
			@Override
			public void onFinishEditDialog(String inputText) {
				if (!TextUtils.isEmpty(inputText)) {
					RenamePlayerTask task = new RenamePlayerTask(getContext(), oldName, inputText);
					TaskUtils.executeAsyncTask(task);
				}
			}
		});
		editTextDialogFragment.setText(oldName);
		DialogUtils.showFragment(getActivity(), editTextDialogFragment, "edit_player");
	}
}
