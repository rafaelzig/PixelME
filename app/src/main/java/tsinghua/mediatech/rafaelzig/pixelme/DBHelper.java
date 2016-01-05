package tsinghua.mediatech.rafaelzig.pixelme;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DBHelper extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "pixelme.db";
    public static final String FEED_TABLE_NAME = "feed";
    public static final String FEED_COLUMN_ID = "id";
    public static final String FEED_COLUMN_URI = "uri";
    public static final String FEED_COLUMN_CREATED = "created";


    /**
     * @param context will require the context of the current main activity, just pass "this"!
     */
    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + FEED_TABLE_NAME +
                "(" + FEED_COLUMN_ID + " integer primary key autoincrement" +
                ", " + FEED_COLUMN_URI + " text not null" +
                ", " + FEED_COLUMN_CREATED + " datetime not null)");
    }

    /**
     * Will insert a new entry in the database!
     *
     * @param uri The string of the URI or the full path of the file
     * @return will return true if inserted!
     */
    public boolean insertEntry (String uri){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(FEED_COLUMN_URI, uri);
        contentValues.put(FEED_COLUMN_CREATED, getDateTime());
        db.insert(FEED_TABLE_NAME, null, contentValues);
        return true;
    }

    public Cursor getData(int id){
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + FEED_TABLE_NAME + "WHERE id = " + id + ";", null);
    }

    public Integer deleteEntry(Integer id){
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(FEED_TABLE_NAME, FEED_COLUMN_ID + " = ?", new String[] { Integer.toString(id) });
    }

    public ArrayList<Map<String, String>> getAllEntries(){
        ArrayList<Map<String, String>> arrayList = new ArrayList<>();

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res = db.rawQuery("SELECT * FROM " + FEED_TABLE_NAME + " ORDER BY created DESC", null);
        res.moveToFirst();

        while(!res.isAfterLast()){
            Map<String, String> map = new HashMap<>();
            map.put("id", res.getString(res.getColumnIndex(FEED_COLUMN_ID)));
            map.put("uri", res.getString(res.getColumnIndex(FEED_COLUMN_URI)));
            map.put("created", res.getString(res.getColumnIndex(FEED_COLUMN_CREATED)));
            arrayList.add(map);
            res.moveToNext();
        }
        res.close();
        db.close();
        return arrayList;
    }

    public Map<String, String> getLastEntry(){
        Map<String, String> lastEntry = new HashMap<>();

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res = db.rawQuery("SELECT * FROM " + FEED_TABLE_NAME + " ORDER BY created DESC LIMIT 1", null);
        res.moveToNext();
        lastEntry.put("id", res.getString(res.getColumnIndex(FEED_COLUMN_ID)));
        lastEntry.put("uri", res.getString(res.getColumnIndex(FEED_COLUMN_URI)));
        lastEntry.put("created", res.getString(res.getColumnIndex(FEED_COLUMN_CREATED)));
        res.close();
        db.close();

        return lastEntry;
    }

    public boolean deleteAll(){
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(FEED_TABLE_NAME, null, null);
        return true;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    private String getDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Date date = new Date();
        return dateFormat.format(date);
    }
}
