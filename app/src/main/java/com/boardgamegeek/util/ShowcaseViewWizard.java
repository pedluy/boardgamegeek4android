package com.boardgamegeek.util;

import android.app.Activity;
import android.os.Handler;
import android.support.annotation.StringRes;
import android.support.v4.util.Pair;
import android.view.View;
import android.view.View.OnClickListener;

import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.ShowcaseView.Builder;
import com.github.amlcurran.showcaseview.targets.Target;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import hugo.weaving.DebugLog;

public class ShowcaseViewWizard {
	private final WeakReference<Activity> activityWeakReference;
	private final String helpKey;
	private final int helpVersion;
	private ShowcaseView showcaseView;
	private int helpIndex;
	final List<Pair<Integer, Target>> helpTargets = new ArrayList<>();

	public ShowcaseViewWizard(Activity activity, String helpKey, int helpVersion) {
		this.activityWeakReference = new WeakReference<>(activity);
		this.helpKey = helpKey;
		this.helpVersion = helpVersion;
		helpTargets.clear();
	}

	public void addTarget(@StringRes int contextResId, Target target) {
		helpTargets.add(new Pair<>(contextResId, target));
	}

	@DebugLog
	public void showHelp() {
		Activity activity = activityWeakReference.get();
		if (activity == null) return;
		helpIndex = 0;
		Builder builder = HelpUtils.getShowcaseBuilder(activity)
			.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					showNextHelp();
				}
			});
		showcaseView = builder.build();
		showcaseView.setButtonPosition(HelpUtils.getLowerLeftLayoutParams(activity));
		showNextHelp();
	}

	@DebugLog
	private void showNextHelp() {
		Activity activity = activityWeakReference.get();
		if (activity == null) return;
		if (helpIndex < helpTargets.size()) {
			Pair<Integer, Target> helpTarget = helpTargets.get(helpIndex);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i <= helpIndex; i++) {
				int resId = helpTargets.get(i).first;
				if (resId > 0) {
					sb.append("\n").append(activity.getString(resId));
				}
			}
			showcaseView.setContentText(sb.toString());
			showcaseView.setShowcase(helpTarget.second, true);
		} else {
			showcaseView.hide();
			HelpUtils.updateHelp(activity, helpKey, helpVersion);
		}
		helpIndex++;
	}

	@DebugLog
	public void maybeShowHelp() {
		Activity activity = activityWeakReference.get();
		if (activity == null) return;
		if (HelpUtils.shouldShowHelp(activity, helpKey, helpVersion)) {
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					showHelp();
				}
			}, 100);
		}
	}
}
