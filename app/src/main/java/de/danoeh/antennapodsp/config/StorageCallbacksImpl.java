package de.danoeh.antennapodsp.config;


import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import de.danoeh.antennapod.core.StorageCallbacks;
import de.danoeh.antennapod.core.storage.PodDBAdapter;

public class StorageCallbacksImpl implements StorageCallbacks {

    private static final int DATABASE_VERSION = 3;

    @Override
    public int getDatabaseVersion() {
        return DATABASE_VERSION;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w("DBAdapter", "Upgrading from version " + oldVersion + " to "
                + newVersion + ".");
        if (oldVersion == 1 && newVersion == 2) upgrade1to2(db);
        if (oldVersion < 3) upgradeTo3(db);

    }

    private void upgrade1to2(final SQLiteDatabase db) {
        db.execSQL("ALTER TABLE "+ PodDBAdapter.TABLE_NAME_FEED_ITEMS
                + " ADD " + PodDBAdapter.KEY_IMAGE + " INTEGER;");

    }

    private void upgradeTo3(final SQLiteDatabase db) {
        db.beginTransaction();
        db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS + " ADD " + PodDBAdapter.KEY_FLATTR_STATUS + " INTEGER;");
        db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS + " ADD " + PodDBAdapter.KEY_USERNAME + " TEXT;");
        db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS + " ADD " + PodDBAdapter.KEY_PASSWORD + " TEXT;");

        db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA + " ADD " + PodDBAdapter.KEY_PLAYED_DURATION + " INTEGER;");

        // create new feeditems table and insert old items into the new table and drop old table
        final String TMPTABLE = "feeditemtmp";
        db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS + " RENAME TO " + TMPTABLE);
        db.execSQL(PodDBAdapter.CREATE_TABLE_FEED_ITEMS);
        db.execSQL(String.format("INSERT INTO %s(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s) SELECT * FROM %s",
                PodDBAdapter.TABLE_NAME_FEED_ITEMS,
                PodDBAdapter.KEY_ID,
                PodDBAdapter.KEY_TITLE,
                PodDBAdapter.KEY_CONTENT_ENCODED,
                PodDBAdapter.KEY_PUBDATE,
                PodDBAdapter.KEY_READ,
                PodDBAdapter.KEY_LINK,
                PodDBAdapter.KEY_DESCRIPTION,
                PodDBAdapter.KEY_PAYMENT_LINK,
                PodDBAdapter.KEY_MEDIA,
                PodDBAdapter.KEY_FEED,
                PodDBAdapter.KEY_HAS_CHAPTERS,
                PodDBAdapter.KEY_ITEM_IDENTIFIER,
                PodDBAdapter.KEY_IMAGE,
                TMPTABLE));
        db.execSQL("DROP TABLE " + TMPTABLE);


        db.setTransactionSuccessful();
        db.endTransaction();
    }
}
