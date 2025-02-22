package org.thoughtcrime.securesms.database;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.SqlUtil;
import org.whispersystems.libsignal.groups.state.SenderKeyRecord;

import java.io.IOException;

/**
 * Stores all of the sender keys -- both the ones we create, and the ones we're told about.
 *
 * When working with SenderKeys, keep this in mind: they're not *really* keys. They're sessions.
 * The name is largely historical, and there's too much momentum to change it.
 */
public class SenderKeyDatabase extends Database {

  private static final String TAG = Log.tag(SenderKeyDatabase.class);

  public static final String TABLE_NAME = "sender_keys";

  private static final String ID              = "_id";
  public static final  String ADDRESS         = "address";
  public static final  String DEVICE          = "device";
  public static final  String DISTRIBUTION_ID = "distribution_id";
  public static final  String RECORD          = "record";
  public static final  String CREATED_AT      = "created_at";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID              + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                 ADDRESS         + " TEXT NOT NULL, " +
                                                                                 DEVICE          + " INTEGER NOT NULL, " +
                                                                                 DISTRIBUTION_ID + " TEXT NOT NULL, " +
                                                                                 RECORD          + " BLOB NOT NULL, " +
                                                                                 CREATED_AT      + " INTEGER NOT NULL, " +
                                                                                 "UNIQUE(" + ADDRESS + "," + DEVICE + ", " + DISTRIBUTION_ID + ") ON CONFLICT REPLACE);";

  SenderKeyDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public void store(@NonNull SignalProtocolAddress address, @NonNull DistributionId distributionId, @NonNull SenderKeyRecord record) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    ContentValues values = new ContentValues();
    values.put(ADDRESS, address.getName());
    values.put(DEVICE, address.getDeviceId());
    values.put(DISTRIBUTION_ID, distributionId.toString());
    values.put(RECORD, record.serialize());
    values.put(CREATED_AT, System.currentTimeMillis());

    db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
  }

  public @Nullable SenderKeyRecord load(@NonNull SignalProtocolAddress address, @NonNull DistributionId distributionId) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    String   query = ADDRESS + " = ? AND " + DEVICE + " = ? AND " + DISTRIBUTION_ID + " = ?";
    String[] args  = SqlUtil.buildArgs(address.getName(), address.getDeviceId(), distributionId);

    try (Cursor cursor = db.query(TABLE_NAME, new String[]{ RECORD }, query, args, null, null, null)) {
      if (cursor.moveToFirst()) {
        try {
          return new SenderKeyRecord(CursorUtil.requireBlob(cursor, RECORD));
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }
    }

    return null;
  }

  /**
   * Gets when the sender key session was created, or -1 if it doesn't exist.
   */
  public long getCreatedTime(@NonNull SignalProtocolAddress address, @NonNull DistributionId distributionId) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    String   query = ADDRESS + " = ? AND " + DEVICE + " = ? AND " + DISTRIBUTION_ID + " = ?";
    String[] args  = SqlUtil.buildArgs(address.getName(), address.getDeviceId(), distributionId);

    try (Cursor cursor = db.query(TABLE_NAME, new String[]{ CREATED_AT }, query, args, null, null, null)) {
      if (cursor.moveToFirst()) {
        return CursorUtil.requireLong(cursor, CREATED_AT);
      }
    }

    return -1;
  }

  /**
   * Removes all sender key session state for all devices for the provided recipient-distributionId pair.
   */
  public void deleteAllFor(@NonNull String addressName, @NonNull DistributionId distributionId) {
    SQLiteDatabase db    = databaseHelper.getSignalWritableDatabase();
    String         query = ADDRESS + " = ? AND " + DISTRIBUTION_ID + " = ?";
    String[]       args  = SqlUtil.buildArgs(addressName, distributionId);

    db.delete(TABLE_NAME, query, args);
  }

  /**
   * Deletes all database state.
   */
  public void deleteAll() {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    db.delete(TABLE_NAME, null, null);
  }
}
