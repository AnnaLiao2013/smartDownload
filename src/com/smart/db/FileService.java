package com.smart.db;

import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

//database services
public class FileService {
	private DBOpenHelper openHelper;

	public FileService(Context context) {
		openHelper = new DBOpenHelper(context);
	}

	public Map<Integer, Integer> getData(String path) {
		SQLiteDatabase db = openHelper.getReadableDatabase();
		Cursor cursor = db
				.rawQuery(
						"select threadid, downlength from SmartFileDownlog where downpath=?",
						new String[] { path });
		Map<Integer, Integer> data = new HashMap<Integer, Integer>();
		while (cursor.moveToNext()) {
			data.put(cursor.getInt(0), cursor.getInt(1));
		}
		cursor.close();
		db.close();
		return data;
	}

	public void save(String path, Map<Integer, Integer> map) {// int thread num,
																// int position
		SQLiteDatabase db = openHelper.getWritableDatabase();
		db.beginTransaction();
		try {
			for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
				db.execSQL(
						"insert into SmartFileDownlog(downpath, threadid, downlength) values(?,?,?)",
						new Object[] { path, entry.getKey(), entry.getValue() });
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		db.close();
	}

	public void delete(String path) {
		SQLiteDatabase db = openHelper.getWritableDatabase();
		db.execSQL("delete from SmartFileDownlog where downpath=?",
				new Object[] { path });
		db.close();
	}

	public void update(String path, Map<Integer, Integer> map) {
		SQLiteDatabase db = openHelper.getWritableDatabase();
		db.beginTransaction();
		try {
			for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
				db.execSQL(
						"update SmartFileDownlog set downlength=? where downpath=? and threadid=?",
						new Object[] { entry.getValue(), path, entry.getKey() });
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		db.close();
	}

}
