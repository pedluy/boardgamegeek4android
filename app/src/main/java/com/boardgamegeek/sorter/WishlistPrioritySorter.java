package com.boardgamegeek.sorter;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import com.boardgamegeek.R;
import com.boardgamegeek.provider.BggContract.Collection;

public class WishlistPrioritySorter extends CollectionSorter {
	private final String[] priorityText;

	public WishlistPrioritySorter(@NonNull Context context) {
		super(context);
		priorityText = context.getResources().getStringArray(R.array.wishlist_priority);
		orderByClause = getClause(Collection.STATUS_WISHLIST_PRIORITY, false);
		descriptionId = R.string.collection_sort_wishlist_priority;
	}

	@StringRes
	@Override
	public int getTypeResource() {
		return R.string.collection_sort_type_wishlist_priority;
	}

	@Override
	public String[] getColumns() {
		return new String[] { Collection.STATUS_WISHLIST_PRIORITY };
	}

	@Override
	public String getHeaderText(@NonNull Cursor cursor) {
		int level = getInt(cursor, Collection.STATUS_WISHLIST_PRIORITY);
		if (level >= priorityText.length) {
			level = 0;
		}
		return priorityText[level];
	}

	@NonNull
	@Override
	public String getDisplayInfo(@NonNull Cursor cursor) {
		return getIntAsString(cursor, Collection.STATUS_WISHLIST_PRIORITY, "?", true) + " - " + getHeaderText(cursor);
	}
}
