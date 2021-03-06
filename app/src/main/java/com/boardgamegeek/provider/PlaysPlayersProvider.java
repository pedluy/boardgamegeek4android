package com.boardgamegeek.provider;

import android.net.Uri;

import com.boardgamegeek.provider.BggContract.PlayItems;
import com.boardgamegeek.provider.BggContract.PlayPlayers;
import com.boardgamegeek.provider.BggContract.Plays;
import com.boardgamegeek.provider.BggDatabase.Tables;
import com.boardgamegeek.util.SelectionBuilder;

public class PlaysPlayersProvider extends BaseProvider {

	@Override
	protected SelectionBuilder buildSimpleSelection(Uri uri) {
		return new SelectionBuilder().table(Tables.PLAY_PLAYERS);
	}

	@Override
	protected SelectionBuilder buildExpandedSelection(Uri uri) {
		SelectionBuilder builder;
		String groupBy = uri.getQueryParameter(BggContract.QUERY_KEY_GROUP_BY);
		if (groupBy == null) {
			groupBy = "";
		}
		switch (groupBy) {
			case BggContract.QUERY_VALUE_NAME_NOT_USER:
				builder = new SelectionBuilder().table(Tables.PLAY_PLAYERS)
					.whereEqualsOrNull(PlayPlayers.USER_NAME, "")
					.groupBy(PlayPlayers.NAME);
				break;
			case BggContract.QUERY_VALUE_UNIQUE_NAME:
				builder = new SelectionBuilder().table(Tables.PLAY_PLAYERS_JOIN_PLAYS)
					.mapToTable(Plays._ID, Tables.PLAY_PLAYERS)
					.mapToTable(Plays.PLAY_ID, Tables.PLAY_PLAYERS)
					.map(Plays.SUM_QUANTITY, "SUM(" + Plays.QUANTITY + ")")
					.map(Plays.SUM_WINS, "SUM(CASE WHEN " + PlayPlayers.WIN + "=1 THEN " + Plays.QUANTITY + " ELSE 0 END)")
					.where(PlayPlayers.NAME + "!= '' OR " + PlayPlayers.USER_NAME + "!=''")
					.groupBy(PlayPlayers.UNIQUE_NAME);
				break;
			case BggContract.QUERY_VALUE_UNIQUE_PLAYER:
				builder = new SelectionBuilder().table(Tables.PLAY_PLAYERS_JOIN_PLAYS)
					.mapToTable(Plays._ID, Tables.PLAY_PLAYERS)
					.mapToTable(Plays.PLAY_ID, Tables.PLAY_PLAYERS)
					.map(Plays.SUM_QUANTITY, "SUM(" + Plays.QUANTITY + ")")
					.map(Plays.SUM_WINS, "SUM(CASE WHEN " + PlayPlayers.WIN + "=1 THEN " + Plays.QUANTITY + " ELSE 0 END)")
					.where(PlayPlayers.NAME + "!= '' OR " + PlayPlayers.USER_NAME + "!=''")
					.groupBy(PlayPlayers.NAME + "," + PlayPlayers.USER_NAME);
				break;
			case BggContract.QUERY_VALUE_UNIQUE_USER:
				builder = new SelectionBuilder().table(Tables.PLAY_PLAYERS_JOIN_PLAYS)
					.mapToTable(Plays._ID, Tables.PLAY_PLAYERS)
					.mapToTable(Plays.PLAY_ID, Tables.PLAY_PLAYERS)
					.map(Plays.SUM_QUANTITY, "SUM(" + Plays.QUANTITY + ")")
					.map(Plays.SUM_WINS, "SUM(CASE WHEN " + PlayPlayers.WIN + "=1 THEN " + Plays.QUANTITY + " ELSE 0 END)")
					.where(PlayPlayers.USER_NAME + "!=''")
					.groupBy(PlayPlayers.USER_NAME);
				break;
			case BggContract.QUERY_VALUE_COLOR:
				builder = new SelectionBuilder().table(Tables.PLAY_PLAYERS_JOIN_PLAYS_JOIN_ITEMS)
					.mapToTable(Plays._ID, Tables.PLAY_PLAYERS)
					.mapToTable(Plays.PLAY_ID, Tables.PLAY_PLAYERS)
					.mapToTable(PlayItems.NAME, Tables.PLAY_ITEMS)
					.groupBy(PlayPlayers.COLOR);
				break;
			case BggContract.QUERY_VALUE_PLAY:
				builder = new SelectionBuilder().table(Tables.PLAY_PLAYERS_JOIN_PLAYS_JOIN_ITEMS)
					.mapToTable(Plays._ID, Tables.PLAYS)
					.mapToTable(Plays.PLAY_ID, Tables.PLAYS)
					.mapToTable(PlayItems.NAME, Tables.PLAY_ITEMS)
					.groupBy(Plays.PLAY_ID);
				break;
			default:
				builder = new SelectionBuilder().table(Tables.PLAY_PLAYERS_JOIN_PLAYS_JOIN_ITEMS)
					.mapToTable(PlayPlayers._ID, Tables.PLAY_PLAYERS)
					.mapToTable(PlayPlayers.PLAY_ID, Tables.PLAY_PLAYERS)
					.mapToTable(PlayPlayers.NAME, Tables.PLAY_PLAYERS);
				break;
		}
		builder
			.map(PlayPlayers.COUNT, "count(*)")
			.map(PlayPlayers.UNIQUE_NAME, "IFNULL(NULLIF(user_name,''), name)")
			.map(PlayPlayers.DESCRIPTION, "name || IFNULL(NULLIF(' ('||user_name||')', ' ()'), '')");
		return builder;
	}

	@Override
	protected String getDefaultSortOrder() {
		return Plays.DATE + " DESC, " + PlayPlayers.DEFAULT_SORT;
	}

	@Override
	protected String getPath() {
		return "plays/players";
	}
}
